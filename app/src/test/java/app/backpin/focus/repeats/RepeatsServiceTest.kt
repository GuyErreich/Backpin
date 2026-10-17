// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.repeats

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimeResult
import app.backpin.focus.domain.ExactAlarmAccess
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.RepeatScheduleSync
import app.backpin.focus.domain.toDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatsServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 10, 16)
    private val clock = SettableClock(0L)
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepositoryImpl
    private lateinit var service: RepeatsService
    private var resyncs: Int = 0
    private var exactAllowed: Boolean = false

    @Before
    fun setUp() {
        clock.now = at(8, 0)
        resyncs = 0
        exactAllowed = false
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            FocusRepositoryImpl(
                database,
                PreferencesDataStore(context, "repeats-api-${UUID.randomUUID()}"),
                clock,
            )
        service =
            RepeatsService(
                repository = repository,
                schedule = RepeatScheduleSync { resyncs += 1 },
                exactAlarms = ExactAlarmAccess { exactAllowed },
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rowsOrderOpenThenMissedThenDoneAndCounts() {
        runBlocking {
            val makeBed = create("Make bed", RepeatBand.MORNING)
            val takeMeds = create("Take meds", RepeatBand.MORNING)
            val plants = create("Water plants", RepeatBand.NOON)
            create("Brush teeth", RepeatBand.NIGHT)
            assertTrue(service.markDone(takeMeds, today.toEpochDay()))
            repository.markOpenRepeatsMissed(RepeatBand.NOON, today.toEpochDay())
            val rows = service.observeRows().first()
            assertEquals(
                listOf("Make bed", "Brush teeth", "Water plants", "Take meds"),
                rows.rows.map { it.repeat.title },
            )
            assertEquals(3, rows.leftCount)
            assertEquals(4, rows.total)
            assertEquals(RepeatDayStatus.OPEN, rows.rows.single { it.repeat.id == makeBed }.status)
            assertEquals(RepeatDayStatus.MISSED, rows.rows.single { it.repeat.id == plants }.status)
            val weekly =
                service.create("Laundry", RepeatBand.NIGHT, 3, today.toEpochDay())!!
            val laundry = service.observeRows().first().rows.single { it.repeat.id == weekly }
            assertEquals(3, laundry.everyNDays)
            assertEquals(3, laundry.cadenceDays)
        }
    }

    @Test
    fun sheetOpensAndClosesOnSuccessfulWrite() {
        runBlocking {
            service.openCreate("Take meds")
            val draft = service.observeEditing().first()
            assertEquals("Take meds", draft?.title)
            assertFalse(draft!!.editing)
            assertNull(service.create("  ", RepeatBand.MORNING, 1, today.toEpochDay()))
            assertNotNull(service.observeEditing().first())
            val id = service.create("Take meds", RepeatBand.MORNING, 1, today.toEpochDay())
            assertNotNull(id)
            assertNull(service.observeEditing().first())
            val repeat = service.observeRepeats().first().single()
            service.openEdit(repeat)
            assertEquals(id, service.observeEditing().first()?.id)
            assertTrue(
                service.update(
                    id = id!!,
                    title = "Vitamins",
                    band = RepeatBand.NOON,
                    everyNDays = 2,
                    anchorEpochDay = today.toEpochDay(),
                    reminderOn = false,
                    sortOrder = repeat.sortOrder,
                ),
            )
            assertNull(service.observeEditing().first())
            service.openEdit(service.observeRepeats().first().single())
            assertTrue(service.archive(id))
            assertNull(service.observeEditing().first())
            assertTrue(service.observeRepeats().first().isEmpty())
        }
    }

    @Test
    fun commitRepeatDraftSavesOnCreateAndEdit() {
        runBlocking {
            val created =
                RepeatDraft(
                    title = "Take meds",
                    band = RepeatBand.MORNING,
                    everyNDays = 2,
                    anchorEpochDay = today.toEpochDay(),
                )
            service.openCreate("Take meds")
            assertFalse(service.commitRepeatDraft(created.copy(title = "  ")))
            assertNotNull(service.observeEditing().first())
            assertTrue(service.observeRepeats().first().isEmpty())
            assertTrue(service.commitRepeatDraft(created))
            assertNull(service.observeEditing().first())
            val stored = service.observeRepeats().first().single()
            assertEquals("Take meds", stored.title)
            assertEquals(2, stored.everyNDays)
            val row = service.observeRows().first().rows.single()
            assertEquals(2, row.everyNDays)
            assertEquals(2, row.cadenceDays)
            service.openEdit(stored)
            assertTrue(
                service.commitRepeatDraft(
                    stored.toDraft().copy(title = "Vitamins", band = RepeatBand.NOON, everyNDays = 3),
                ),
            )
            val updated = service.observeRepeats().first().single()
            assertEquals("Vitamins", updated.title)
            assertEquals(RepeatBand.NOON, updated.band)
            assertEquals(3, updated.everyNDays)
            assertNull(service.observeEditing().first())
        }
    }

    @Test
    fun markDoneOffersUndoThatRestoresOpen() {
        runBlocking {
            val id = create("Take meds", RepeatBand.MORNING)
            assertTrue(service.markDone(id, today.toEpochDay()))
            assertEquals(RepeatDayStatus.DONE, statusOf(id))
            val undo = service.observePendingUndo().first()
            assertNotNull(undo)
            assertEquals(id, undo!!.repeatId)
            assertEquals(RepeatDayStatus.OPEN, undo.restore)
            assertTrue(service.undo(undo.id))
            assertEquals(RepeatDayStatus.OPEN, statusOf(id))
            assertNull(service.observePendingUndo().first())
            assertNull(database.repeatLogDao().get(id, today.toEpochDay()))
        }
    }

    @Test
    fun lateDoneOnMissedRestoresMissedOnUndo() {
        runBlocking {
            val id = create("Water plants", RepeatBand.NOON)
            repository.markOpenRepeatsMissed(RepeatBand.NOON, today.toEpochDay())
            assertEquals(RepeatDayStatus.MISSED, statusOf(id))
            assertEquals(RepeatOutcome.MISSED.name, database.repeatLogDao().get(id, today.toEpochDay())?.outcome)
            assertTrue(service.markDone(id, today.toEpochDay()))
            assertEquals(RepeatDayStatus.DONE, statusOf(id))
            val undo = service.observePendingUndo().first()
            assertEquals(RepeatDayStatus.MISSED, undo!!.restore)
            assertTrue(service.undo(undo.id))
            assertEquals(RepeatDayStatus.MISSED, statusOf(id))
            assertEquals(RepeatOutcome.MISSED.name, database.repeatLogDao().get(id, today.toEpochDay())?.outcome)
        }
    }

    @Test
    fun setBandTimeValidatesOrderAndExposesResult() {
        runBlocking {
            assertEquals(BandTimeResult.Applied, service.setBandTime(BandEdge.MORNING, 8 * 60))
            assertEquals(BandTimeResult.Applied, service.observeBandTimeResult().first())
            assertEquals(8 * 60, service.observeBandTimes().first().morningStartMinute)
            assertEquals(BandTimeResult.Rejected, service.setBandTime(BandEdge.MORNING, 13 * 60))
            assertEquals(BandTimeResult.Rejected, service.observeBandTimeResult().first())
            assertEquals(8 * 60, service.observeBandTimes().first().morningStartMinute)
            assertEquals(BandTimeResult.Applied, service.setBandTime(BandEdge.NIGHT_END, 23 * 60 + 59))
            assertEquals(23 * 60 + 59, service.observeBandTimes().first().nightEndMinute)
            assertTrue(resyncs >= 2)
        }
    }

    @Test
    fun exactAlarmPromptShowsUntilDismissedOrGranted() {
        runBlocking {
            assertFalse(service.canScheduleExactAlarms())
            assertTrue(service.observeExactAlarmPrompt().first())
            service.dismissExactAlarmPrompt()
            assertFalse(service.observeExactAlarmPrompt().first())
            exactAllowed = true
            service.refreshExactAlarmPermission()
            assertTrue(service.canScheduleExactAlarms())
            assertFalse(service.observeExactAlarmPrompt().first())
        }
    }

    @Test
    fun createResyncsAlarms() {
        runBlocking {
            create("Take meds", RepeatBand.MORNING)
            assertEquals(1, resyncs)
        }
    }

    private suspend fun create(
        title: String,
        band: RepeatBand,
    ): String = service.create(title, band, 1, today.toEpochDay())!!

    private suspend fun statusOf(id: String): RepeatDayStatus = service
        .observeTodaysRepeats()
        .first()
        .all()
        .single { it.repeat.id == id }
        .status

    private fun at(
        hour: Int,
        minute: Int,
    ): Long = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }
}
