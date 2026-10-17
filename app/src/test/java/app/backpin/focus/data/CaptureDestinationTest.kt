// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureDestinationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "dest-${UUID.randomUUID()}"),
                clock = object : AppClock {
                    override fun nowEpochMillis(): Long = 1_700_000_000_000L
                },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun inboxLeavesTheActiveTaskAlone() =
        runBlocking {
            val current = fillToday(1).single()
            repository.start(current)
            val before = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
            repository.capture("Buy milk")
            val after = snapshot()
            assertEquals(before, after.frames.single { it.state == FrameState.ACTIVE }.id)
            assertEquals(TaskBucket.INBOX, taskNamed("Buy milk").bucket)
            assertTrue(after.frames.none { it.taskId == taskNamed("Buy milk").id })
        }

    @Test
    fun todayPlacesWithoutStarting() =
        runBlocking {
            val outcome = repository.captureIntoToday("Write the notes")
            assertTrue(outcome is PromoteOutcome.Promoted)
            val task = taskNamed("Write the notes")
            assertEquals(TaskBucket.TODAY, task.bucket)
            assertTrue(snapshot().frames.none { it.taskId == task.id })
            assertNull(snapshot().frames.firstOrNull { it.state == FrameState.ACTIVE })
        }

    @Test
    fun todayOnAFullListWritesNothingAndCanInterruptWhenActive() =
        runBlocking {
            val today = fillToday(3)
            repository.start(today[0])
            val before = snapshot()
            val blocked = repository.captureIntoToday("Call the school")
            assertTrue(blocked is PromoteOutcome.NeedsSwap)
            assertTrue((blocked as PromoteOutcome.NeedsSwap).canInterrupt)
            assertEquals(before.tasks.map { it.id }.toSet(), snapshot().tasks.map { it.id }.toSet())
            assertEquals(today.toSet(), openToday().map { it.id }.toSet())
        }

    @Test
    fun todayOnAFullIdleListDoesNotOfferInterrupt() =
        runBlocking {
            fillToday(3)
            val blocked = repository.captureIntoToday("Call the school")
            assertTrue(blocked is PromoteOutcome.NeedsSwap)
            assertEquals(false, (blocked as PromoteOutcome.NeedsSwap).canInterrupt)
            assertTrue(snapshot().tasks.none { it.title == "Call the school" })
        }

    @Test
    fun startWithNothingActiveJustStarts() =
        runBlocking {
            val outcome = repository.captureAndStart("Write the notes")
            assertTrue(outcome is PromoteOutcome.Promoted)
            val task = taskNamed("Write the notes")
            val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
            assertEquals(task.id, active.taskId)
            assertNull(active.parentFrameId)
            assertEquals(TaskBucket.TODAY, task.bucket)
        }

    @Test
    fun startWhileActiveIsASwitch() =
        runBlocking {
            val current = fillToday(1).single()
            repository.start(current)
            val outcome = repository.captureAndStart("Call Dana")
            assertTrue(outcome is PromoteOutcome.Promoted)
            val snapshot = snapshot()
            val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
            val started = taskNamed("Call Dana")
            assertEquals(started.id, active.taskId)
            assertNull(active.parentFrameId)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == current }.state)
            assertEquals(TaskBucket.TODAY, taskNamed("Call Dana").bucket)
            assertEquals(TaskBucket.TODAY, task(current).bucket)
        }

    @Test
    fun startOnAFullTodayWritesNothing() =
        runBlocking {
            val today = fillToday(3)
            repository.start(today[0])
            val before = snapshot().tasks.size
            val blocked = repository.captureAndStart("Call Dana")
            assertTrue(blocked is PromoteOutcome.NeedsSwap)
            assertTrue((blocked as PromoteOutcome.NeedsSwap).canInterrupt)
            assertEquals(before, snapshot().tasks.size)
            assertEquals(today[0], snapshot().frames.single { it.state == FrameState.ACTIVE }.taskId)
        }

    @Test
    fun laterParksWithoutStarting() =
        runBlocking {
            repository.captureLater("Call the vendor")
            val task = taskNamed("Call the vendor")
            assertEquals(TaskBucket.LATER, task.bucket)
            assertTrue(snapshot().frames.none { it.taskId == task.id })
        }

    @Test
    fun scheduleStaysOutsideInboxTodayAndLater() =
        runBlocking {
            val day = LocalDate.of(2026, 10, 6).toEpochDay()
            repository.captureScheduled("Talk to Dana", day)
            val task = taskNamed("Talk to Dana")
            assertEquals(TaskBucket.SCHEDULED, task.bucket)
            assertEquals(day, task.scheduledOnEpochDay)
            assertTrue(snapshot().frames.none { it.taskId == task.id })
        }

    @Test
    fun replaceOnAFullTodayDoesNotStartTheNewTask() =
        runBlocking {
            val today = fillToday(3)
            repository.start(today[0])
            val token = repository.captureReplacing("Call the school", today[1])
            val created = taskNamed("Call the school")
            assertEquals(TaskBucket.TODAY, created.bucket)
            assertEquals(TaskBucket.LATER, task(today[1]).bucket)
            assertTrue(snapshot().frames.none { it.taskId == created.id })
            assertEquals(today[0], snapshot().frames.single { it.state == FrameState.ACTIVE }.taskId)
            repository.undo(token)
            assertTrue(snapshot().tasks.none { it.title == "Call the school" })
            assertEquals(TaskBucket.TODAY, task(today[1]).bucket)
        }

    @Test
    fun interruptOnAFullTodayNestsAndParksTheCurrentTask() =
        runBlocking {
            val today = fillToday(3)
            repository.start(today[0])
            val parent = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
            repository.interruptWithNew("Call the school")
            val created = taskNamed("Call the school")
            val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
            assertEquals(created.id, active.taskId)
            assertEquals(parent, active.parentFrameId)
            assertEquals(TaskBucket.TODAY, created.bucket)
            assertEquals(TaskBucket.LATER, task(today[0]).bucket)
            assertEquals(FrameState.PAUSED, snapshot().frames.single { it.id == parent }.state)
        }

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
        snapshot().tasks.filter { it.bucket == TaskBucket.TODAY && it.status.name == "OPEN" }

    private suspend fun task(id: String) = snapshot().tasks.single { it.id == id }

    private suspend fun taskNamed(title: String) = snapshot().tasks.single { it.title == title }
}
