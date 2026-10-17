// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BandBoundaryTickTest {
    @Test
    fun eachDefaultBoundFiresOnceAtThatMinute() {
        assertTickAt(7, 0)
        assertTickAt(12, 0)
        assertTickAt(18, 0)
        assertTickAt(23, 0)
    }

    @Test
    fun refreshTodayTicksOnceAndDoesNotAlsoFireTheCancelledWait() {
        val env = env(millisBeforeBound(12, 0))
        try {
            val before = env.ticks.size
            env.clock.now += 1
            env.repeats.refreshToday()
            val tick = env.awaitTick(before)
            assertEquals(env.clock.now, tick)
            assertEquals(before + 1, env.ticks.size)
            Thread.sleep(AFTER_CANCEL_MS)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before + 1, env.ticks.size)
        } finally {
            env.close()
        }
    }

    @Test
    fun bandTimeChangeRestartsWaitWithoutATickFromTheOldBound() {
        val env = env(millisBeforeBound(7, 0))
        try {
            val before = env.ticks.size
            runBlocking { env.repeats.setBandTime(BandEdge.MORNING, 8 * 60) }
            Thread.sleep(AFTER_CANCEL_MS)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before, env.ticks.size)
        } finally {
            env.close()
        }
    }

    @Test
    fun dateTimeZoneBroadcastRestartsLikeRefreshToday() {
        val env = env(millisBeforeBound(18, 0))
        try {
            val before = env.ticks.size
            env.clock.now += 1
            val app = RuntimeEnvironment.getApplication()
            val intent = Intent(Intent.ACTION_TIME_CHANGED)
            val receivers =
                shadowOf(app).getReceiversForIntent(intent).filter { receiver ->
                    receiver.javaClass.name.startsWith("app.backpin.focus.repeats.RepeatsService")
                }
            assertTrue(receivers.isNotEmpty())
            receivers.forEach { receiver ->
                receiver.onReceive(app, intent)
            }
            shadowOf(Looper.getMainLooper()).idle()
            val tick = env.awaitTick(before)
            assertEquals(env.clock.now, tick)
            assertEquals(before + 1, env.ticks.size)
            Thread.sleep(AFTER_CANCEL_MS)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before + 1, env.ticks.size)
        } finally {
            env.close()
        }
    }

    private fun assertTickAt(
        hour: Int,
        minute: Int,
    ) {
        val bound = at(hour, minute)
        val env = env(bound - WAIT_BEFORE_MS)
        try {
            val before = env.ticks.size
            env.clock.now = bound
            val tick = env.awaitTick(before)
            assertEquals(bound, tick)
            assertEquals(before + 1, env.ticks.size)
            Thread.sleep(AFTER_CANCEL_MS)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before + 1, env.ticks.size)
        } finally {
            env.close()
        }
    }

    private fun millisBeforeBound(
        hour: Int,
        minute: Int,
    ): Long = at(hour, minute) - WAIT_BEFORE_MS

    private fun env(now: Long): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val clock = SettableClock(now)
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database,
                PreferencesDataStore(context, "band-tick-${UUID.randomUUID()}"),
                clock,
            )
        runBlocking { repository.preferences.first() }
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val repeats =
            RepeatsService(
                repository = repository,
                clock = clock,
                context = context,
                zone = { ZONE },
                scope = scope,
            )
        val ticks = mutableListOf<Long>()
        val collector =
            scope.launch {
                repeats.observeBandBoundaryTick().collect { ticks += it }
            }
        waitUntil(2_000) { ticks.isNotEmpty() }
        Thread.sleep(LOOP_ARMED_MS)
        return Env(
            context = context,
            clock = clock,
            database = database,
            repeats = repeats,
            job = job,
            ticks = ticks,
            collector = collector,
        )
    }

    private class Env(
        val context: Context,
        val clock: SettableClock,
        val database: FocusStackDatabase,
        val repeats: RepeatsService,
        val job: Job,
        val ticks: MutableList<Long>,
        val collector: Job,
    ) {
        fun awaitTick(beforeSize: Int): Long {
            waitUntil(2_000) { ticks.size > beforeSize }
            return ticks.last()
        }

        fun close() {
            collector.cancel()
            runBlocking { job.cancelAndJoin() }
            database.close()
        }
    }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.of("UTC")
        const val WAIT_BEFORE_MS: Long = 150L
        const val LOOP_ARMED_MS: Long = 50L
        const val AFTER_CANCEL_MS: Long = 250L

        fun at(
            hour: Int,
            minute: Int,
        ): Long = DAY.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

        fun waitUntil(
            timeoutMs: Long,
            condition: () -> Boolean,
        ) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition()) {
                check(System.currentTimeMillis() < deadline) { "timed out after ${timeoutMs}ms" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
        }
    }
}
