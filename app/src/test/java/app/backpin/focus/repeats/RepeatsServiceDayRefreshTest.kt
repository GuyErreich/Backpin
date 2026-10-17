// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.notifications.BandResyncReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Day-signal behavior for [RepeatsService]: midnight, system date/time/zone
 * broadcasts, [RepeatsApi.refreshToday], missed-row lifetime, draft anchors,
 * DST, and 23:59 / 00:00 edges.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatsServiceDayRefreshTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val open = mutableListOf<Harness>()

    @After
    fun tearDown() {
        open.toList().forEach { it.close() }
        open.clear()
    }

    @Test
    fun observeRowsAndTodaysRepeatsReemitAtLocalMidnight() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 10, 16)
        val env = harness(at(day, 23, 59, zone), zone)
        env.service.create("Take meds", RepeatBand.MORNING, 1, day.toEpochDay())
        env.service.create("Water plants", RepeatBand.NOON, 2, day.toEpochDay())
        val rows = probe(env.service.observeRows())
        val todays = probe(env.service.observeTodaysRepeats())
        rows.await { lastTitles(it) == setOf("Take meds", "Water plants") }
        todays.awaitLast { it.all().size == 2 }
        val rowCount = rows.size
        val todayCount = todays.size
        assertTrue(rows.last().rows.all { it.status == RepeatDayStatus.MISSED })

        env.advanceClockPastMidnight(at(day.plusDays(1), 0, 1, zone))

        rows.await { it.size > rowCount }
        todays.await { it.size > todayCount }
        assertEquals(listOf("Take meds"), rows.last().rows.map { it.repeat.title })
        assertEquals(RepeatDayStatus.OPEN, rows.last().rows.single().status)
        assertEquals(listOf("Take meds"), todays.last().all().map { it.repeat.title })
        assertEquals(day.plusDays(1).toEpochDay(), todays.last().all().single().epochDay)
        rows.close()
        todays.close()
    }

    @Test
    fun dateTimeAndTimezoneBroadcastsReemitIncludingAZoneThatCrossesADay() = runBlocking {
        val losAngeles = ZoneId.of("America/Los_Angeles")
        val auckland = ZoneId.of("Pacific/Auckland")
        val friday = LocalDate.of(2026, 10, 16)
        val env = harness(at(friday, 8, 0, losAngeles), losAngeles)
        env.service.create("Take meds", RepeatBand.MORNING, 1, friday.toEpochDay())
        env.service.create("Laundry", RepeatBand.NIGHT, 2, friday.toEpochDay())
        val rows = probe(env.service.observeRows())
        val todays = probe(env.service.observeTodaysRepeats())
        rows.await { lastTitles(it) == setOf("Take meds", "Laundry") }

        env.clock.now = at(friday.plusDays(1), 0, 1, losAngeles)
        val afterTime = rows.size
        deliverToRepeatsService(Intent.ACTION_TIME_CHANGED)
        rows.await { it.size > afterTime }
        todays.awaitLast { it.all().none { item -> item.repeat.title == "Laundry" } }
        assertEquals(listOf("Take meds"), rows.last().rows.map { it.repeat.title })

        env.clock.now = at(friday, 23, 59, losAngeles)
        env.service.refreshToday()
        rows.await { lastTitles(it) == setOf("Take meds", "Laundry") }
        env.clock.now = at(friday.plusDays(1), 8, 0, losAngeles)
        val afterDate = rows.size
        deliverToRepeatsService(Intent.ACTION_DATE_CHANGED)
        rows.await { it.size > afterDate }
        assertEquals(listOf("Take meds"), rows.last().rows.map { it.repeat.title })
        assertFalse(rows.last().rows.any { it.repeat.title == "Laundry" })

        val crossing = Instant.parse("2026-10-16T04:00:00Z")
        env.clock.now = crossing.toEpochMilli()
        env.zoneId = losAngeles
        env.service.create("Zone miss", RepeatBand.NOON, 2, LocalDate.of(2026, 10, 15).toEpochDay())
        env.service.refreshToday()
        rows.await { lastTitles(it) == setOf("Zone miss") }
        assertEquals(LocalDate.of(2026, 10, 15), crossing.atZone(losAngeles).toLocalDate())
        assertEquals(LocalDate.of(2026, 10, 16), crossing.atZone(auckland).toLocalDate())
        val beforeZone = rows.size
        env.zoneId = auckland
        deliverToRepeatsService(Intent.ACTION_TIMEZONE_CHANGED)
        rows.await { it.size > beforeZone }
        assertTrue(rows.last().rows.any { it.repeat.title == "Take meds" })
        assertFalse(rows.last().rows.any { it.repeat.title == "Zone miss" })
        rows.close()
        todays.close()
    }

    @Test
    fun refreshTodayRecomputesOnceWhenTheDayChangesAndIsANoOpOnTheSameDay() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 10, 16)
        val env = harness(at(day, 23, 59, zone), zone)
        env.service.create("Water plants", RepeatBand.NOON, 2, day.toEpochDay())
        val rows = probe(env.service.observeRows())
        val todays = probe(env.service.observeTodaysRepeats())
        rows.await { it.lastOrNull()?.rows?.singleOrNull()?.repeat?.title == "Water plants" }
        val rowCount = rows.size
        val todayCount = todays.size

        env.clock.now = at(day.plusDays(1), 8, 0, zone)
        env.service.refreshToday()
        rows.await { it.size > rowCount && it.last().rows.isEmpty() }
        todays.await { it.size > todayCount && it.last().all().isEmpty() }
        val rowsAfterChange = rows.size
        val todaysAfterChange = todays.size

        env.service.refreshToday()
        yield()
        yield()
        assertEquals(rowsAfterChange, rows.size)
        assertEquals(todaysAfterChange, todays.size)
        rows.close()
        todays.close()
    }

    @Test
    fun missedRepeatStaysMissedThroughItsDayThenLeavesHomeAfterTheDayChanges() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 10, 16)
        val env = harness(at(day, 13, 0, zone), zone)
        env.service.create("Take meds", RepeatBand.MORNING, 1, day.toEpochDay())
        env.service.create("Water plants", RepeatBand.NOON, 2, day.toEpochDay())
        env.repository.markOpenRepeatsMissed(RepeatBand.MORNING, day.toEpochDay())
        env.repository.markOpenRepeatsMissed(RepeatBand.NOON, day.toEpochDay())
        val rows = probe(env.service.observeRows())
        rows.await {
            it.lastOrNull()?.rows?.size == 2 &&
                it.last().rows.all { row -> row.status == RepeatDayStatus.MISSED }
        }

        env.clock.now = at(day, 23, 59, zone)
        env.service.refreshToday()
        rows.awaitLast { state ->
            state.rows.size == 2 &&
                state.rows.all { it.status == RepeatDayStatus.MISSED } &&
                state.rows.map { it.repeat.title }.toSet() == setOf("Take meds", "Water plants")
        }

        env.clock.now = at(day.plusDays(1), 0, 0, zone)
        env.service.refreshToday()
        rows.awaitLast { state ->
            state.rows.none { it.repeat.title == "Water plants" } &&
                state.rows.single { it.repeat.title == "Take meds" }.status == RepeatDayStatus.OPEN
        }
        rows.close()
    }

    @Test
    fun commitRepeatDraftWithZeroAnchorUsesInjectedClockAndZoneNotLocalDateNow() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val instant = Instant.parse("2020-01-01T04:00:00Z")
        val env = harness(instant.toEpochMilli(), zone, listenForDayChanges = false)
        val utcDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
        val localDate = instant.atZone(zone).toLocalDate()
        val wallDate = LocalDate.now()
        assertNotEquals(utcDate, localDate)
        assertNotEquals(wallDate, localDate)
        assertTrue(
            env.service.commitRepeatDraft(RepeatDraft(title = "Take meds", anchorEpochDay = 0L)),
        )
        val stored = env.service.observeRepeats().first().single()
        assertEquals(localDate.toEpochDay(), stored.anchorEpochDay)
        assertNotEquals(utcDate.toEpochDay(), stored.anchorEpochDay)
        assertNotEquals(wallDate.toEpochDay(), stored.anchorEpochDay)
    }

    @Test
    fun midnightStillReemitsAcrossADstTransitionDay() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        val dstDay = LocalDate.of(2026, 3, 8)
        val env = harness(at(dstDay, 23, 59, zone), zone)
        env.service.create("Take meds", RepeatBand.MORNING, 1, dstDay.toEpochDay())
        env.service.create("Water plants", RepeatBand.NOON, 2, dstDay.toEpochDay())
        val rows = probe(env.service.observeRows())
        rows.await { it.lastOrNull()?.rows?.size == 2 }
        val count = rows.size
        env.advanceClockPastMidnight(at(dstDay.plusDays(1), 0, 1, zone))
        rows.await { it.size > count }
        assertEquals(listOf("Take meds"), rows.last().rows.map { it.repeat.title })
        assertEquals(RepeatDayStatus.OPEN, rows.last().rows.single().status)
        rows.close()
    }

    @Test
    fun repeatsAt2359StayOnTheirDayAnd0000IsTheNextLocalDay() = runBlocking {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 10, 16)
        val env = harness(at(day, 23, 59, zone), zone)
        env.service.create("Take meds", RepeatBand.MORNING, 1, day.toEpochDay())
        env.service.create("Water plants", RepeatBand.NOON, 2, day.toEpochDay())
        val rows = probe(env.service.observeRows())
        rows.awaitLast { state ->
            state.rows.map { it.repeat.title }.toSet() == setOf("Take meds", "Water plants") &&
                state.rows.all { it.status == RepeatDayStatus.MISSED } &&
                state.rows.all { it.epochDay == day.toEpochDay() }
        }

        env.clock.now = at(day.plusDays(1), 0, 0, zone)
        env.service.refreshToday()
        rows.awaitLast { state ->
            state.rows.single { it.repeat.title == "Take meds" }.let { row ->
                row.status == RepeatDayStatus.OPEN && row.epochDay == day.plusDays(1).toEpochDay()
            } &&
                state.rows.none { it.repeat.title == "Water plants" }
        }
        rows.close()
    }

    private fun lastTitles(states: List<RepeatRowsState>): Set<String>? = states.lastOrNull()?.rows?.map { it.repeat.title }?.toSet()

    private fun deliverToRepeatsService(action: String) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val intent = Intent(action)
        shadowOf(app).registeredReceivers.forEach { wrapper ->
            if (wrapper.intentFilter.hasAction(action) && wrapper.broadcastReceiver !is BandResyncReceiver) {
                wrapper.broadcastReceiver.onReceive(context, intent)
            }
        }
    }

    private fun harness(
        nowMillis: Long,
        zone: ZoneId,
        listenForDayChanges: Boolean = true,
    ): Harness {
        val clock = SettableClock(nowMillis)
        val zoneHolder = arrayOf(zone)
        val scheduler = TestCoroutineScheduler()
        val dispatcher = StandardTestDispatcher(scheduler)
        val job = SupervisorJob()
        val scope = CoroutineScope(job + dispatcher)
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database,
                PreferencesDataStore(context, "repeats-day-${UUID.randomUUID()}"),
                clock,
            )
        val service =
            RepeatsService(
                repository = repository,
                clock = clock,
                context = if (listenForDayChanges) context else null,
                zone = { zoneHolder[0] },
                scope = scope,
            )
        if (listenForDayChanges) {
            scheduler.runCurrent()
        }
        val harness =
            Harness(
                clock = clock,
                repository = repository,
                service = service,
                database = database,
                scheduler = scheduler,
                job = job,
                zoneHolder = zoneHolder,
            )
        open += harness
        return harness
    }

    private fun <T> CoroutineScope.probe(flow: Flow<T>): Probe<T> {
        val values = CopyOnWriteArrayList<T>()
        val job = launch { flow.collect { values += it } }
        return Probe(values, job)
    }

    private class Probe<T>(
        private val values: MutableList<T>,
        private val job: kotlinx.coroutines.Job,
    ) {
        val size: Int
            get() = values.size

        fun last(): T = values.last()

        suspend fun await(predicate: (List<T>) -> Boolean) {
            withTimeout(5_000) {
                while (!predicate(values)) {
                    shadowOf(Looper.getMainLooper()).idle()
                    yield()
                }
            }
        }

        suspend fun awaitLast(predicate: (T) -> Boolean) {
            await { it.isNotEmpty() && predicate(it.last()) }
        }

        fun close() {
            job.cancel()
        }
    }

    private class Harness(
        val clock: SettableClock,
        val repository: FocusRepositoryImpl,
        val service: RepeatsService,
        val database: FocusStackDatabase,
        val scheduler: TestCoroutineScheduler,
        val job: Job,
        private val zoneHolder: Array<ZoneId>,
    ) {
        var zoneId: ZoneId
            get() = zoneHolder[0]
            set(value) {
                zoneHolder[0] = value
            }

        fun advanceClockPastMidnight(nextNow: Long) {
            val wait = millisUntilNextLocalMidnight(clock.now, zoneId)
            clock.now = nextNow
            scheduler.advanceTimeBy(wait)
            scheduler.runCurrent()
        }

        fun close() {
            job.cancel()
            database.close()
        }
    }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }

    private companion object {
        fun at(
            day: LocalDate,
            hour: Int,
            minute: Int,
            zone: ZoneId,
        ): Long = day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

        fun millisUntilNextLocalMidnight(
            nowMillis: Long,
            zone: ZoneId,
        ): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return (nextMidnight - nowMillis).coerceAtLeast(1L)
        }
    }
}
