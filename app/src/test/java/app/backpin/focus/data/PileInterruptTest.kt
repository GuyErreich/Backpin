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
import app.backpin.focus.testing.seedInbox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
class PileInterruptTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 9, 27)
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
                preferencesStore = PreferencesDataStore(context, "pile-${UUID.randomUUID()}"),
                clock =
                object : AppClock {
                    override fun nowEpochMillis(): Long = today.atStartOfDay(zone).toInstant().toEpochMilli()
                },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun interruptFromInboxNestsAndLeavesTodayFull() = runBlocking {
        val parent = startFullToday()
        repository.capture("Buy milk")
        val child = taskNamed("Buy milk").id
        nestAndFinish(parent, child)
        assertTrue(snapshot().tasks.none { it.bucket == TaskBucket.INBOX })
    }

    @Test
    fun interruptFromLaterNestsAndLeavesTodayFull() = runBlocking {
        val parent = startFullToday()
        val child = repository.seedInbox("Call the vendor").single().id
        repository.demote(child)
        nestAndFinish(parent, child)
        assertTrue(snapshot().tasks.none { it.bucket == TaskBucket.LATER })
    }

    @Test
    fun interruptFromScheduledNestsWithoutDroppingOntoToday() = runBlocking {
        val parent = startFullToday()
        val due = today.toEpochDay()
        repository.captureScheduled("Talk to Dana", due)
        val child = taskNamed("Talk to Dana").id
        val before = openToday().map { it.id }.toSet()
        repository.interruptFromPile(child)
        val nested = task(child)
        assertEquals(TaskBucket.INTERRUPTION, nested.bucket)
        assertNull(nested.scheduledOnEpochDay)
        assertEquals(before, openToday().map { it.id }.toSet())
        repository.releaseDueScheduled()
        assertEquals(TaskBucket.INTERRUPTION, task(child).bucket)
        assertEquals(before, openToday().map { it.id }.toSet())
        assertTrue(snapshot().tasks.none { it.bucket == TaskBucket.LATER })
        val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
        assertEquals(child, active.taskId)
        assertEquals(parent.frameId, active.parentFrameId)
        assertEquals("Next: the failing test", task(parent.taskId).returnCue)
    }

    @Test
    fun interruptIsRefusedWhenNothingIsActive() = runBlocking {
        repository.capture("Buy milk")
        val milk = taskNamed("Buy milk").id
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.interruptFromPile(milk) }
        }
        assertEquals(TaskBucket.INBOX, task(milk).bucket)
        assertTrue(snapshot().frames.isEmpty())
    }

    @Test
    fun interruptIsRefusedWhenTheOnlyFrameIsWaiting() = runBlocking {
        val parent = startFullToday()
        repository.interrupt("Ping")
        repository.completeActive()
        assertEquals(FrameState.AWAITING_RESUME, snapshot().frames.single { it.id == parent.frameId }.state)
        repository.capture("Buy milk")
        val milk = taskNamed("Buy milk").id
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.interruptFromPile(milk) }
        }
        assertEquals(TaskBucket.INBOX, task(milk).bucket)
        assertNull(snapshot().frames.firstOrNull { it.state == FrameState.ACTIVE })
        assertEquals(parent.todayIds, openToday().map { it.id }.toSet())
    }

    private suspend fun nestAndFinish(
        parent: StartedToday,
        child: String,
    ) {
        val before = openToday().map { it.id }.toSet()
        repository.interruptFromPile(child)
        val snapshot = snapshot()
        val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
        assertEquals(child, active.taskId)
        assertEquals(parent.frameId, active.parentFrameId)
        assertEquals(FrameState.PAUSED, snapshot.frames.single { it.id == parent.frameId }.state)
        assertEquals(TaskBucket.INTERRUPTION, task(child).bucket)
        assertEquals(TaskBucket.TODAY, task(parent.taskId).bucket)
        assertEquals("Next: the failing test", task(parent.taskId).returnCue)
        assertEquals(before, openToday().map { it.id }.toSet())
        assertTrue(snapshot.tasks.none { it.bucket == TaskBucket.LATER })
        repository.completeActive()
        assertEquals(FrameState.AWAITING_RESUME, snapshot().frames.single { it.id == parent.frameId }.state)
        assertEquals(TaskStatus.DONE, task(child).status)
        assertEquals("Next: the failing test", task(parent.taskId).returnCue)
        assertEquals(before, openToday().map { it.id }.toSet())
    }

    private suspend fun startFullToday(): StartedToday {
        val ids =
            (1..3).map { index ->
                val title = "Today $index"
                repository.capture(title)
                val id = snapshot().tasks.single { it.title == title }.id
                repository.promote(id)
                id
            }
        repository.updateReturnCue(ids[0], "Next: the failing test")
        repository.start(ids[0])
        val frameId = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
        return StartedToday(taskId = ids[0], frameId = frameId, todayIds = ids.toSet())
    }

    private suspend fun snapshot(): FocusSnapshot = repository.observeSnapshot().first()

    private suspend fun openToday() = snapshot().tasks.filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }

    private suspend fun task(id: String) = snapshot().tasks.single { it.id == id }

    private suspend fun taskNamed(title: String) = snapshot().tasks.single { it.title == title }

    private data class StartedToday(
        val taskId: String,
        val frameId: String,
        val todayIds: Set<String>,
    )
}
