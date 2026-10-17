// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ScheduledReleaseTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 9, 27)
    private val clock = SettableClock(epochAt(today))
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepository

    @Before
    fun setUp() {
        clock.now = epochAt(today)
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "sched-${UUID.randomUUID()}"),
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aDueTaskTakesAFreeSlotAndDoesNotStart() =
        runBlocking {
            fillToday(2)
            repository.captureScheduled("Call Dana", today.toEpochDay())
            repository.releaseDueScheduled()
            val task = taskNamed("Call Dana")
            assertEquals(TaskBucket.TODAY, task.bucket)
            assertNull(task.scheduledOnEpochDay)
            assertTrue(snapshot().frames.none { it.taskId == task.id })
            assertEquals(3, openToday().size)
        }

    @Test
    fun aFullTodayLeavesTheDueTaskScheduled() =
        runBlocking {
            val todayIds = fillToday(3)
            repository.start(todayIds[0])
            repository.captureScheduled("Call Dana", today.minusDays(1).toEpochDay())
            repository.releaseDueScheduled()
            val waiting = taskNamed("Call Dana")
            assertEquals(TaskBucket.SCHEDULED, waiting.bucket)
            assertEquals(today.minusDays(1).toEpochDay(), waiting.scheduledOnEpochDay)
            assertEquals(todayIds.toSet(), openToday().map { it.id }.toSet())
            assertTrue(snapshot().tasks.none { it.bucket == TaskBucket.LATER })
            assertEquals(todayIds[0], snapshot().frames.single { it.state == FrameState.ACTIVE }.taskId)
            assertTrue(snapshot().frames.none { it.taskId == waiting.id })
        }

    @Test
    fun aFutureDateStaysScheduled() =
        runBlocking {
            repository.captureScheduled("Next week", today.plusDays(7).toEpochDay())
            repository.releaseDueScheduled()
            val task = taskNamed("Next week")
            assertEquals(TaskBucket.SCHEDULED, task.bucket)
            assertEquals(today.plusDays(7).toEpochDay(), task.scheduledOnEpochDay)
            assertTrue(openToday().isEmpty())
        }

    @Test
    fun finishingATodayTaskLetsTheDueTaskIn() =
        runBlocking {
            val todayIds = fillToday(3)
            repository.start(todayIds[0])
            repository.captureScheduled("Call Dana", today.toEpochDay())
            repository.releaseDueScheduled()
            assertEquals(TaskBucket.SCHEDULED, taskNamed("Call Dana").bucket)
            repository.completeActive()
            val moved = taskNamed("Call Dana")
            assertEquals(TaskBucket.TODAY, moved.bucket)
            assertNull(moved.scheduledOnEpochDay)
            assertTrue(snapshot().frames.none { it.taskId == moved.id })
            assertEquals(3, openToday().size)
        }

    @Test
    fun onlyTheOldestDueTaskMovesWhenOneSlotIsFree() =
        runBlocking {
            fillToday(2)
            repository.captureScheduled("Older", today.minusDays(3).toEpochDay())
            clock.now = epochAt(today) + 1_000
            repository.captureScheduled("Newer", today.minusDays(1).toEpochDay())
            repository.releaseDueScheduled()
            assertEquals(TaskBucket.TODAY, taskNamed("Older").bucket)
            assertEquals(TaskBucket.SCHEDULED, taskNamed("Newer").bucket)
            assertEquals(today.minusDays(1).toEpochDay(), taskNamed("Newer").scheduledOnEpochDay)
            assertEquals(3, openToday().size)
        }

    @Test
    fun movingEarlyLeavesTheSchedule() =
        runBlocking {
            val laterDay = today.plusDays(4).toEpochDay()
            repository.captureScheduled("Talk to Dana", laterDay)
            val id = taskNamed("Talk to Dana").id
            repository.promote(id)
            assertEquals(TaskBucket.TODAY, task(id).bucket)
            assertNull(task(id).scheduledOnEpochDay)
            assertTrue(snapshot().frames.none { it.taskId == id })

            repository.captureScheduled("Inbox one", laterDay)
            val inboxId = taskNamed("Inbox one").id
            repository.moveToInbox(inboxId)
            assertEquals(TaskBucket.INBOX, task(inboxId).bucket)
            assertNull(task(inboxId).scheduledOnEpochDay)

            repository.captureScheduled("Later one", laterDay)
            val laterId = taskNamed("Later one").id
            repository.demote(laterId)
            assertEquals(TaskBucket.LATER, task(laterId).bucket)
            assertNull(task(laterId).scheduledOnEpochDay)

            repository.captureScheduled("Start one", laterDay)
            val startId = taskNamed("Start one").id
            repository.start(startId)
            assertEquals(TaskBucket.TODAY, task(startId).bucket)
            assertEquals(FrameState.ACTIVE, snapshot().frames.single { it.taskId == startId }.state)
            assertNull(snapshot().frames.single { it.taskId == startId }.parentFrameId)

            repository.captureScheduled("Remove one", laterDay)
            val removeId = taskNamed("Remove one").id
            repository.removeScheduled(removeId)
            assertTrue(snapshot().tasks.none { it.id == removeId })
        }

    @Test
    fun raisingTheTodayLimitPullsADueTaskAndLeavesInbox() =
        runBlocking {
            val kept = fillToday(1).single()
            repository.setTodayLimit(1)
            repository.capture("Stays in inbox")
            repository.captureScheduled("Due", today.toEpochDay())
            repository.setTodayLimit(3)
            assertEquals(TaskBucket.TODAY, taskNamed("Due").bucket)
            assertNull(taskNamed("Due").scheduledOnEpochDay)
            assertEquals(TaskBucket.INBOX, taskNamed("Stays in inbox").bucket)
            assertTrue(openToday().map { it.id }.contains(kept))
            assertTrue(snapshot().frames.none { it.taskId == taskNamed("Due").id })
        }

    @Test
    fun aDatedTaskWithATimeWaitsForThatTime() =
        runBlocking {
            clock.now = epochAt(today, 14, 0)
            repository.captureScheduled("Call Dana", today.toEpochDay(), 14 * 60 + 30)
            repository.releaseDueScheduled()
            val waiting = taskNamed("Call Dana")
            assertEquals(TaskBucket.SCHEDULED, waiting.bucket)
            assertEquals(14 * 60 + 30, waiting.scheduledAtMinuteOfDay)

            clock.now = epochAt(today, 14, 31)
            repository.releaseDueScheduled()
            val moved = taskNamed("Call Dana")
            assertEquals(TaskBucket.TODAY, moved.bucket)
            assertNull(moved.scheduledOnEpochDay)
            assertNull(moved.scheduledAtMinuteOfDay)
            assertTrue(snapshot().frames.none { it.taskId == moved.id })
        }

    @Test
    fun aTimedTaskFromAnEarlierDayIsDueAtOnce() =
        runBlocking {
            clock.now = epochAt(today, 8, 0)
            repository.captureScheduled("Yesterday late", today.minusDays(1).toEpochDay(), 23 * 60)
            repository.captureScheduled("Tomorrow early", today.plusDays(1).toEpochDay(), 0)
            repository.releaseDueScheduled()
            assertEquals(TaskBucket.TODAY, taskNamed("Yesterday late").bucket)
            assertEquals(TaskBucket.SCHEDULED, taskNamed("Tomorrow early").bucket)
        }

    @Test
    fun changingTheDayOrTimeKeepsTheSameScheduledTask() =
        runBlocking {
            val day = today.plusDays(4).toEpochDay()
            repository.captureScheduled("Call Dana", day, 9 * 60)
            val id = taskNamed("Call Dana").id
            val next = today.plusDays(11).toEpochDay()
            assertTrue(repository.rescheduleScheduled(id, next))
            assertEquals(id, taskNamed("Call Dana").id)
            assertEquals(next, task(id).scheduledOnEpochDay)
            assertEquals(9 * 60, task(id).scheduledAtMinuteOfDay)
            assertEquals(1, snapshot().tasks.count { it.title == "Call Dana" })

            assertTrue(repository.setScheduledTime(id, 15 * 60))
            assertEquals(id, task(id).id)
            assertEquals(next, task(id).scheduledOnEpochDay)
            assertEquals(15 * 60, task(id).scheduledAtMinuteOfDay)

            assertTrue(repository.setScheduledTime(id, null))
            assertNull(task(id).scheduledAtMinuteOfDay)
            assertEquals(next, task(id).scheduledOnEpochDay)
            assertEquals(1, snapshot().tasks.count { it.bucket == TaskBucket.SCHEDULED })
        }

    @Test
    fun aTimedTaskStillWaitsWhenTodayIsFull() =
        runBlocking {
            fillToday(3)
            clock.now = epochAt(today, 15, 0)
            repository.captureScheduled("Call Dana", today.toEpochDay(), 14 * 60 + 30)
            repository.releaseDueScheduled()
            assertEquals(TaskBucket.SCHEDULED, taskNamed("Call Dana").bucket)
            assertEquals(14 * 60 + 30, taskNamed("Call Dana").scheduledAtMinuteOfDay)
        }

    @Test
    fun schedulingAnInboxTaskClearsItsPileAndKeepsTheDay() =
        runBlocking {
            repository.capture("Call Dana")
            val id = taskNamed("Call Dana").id
            val day = today.plusDays(5).toEpochDay()
            assertTrue(repository.scheduleExisting(id, day))
            val moved = task(id)
            assertEquals(TaskBucket.SCHEDULED, moved.bucket)
            assertEquals(day, moved.scheduledOnEpochDay)
            assertNull(moved.scheduledAtMinuteOfDay)
            assertTrue(snapshot().frames.none { it.taskId == id })
        }

    @Test
    fun inboxFromLaterClearsAScheduleAfterAMove() =
        runBlocking {
            val day = today.plusDays(4).toEpochDay()
            repository.captureScheduled("Talk to Dana", day, 9 * 60)
            val id = taskNamed("Talk to Dana").id
            repository.demote(id)
            assertEquals(TaskBucket.LATER, task(id).bucket)
            assertNull(task(id).scheduledOnEpochDay)
            repository.moveToInbox(id)
            assertEquals(TaskBucket.INBOX, task(id).bucket)
            assertNull(task(id).scheduledOnEpochDay)
            assertTrue(snapshot().frames.none { it.taskId == id })
        }

    private fun epochAt(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

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
