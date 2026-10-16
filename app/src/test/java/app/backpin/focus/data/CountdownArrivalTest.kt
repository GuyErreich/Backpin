package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class CountdownArrivalTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 9, 28)
    private val clock = SettableClock(0L)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepository

    @Before
    fun setUp() {
        clock.now = epochAt(today, 10, 0)
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "countdown-${UUID.randomUUID()}"),
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aCountdownWaitsWithoutATodaySlotUntilItsDay() =
        runBlocking {
            repository.captureCountdown("Dana's birthday", today.plusDays(12).toEpochDay())
            val countdown = taskNamed("Dana's birthday")
            assertEquals(TaskBucket.COUNTDOWN, countdown.bucket)
            assertEquals(today.plusDays(12).toEpochDay(), countdown.scheduledOnEpochDay)
            assertEquals(12, countdown.countdownIntervalDays)
            assertNull(repository.releaseDueCountdowns())
            repository.releaseDueScheduled()
            assertEquals(TaskBucket.COUNTDOWN, taskNamed("Dana's birthday").bucket)
            assertTrue(snapshot().frames.isEmpty())
            assertTrue(openToday().isEmpty())
        }

    @Test
    fun onItsDayItNestsUnderTheActiveTask() =
        runBlocking {
            val work = fillToday(1).single()
            repository.start(work)
            repository.updateReturnCue(work, "Finish the second paragraph")
            repository.captureCountdown("Dentist", today.toEpochDay())
            val arrived = repository.releaseDueCountdowns()
            assertEquals("Dentist", arrived?.title)
            val frames = snapshot().frames
            val parent = frames.single { it.taskId == work }
            val child = frames.single { it.taskId == arrived!!.id }
            assertEquals(FrameState.PAUSED, parent.state)
            assertEquals(FrameState.ACTIVE, child.state)
            assertEquals(parent.id, child.parentFrameId)
            assertEquals("Finish the second paragraph", task(work).returnCue)
            assertEquals(TaskBucket.TODAY, task(work).bucket)
            assertEquals(TaskBucket.COUNTDOWN, task(arrived!!.id).bucket)
            assertEquals(1, openToday().size)
        }

    @Test
    fun withNothingActiveItStartsOnItsOwn() =
        runBlocking {
            repository.captureCountdown("Dentist", today.toEpochDay())
            val arrived = repository.releaseDueCountdowns()
            assertNotNull(arrived)
            val frame = snapshot().frames.single()
            assertEquals(arrived!!.id, frame.taskId)
            assertEquals(FrameState.ACTIVE, frame.state)
            assertNull(frame.parentFrameId)
            assertTrue(openToday().isEmpty())
        }

    @Test
    fun itArrivesOutsideQuietHoursAndNotInsideThem() =
        runBlocking {
            repository.setQuietHours("22:00", "08:00")
            repository.captureCountdown("Dentist", today.toEpochDay())
            clock.now = epochAt(today, 6, 30)
            assertNull(repository.releaseDueCountdowns())
            assertTrue(snapshot().frames.isEmpty())
            clock.now = epochAt(today, 8, 0)
            assertEquals("Dentist", repository.releaseDueCountdowns()?.title)
            assertEquals(FrameState.ACTIVE, snapshot().frames.single().state)
        }

    @Test
    fun itDoesNotArriveLateAtNightOnItsDay() =
        runBlocking {
            repository.setQuietHours("22:00", "08:00")
            clock.now = epochAt(today, 23, 0)
            repository.captureCountdown("Dentist", today.toEpochDay())
            assertNull(repository.releaseDueCountdowns())
            assertTrue(snapshot().frames.isEmpty())
        }

    @Test
    fun onlyOneCountdownInterruptsAtATime() =
        runBlocking {
            repository.captureCountdown("First", today.minusDays(1).toEpochDay())
            clock.now += 1_000
            repository.captureCountdown("Second", today.toEpochDay())
            assertEquals("First", repository.releaseDueCountdowns()?.title)
            assertNull(repository.releaseDueCountdowns())
            assertTrue(snapshot().frames.none { it.taskId == taskNamed("Second").id })

            repository.completeActive()
            assertEquals("Second", repository.releaseDueCountdowns()?.title)
            assertEquals(
                taskNamed("Second").id,
                snapshot().frames.single { it.state == FrameState.ACTIVE }.taskId,
            )
        }

    @Test
    fun doneReturnsToThePausedTaskWithoutResumingIt() =
        runBlocking {
            val work = startedToday()
            arrive("Dentist")
            repository.completeActive()
            assertEquals(TaskStatus.DONE, taskNamed("Dentist").status)
            assertParentWaits(work)
        }

    @Test
    fun todayWithAFreeSlotPlacesTheEventWithoutStartingIt() =
        runBlocking {
            val work = startedToday()
            val event = arrive("Dentist")
            val outcome = repository.detachInterruption(event.id, DetachDestination.Today)
            assertEquals(PromoteOutcome.Promoted, outcome)
            assertEquals(TaskBucket.TODAY, task(event.id).bucket)
            assertEquals(TaskStatus.OPEN, task(event.id).status)
            assertNull(task(event.id).scheduledOnEpochDay)
            assertTrue(snapshot().frames.filter { it.taskId == event.id }.all { it.state == FrameState.CLOSED })
            assertParentWaits(work)
        }

    @Test
    fun todayWhenFullAsksForReplaceAndStillReturnsToTheParent() =
        runBlocking {
            val ids = fillToday(3)
            repository.start(ids[0])
            val event = arrive("Dentist")
            val outcome = repository.detachInterruption(event.id, DetachDestination.Today)
            assertTrue(outcome is PromoteOutcome.NeedsSwap)
            assertEquals(ids.toSet(), (outcome as PromoteOutcome.NeedsSwap).todayTasks.map { it.id }.toSet())
            assertEquals(false, outcome.canInterrupt)
            assertEquals(TaskBucket.INBOX, task(event.id).bucket)
            assertEquals(3, openToday().size)
            assertParentWaits(ids[0])

            repository.swapIntoToday(event.id, ids[2])
            assertEquals(TaskBucket.TODAY, task(event.id).bucket)
            assertEquals(TaskBucket.LATER, task(ids[2]).bucket)
            assertParentWaits(ids[0])
        }

    @Test
    fun laterParksTheEventAndReturnsToTheParent() =
        runBlocking {
            val work = startedToday()
            val event = arrive("Dentist")
            assertEquals(PromoteOutcome.Promoted, repository.detachInterruption(event.id, DetachDestination.Later))
            assertEquals(TaskBucket.LATER, task(event.id).bucket)
            assertNull(task(event.id).scheduledOnEpochDay)
            assertParentWaits(work)
        }

    @Test
    fun countDownAgainGoesBackToTheCountdownList() =
        runBlocking {
            val work = startedToday()
            val event = arrive("Dentist")
            val next = today.plusDays(30).toEpochDay()
            assertEquals(
                PromoteOutcome.Promoted,
                repository.detachInterruption(event.id, DetachDestination.Countdown(next)),
            )
            assertEquals(TaskBucket.COUNTDOWN, task(event.id).bucket)
            assertEquals(next, task(event.id).scheduledOnEpochDay)
            assertEquals(30, task(event.id).countdownIntervalDays)
            assertParentWaits(work)
            assertNull(repository.releaseDueCountdowns())
        }

    @Test
    fun captureRescheduleAndDetachWriteTheIntervalAndKeepItThroughAMove() =
        runBlocking {
            repository.captureCountdown("Trip", today.plusDays(12).toEpochDay())
            val trip = taskNamed("Trip")
            assertEquals(12, trip.countdownIntervalDays)
            assertEquals(12, repository.preferences.first().lastCountdownDays)

            repository.rescheduleCountdown(trip.id, today.plusDays(9).toEpochDay())
            assertEquals(9, task(trip.id).countdownIntervalDays)
            assertEquals(9, repository.preferences.first().lastCountdownDays)

            val work = startedToday()
            val event = arrive("Dentist")
            assertNull(event.countdownIntervalDays)
            assertEquals(
                PromoteOutcome.Promoted,
                repository.detachInterruption(event.id, DetachDestination.Countdown(today.plusDays(21).toEpochDay())),
            )
            val resumed = task(event.id)
            assertEquals(TaskBucket.COUNTDOWN, resumed.bucket)
            assertEquals(21, resumed.countdownIntervalDays)
            assertEquals(21, repository.preferences.first().lastCountdownDays)

            val parked = arrive("Parked")
            val parkedRow = database.taskDao().getById(parked.id) ?: error("Missing parked event")
            database.taskDao().upsert(parkedRow.copy(countdownIntervalDays = 14))
            repository.detachInterruption(parked.id, DetachDestination.Later)
            assertEquals(TaskBucket.LATER, task(parked.id).bucket)
            assertEquals(14, task(parked.id).countdownIntervalDays)
            assertEquals(21, task(event.id).countdownIntervalDays)
            assertEquals(21, repository.preferences.first().lastCountdownDays)
            assertParentWaits(work)
        }

    @Test
    fun countDownAgainRefusesTodayOrEarlier() =
        runBlocking {
            val work = startedToday()
            val event = arrive("Dentist")
            val outcome = repository.detachInterruption(event.id, DetachDestination.Countdown(today.toEpochDay()))
            assertTrue(outcome is PromoteOutcome.Rejected)
            assertEquals(FrameState.ACTIVE, snapshot().frames.single { it.taskId == event.id }.state)
            assertEquals(FrameState.PAUSED, snapshot().frames.single { it.taskId == work }.state)
        }

    @Test
    fun anArrivedEventIsNotEditedFromTheList() =
        runBlocking {
            val event = arrive("Dentist")
            repository.rescheduleCountdown(event.id, today.plusDays(3).toEpochDay())
            repository.removeCountdown(event.id)
            assertEquals(today.toEpochDay(), task(event.id).scheduledOnEpochDay)

            repository.captureCountdown("Trip", today.plusDays(5).toEpochDay())
            val trip = taskNamed("Trip")
            repository.rescheduleCountdown(trip.id, today.plusDays(9).toEpochDay())
            assertEquals(today.plusDays(9).toEpochDay(), task(trip.id).scheduledOnEpochDay)
            assertEquals(9, task(trip.id).countdownIntervalDays)
            repository.removeCountdown(trip.id)
            assertTrue(snapshot().tasks.none { it.id == trip.id })
        }

    private suspend fun startedToday(): String {
        val id = fillToday(1).single()
        repository.start(id)
        return id
    }

    private suspend fun arrive(title: String): Task {
        repository.captureCountdown(title, today.toEpochDay())
        return repository.releaseDueCountdowns() ?: error("Countdown did not arrive")
    }

    private suspend fun assertParentWaits(parentTaskId: String) {
        val frames = snapshot().frames
        assertEquals(FrameState.AWAITING_RESUME, frames.single { it.taskId == parentTaskId }.state)
        assertTrue(frames.none { it.state == FrameState.ACTIVE })
    }

    private fun epochAt(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private suspend fun fillToday(count: Int): List<String> =
        (1..count).map { index ->
            val title = "Today $index"
            repository.capture(title)
            val id = snapshot().tasks.single { it.title == title }.id
            repository.promote(id)
            id
        }

    private suspend fun snapshot(): FocusSnapshot = repository.observeSnapshot().first()

    private suspend fun openToday() =
        snapshot().tasks.filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }

    private suspend fun task(id: String) = snapshot().tasks.single { it.id == id }

    private suspend fun taskNamed(title: String) = snapshot().tasks.single { it.title == title }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }
}
