// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
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
class RepeatRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 10, 16)
    private val clock = SettableClock(0L)
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepository
    private lateinit var preferences: PreferencesDataStore

    @Before
    fun setUp() {
        clock.now = at(8, 0)
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        preferences = PreferencesDataStore(context, "repeats-${UUID.randomUUID()}")
        repository = FocusRepositoryImpl(database, preferences, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createObserveAndNeverTouchToday() =
        runBlocking {
            val id =
                repository.createRepeat(
                    title = "Take meds",
                    band = RepeatBand.MORNING,
                    everyNDays = 1,
                    anchorEpochDay = today.toEpochDay(),
                )
            assertNotNull(id)
            val morning = repository.observeTodaysRepeats().first().morning
            assertEquals(1, morning.size)
            assertEquals("Take meds", morning.single().repeat.title)
            assertEquals(RepeatDayStatus.OPEN, morning.single().status)
            assertTrue(repository.observeSnapshot().first().tasks.isEmpty())
            repository.capture("A today task")
            val taskId = repository.observeSnapshot().first().tasks.single().id
            repository.promote(taskId)
            assertEquals(1, openToday().size)
            assertEquals(TaskBucket.TODAY, openToday().single().bucket)
            assertEquals(1, repository.observeTodaysRepeats().first().morning.size)
        }

    @Test
    fun blankTitleAndOffDayStayOut() =
        runBlocking {
            assertNull(
                repository.createRepeat("  ", RepeatBand.NOON, 1, today.toEpochDay()),
            )
            repository.createRepeat("Water plants", RepeatBand.NOON, 2, today.plusDays(1).toEpochDay())
            assertTrue(repository.observeTodaysRepeats().first().noon.isEmpty())
            assertEquals(1, repository.observeRepeats().first().size)
        }

    @Test
    fun updateArchiveDoneAndUndo() =
        runBlocking {
            val id =
                repository.createRepeat("Make bed", RepeatBand.MORNING, 1, today.toEpochDay())!!
            assertTrue(
                repository.updateRepeat(
                    id = id,
                    title = "Make the bed",
                    band = RepeatBand.NOON,
                    everyNDays = 1,
                    anchorEpochDay = today.toEpochDay(),
                    reminderOn = false,
                    sortOrder = 9,
                ),
            )
            val updated = repository.observeRepeats().first().single()
            assertEquals("Make the bed", updated.title)
            assertEquals(RepeatBand.NOON, updated.band)
            assertFalse(updated.reminderOn)
            assertTrue(repository.markRepeatDone(id, today.toEpochDay()))
            assertTrue(repository.markRepeatDone(id, today.toEpochDay()))
            assertEquals(RepeatDayStatus.DONE, repository.observeTodaysRepeats().first().noon.single().status)
            assertTrue(repository.undoRepeatDone(id, today.toEpochDay()))
            assertEquals(RepeatDayStatus.OPEN, repository.observeTodaysRepeats().first().noon.single().status)
            assertTrue(repository.archiveRepeat(id))
            assertTrue(repository.observeRepeats().first().isEmpty())
            assertTrue(repository.observeTodaysRepeats().first().noon.isEmpty())
        }

    @Test
    fun missedStaysVisibleThroughTheDay() =
        runBlocking {
            val id = repository.createRepeat("Vitamins", RepeatBand.MORNING, 1, today.toEpochDay())!!
            clock.now = at(13, 0)
            assertEquals(RepeatDayStatus.MISSED, repository.observeTodaysRepeats().first().morning.single().status)
            repository.markOpenRepeatsMissed(RepeatBand.MORNING, today.toEpochDay())
            val log = database.repeatLogDao().get(id, today.toEpochDay())
            assertEquals(RepeatOutcome.MISSED.name, log?.outcome)
            assertEquals(RepeatDayStatus.MISSED, repository.observeTodaysRepeats().first().morning.single().status)
            assertFalse(repository.undoRepeatDone(id, today.toEpochDay()))
            assertTrue(repository.markRepeatDone(id, today.toEpochDay()))
            assertEquals(RepeatDayStatus.DONE, repository.observeTodaysRepeats().first().morning.single().status)
        }

    @Test
    fun bandTimesRoundTripAndRejectOutOfOrder() =
        runBlocking {
            val defaults = repository.preferences.first().bandTimes
            assertEquals(BandTimes.DEFAULT, defaults)
            assertTrue(repository.setBandTimes(BandTimes(8 * 60, 13 * 60, 19 * 60, 22 * 60)))
            assertEquals(8 * 60, repository.preferences.first().bandTimes.morningStartMinute)
            assertFalse(repository.setBandTimes(BandTimes(12 * 60, 7 * 60, 18 * 60, 23 * 60)))
            assertEquals(8 * 60, repository.preferences.first().bandTimes.morningStartMinute)
        }

    @Test
    fun heldBandNudgePersists() =
        runBlocking {
            repository.setHeldBandNudge(RepeatBand.NIGHT, today.toEpochDay())
            val prefs = repository.preferences.first()
            assertEquals(RepeatBand.NIGHT, prefs.heldBandNudge)
            assertEquals(today.toEpochDay(), prefs.heldBandEpochDay)
            repository.setHeldBandNudge(null, null)
            assertNull(repository.preferences.first().heldBandNudge)
        }

    private fun at(
        hour: Int,
        minute: Int,
    ): Long = today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private suspend fun openToday() =
        repository.observeSnapshot().first().tasks.filter {
            it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN
        }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }
}
