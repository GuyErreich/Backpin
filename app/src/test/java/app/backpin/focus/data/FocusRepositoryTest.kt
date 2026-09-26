package app.backpin.focus.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.MAX_OPEN_TODAY
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var repository: FocusRepository
    private val clock = MutableClock(1_000_000L)

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
                preferencesStore = PreferencesDataStore(context, "prefs-${UUID.randomUUID()}"),
                clock = clock,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun todayHoldsAtMostThreeOpenTasks() =
        runBlocking {
            val ids = (1..4).map { capture("Task $it") }
            assertTrue(repository.promote(ids[0]) is PromoteOutcome.Promoted)
            assertTrue(repository.promote(ids[1]) is PromoteOutcome.Promoted)
            assertTrue(repository.promote(ids[2]) is PromoteOutcome.Promoted)
            val blocked = repository.promote(ids[3])
            assertTrue(blocked is PromoteOutcome.NeedsSwap)
            assertEquals(3, blocked.let { (it as PromoteOutcome.NeedsSwap).todayTasks.size })
            assertEquals(MAX_OPEN_TODAY, openToday().size)
            assertEquals(TaskBucket.INBOX, task(ids[3]).bucket)
        }

    @Test
    fun doneTodayTaskDoesNotCountTowardCapacity() =
        runBlocking {
            val ids = (1..3).map { capture("Task $it").also { id -> repository.promote(id) } }
            val extra = capture("Fourth")
            assertTrue(repository.promote(extra) is PromoteOutcome.NeedsSwap)
            repository.start(ids[0])
            repository.completeActive()
            assertTrue(repository.promote(extra) is PromoteOutcome.Promoted)
            assertEquals(3, openToday().size)
            assertTrue(openToday().none { it.id == ids[0] })
        }

    @Test
    fun swapChangesBothBucketsInOneTransaction() =
        runBlocking {
            val today = (1..3).map { capture("Today $it").also { id -> repository.promote(id) } }
            val incoming = capture("Incoming")
            repository.swapIntoToday(incoming, today[1])
            assertEquals(setOf(today[0], today[2], incoming), openToday().map { it.id }.toSet())
            assertEquals(TaskBucket.LATER, task(today[1]).bucket)
            assertEquals(3, openToday().size)
        }

    @Test
    fun rejectedSwapRollsBackBothWrites() =
        runBlocking {
            val today = (1..3).map { capture("Stay $it").also { id -> repository.promote(id) } }
            val incoming = capture("Incoming")
            val otherInbox = capture("Not today")
            val failed =
                runCatching {
                    repository.swapIntoToday(incoming, otherInbox)
                }
            assertTrue(failed.isFailure)
            assertEquals(TaskBucket.INBOX, task(incoming).bucket)
            assertEquals(TaskBucket.INBOX, task(otherInbox).bucket)
            assertEquals(today.toSet(), openToday().map { it.id }.toSet())
        }

    @Test
    fun atMostOneFrameIsActive() =
        runBlocking {
            val first = capture("First").also { repository.promote(it) }
            val second = capture("Second").also { repository.promote(it) }
            repository.start(first)
            repository.start(second)
            val snapshot = snapshot()
            assertEquals(1, snapshot.frames.count { it.state == FrameState.ACTIVE })
            val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
            assertEquals(second, active.taskId)
            assertNull(active.parentFrameId)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == first }.state)
        }

    @Test
    fun interruptKeepsASingleActiveChild() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            val parentFrame = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
            val child = repository.interrupt("Message John")
            val snapshot = snapshot()
            assertEquals(1, snapshot.frames.count { it.state == FrameState.ACTIVE })
            val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
            assertEquals(child.id, active.taskId)
            assertEquals(parentFrame, active.parentFrameId)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.id == parentFrame }.state)
            assertEquals(TaskBucket.INBOX, task(child.id).bucket)
        }

    @Test
    fun resumeRefusesASecondActiveFrame() =
        runBlocking {
            val original = capture("Original").also { repository.promote(it) }
            val other = capture("Other").also { repository.promote(it) }
            repository.start(original)
            repository.interrupt("Ping")
            repository.completeActive()
            val waiting = snapshot().frames.single { it.state == FrameState.AWAITING_RESUME }
            repository.start(other)
            val failed = runCatching { repository.resume(waiting.id) }
            assertTrue(failed.isFailure)
            val snapshot = snapshot()
            assertEquals(1, snapshot.frames.count { it.state == FrameState.ACTIVE })
            assertEquals(other, snapshot.frames.single { it.state == FrameState.ACTIVE }.taskId)
            assertEquals(FrameState.AWAITING_RESUME, snapshot.frames.single { it.id == waiting.id }.state)
        }

    @Test
    fun captureDoesNotChangeTheActiveFrame() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            val before = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
            repository.capture("Buy milk")
            val after = snapshot()
            assertEquals(before, after.frames.single { it.state == FrameState.ACTIVE }.id)
            assertEquals(original, after.frames.single { it.state == FrameState.ACTIVE }.taskId)
            assertTrue(after.tasks.any { it.title == "Buy milk" && it.bucket == TaskBucket.INBOX })
        }

    @Test
    fun blankCaptureChangesNothing() =
        runBlocking {
            repository.capture("   ")
            repository.capture("")
            assertTrue(snapshot().tasks.isEmpty())
            assertTrue(snapshot().frames.isEmpty())
        }

    @Test
    fun blankInterruptIsUntitledAndPushesAFrame() =
        runBlocking {
            val original = capture("Deep work").also { repository.promote(it) }
            repository.start(original)
            val created = repository.interrupt("  ")
            assertEquals(UNTITLED_INTERRUPTION, created.title)
            assertEquals(TaskBucket.INBOX, task(created.id).bucket)
            val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
            assertEquals(created.id, active.taskId)
            assertEquals(FrameState.PAUSED, snapshot().frames.single { it.taskId == original }.state)
        }

    @Test
    fun nestedInterruptCompletesBackToTheOriginalCue() =
        runBlocking {
            val original = capture("Review CI failure")
            repository.promote(original)
            repository.updateReturnCue(original, "Next: reproduce CI failure")
            repository.start(original)
            repository.interrupt("Message John")
            repository.updateReturnCue(snapshot().frames.single { it.state == FrameState.ACTIVE }.taskId, "Next: send the recap")
            repository.interrupt("Talk to Sarah")

            repository.completeActive()
            var snapshot = snapshot()
            assertEquals(0, snapshot.frames.count { it.state == FrameState.ACTIVE })
            val john = snapshot.frames.single { it.state == FrameState.AWAITING_RESUME }
            assertEquals("Message John", snapshot.tasks.single { it.id == john.taskId }.title)
            assertEquals("Next: send the recap", snapshot.tasks.single { it.id == john.taskId }.returnCue)
            assertTrue(snapshot.tasks.single { it.title == "Talk to Sarah" }.status == TaskStatus.DONE)

            repository.completeActive()
            assertNull(snapshot().frames.singleOrNull { it.state == FrameState.ACTIVE })
            repository.resume(john.id)
            repository.completeActive()
            snapshot = snapshot()
            val waiting = snapshot.frames.single { it.state == FrameState.AWAITING_RESUME }
            val originalTask = snapshot.tasks.single { it.id == waiting.taskId }
            assertEquals("Review CI failure", originalTask.title)
            assertEquals("Next: reproduce CI failure", originalTask.returnCue)
            assertEquals(1, snapshot.frames.count { it.state == FrameState.AWAITING_RESUME })

            val accumulated = waiting.accumulatedMillis
            clock.advance(60_000)
            repository.resume(waiting.id)
            snapshot = snapshot()
            val resumed = snapshot.frames.single { it.state == FrameState.ACTIVE }
            assertEquals(original, resumed.taskId)
            assertEquals(accumulated, resumed.accumulatedMillis)
            assertEquals("Next: reproduce CI failure", snapshot.tasks.single { it.id == original }.returnCue)
            assertEquals(1, snapshot.frames.count { it.state == FrameState.ACTIVE })
        }

    @Test
    fun switchPausesWithoutCreatingAnInterruption() =
        runBlocking {
            val first = capture("First").also { repository.promote(it) }
            val second = capture("Second").also { repository.promote(it) }
            repository.updateReturnCue(first, "Next: stay here")
            repository.start(first)
            repository.start(second)
            val snapshot = snapshot()
            val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
            assertEquals(second, active.taskId)
            assertNull(active.parentFrameId)
            val paused = snapshot.frames.single { it.taskId == first }
            assertEquals(FrameState.PAUSED, paused.state)
            assertEquals("Next: stay here", snapshot.tasks.single { it.id == first }.returnCue)
        }

    @Test
    fun restoreReturnsAnOpenInboxTaskWithoutChangingTheActiveFrame() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            repository.capture("Buy milk")
            val milk = snapshot().tasks.single { it.title == "Buy milk" }.id
            val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
            val frameCount = snapshot().frames.size
            repository.completeTask(milk)
            assertEquals(TaskStatus.DONE, task(milk).status)
            repository.restore(milk)
            val restored = task(milk)
            val after = snapshot()
            assertEquals(TaskStatus.OPEN, restored.status)
            assertEquals(TaskBucket.INBOX, restored.bucket)
            assertNull(restored.completedAtEpochMillis)
            assertEquals(active.id, after.frames.single { it.state == FrameState.ACTIVE }.id)
            assertEquals(original, after.frames.single { it.state == FrameState.ACTIVE }.taskId)
            assertEquals(frameCount, after.frames.size)
            assertTrue(after.frames.none { it.taskId == milk })
            assertTrue(openToday().none { it.id == milk })
            assertEquals(1, after.frames.count { it.state == FrameState.ACTIVE })
        }

    @Test
    fun undoDoneRestoresBucketAndActiveFrame() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            repository.interrupt("Message John")
            val token = repository.completeActive()!!
            repository.undo(token)
            val snapshot = snapshot()
            val john = snapshot.tasks.single { it.title == "Message John" }
            assertEquals(TaskStatus.OPEN, john.status)
            assertNull(john.completedAtEpochMillis)
            assertEquals(FrameState.ACTIVE, snapshot.frames.single { it.taskId == john.id }.state)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == original }.state)
            assertTrue(snapshot.frames.none { it.state == FrameState.AWAITING_RESUME })
        }

    @Test
    fun undoLaterRestoresBucketAndFrame() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            val token = repository.demote(original)
            assertEquals(TaskBucket.LATER, task(original).bucket)
            assertEquals(FrameState.PAUSED, snapshot().frames.single { it.taskId == original }.state)
            repository.undo(token)
            assertEquals(TaskBucket.TODAY, task(original).bucket)
            assertEquals(FrameState.ACTIVE, snapshot().frames.single { it.taskId == original }.state)
        }

    @Test
    fun demoteActiveInterruptionSurfacesTheParent() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            val child = repository.interrupt("Message John")
            repository.demote(child.id)
            val snapshot = snapshot()
            assertEquals(TaskBucket.LATER, task(child.id).bucket)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == child.id }.state)
            assertEquals(FrameState.AWAITING_RESUME, snapshot.frames.single { it.taskId == original }.state)
            assertEquals(0, snapshot.frames.count { it.state == FrameState.ACTIVE })
        }

    @Test
    fun laterOnRecoveryParksTheFrameWithoutAnotherLayer() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            repository.interrupt("Message John")
            repository.interrupt("Talk to Sarah")
            repository.completeActive()
            val johnId = snapshot().frames.single { it.state == FrameState.AWAITING_RESUME }.taskId
            repository.demote(johnId)
            val snapshot = snapshot()
            assertEquals(TaskBucket.LATER, task(johnId).bucket)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == johnId }.state)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == original }.state)
            assertTrue(snapshot.frames.none { it.state == FrameState.AWAITING_RESUME })
        }

    @Test
    fun reorderTodayRewritesSortAndUndoRestoresIt() =
        runBlocking {
            val first = capture("First").also { repository.promote(it) }
            clock.advance(10)
            val second = capture("Second").also { repository.promote(it) }
            clock.advance(10)
            val third = capture("Third").also { repository.promote(it) }
            assertEquals(listOf(first, second, third), openToday().map { it.id })
            val token = repository.reorderToday(listOf(third, first, second))
            assertEquals(listOf(third, first, second), openToday().map { it.id })
            repository.undo(token!!)
            assertEquals(listOf(first, second, third), openToday().map { it.id })
        }

    @Test
    fun completeTaskLeavesTheActiveFrameAlone() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            repository.capture("Buy milk")
            val milk = snapshot().tasks.single { it.title == "Buy milk" }.id
            val active = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
            val token = repository.completeTask(milk)
            assertEquals(TaskStatus.DONE, task(milk).status)
            assertEquals(active, snapshot().frames.single { it.state == FrameState.ACTIVE }.id)
            repository.undo(token!!)
            assertEquals(TaskStatus.OPEN, task(milk).status)
            assertEquals(TaskBucket.INBOX, task(milk).bucket)
        }

    @Test
    fun completeTaskRefusesAnAncestorOfTheActiveFrame() =
        runBlocking {
            val original = capture("Review CI failure").also { repository.promote(it) }
            repository.start(original)
            repository.interrupt("Message John")
            assertNull(repository.completeTask(original))
            assertEquals(TaskStatus.OPEN, task(original).status)
            assertEquals(1, snapshot().frames.count { it.state == FrameState.ACTIVE })
        }

    @Test
    fun stackSurvivesReopeningTheDatabase() =
        runBlocking {
            val name = "reopen-${UUID.randomUUID()}"
            val prefs = PreferencesDataStore(context, "prefs-$name")
            val firstDb =
                Room
                    .databaseBuilder(context, FocusStackDatabase::class.java, name)
                    .allowMainThreadQueries()
                    .addMigrations(*FocusStackDatabase.migrations)
                    .build()
            val firstRepo = FocusRepositoryImpl(firstDb, prefs, clock)
            val original = firstRepo.captureReturning("Review CI failure")
            firstRepo.promote(original)
            firstRepo.updateReturnCue(original, "Next: reproduce CI failure")
            firstRepo.start(original)
            firstRepo.interrupt("Message John")
            firstRepo.interrupt("Talk to Sarah")
            firstRepo.completeActive()
            val waitingId = firstRepo.observeSnapshot().first().frames.single { it.state == FrameState.AWAITING_RESUME }.id
            firstDb.close()

            val reopened =
                Room
                    .databaseBuilder(context, FocusStackDatabase::class.java, name)
                    .allowMainThreadQueries()
                    .addMigrations(*FocusStackDatabase.migrations)
                    .build()
            val repo = FocusRepositoryImpl(reopened, prefs, clock)
            val snapshot = repo.observeSnapshot().first()
            reopened.close()
            context.deleteDatabase(name)
            assertEquals(waitingId, snapshot.frames.single { it.state == FrameState.AWAITING_RESUME }.id)
            assertEquals(
                "Message John",
                snapshot.tasks.single { it.id == snapshot.frames.single { frame -> frame.id == waitingId }.taskId }.title,
            )
            assertEquals(0, snapshot.frames.count { it.state == FrameState.ACTIVE })
            assertEquals(TaskStatus.DONE, snapshot.tasks.single { it.title == "Talk to Sarah" }.status)
            val originalTask = snapshot.tasks.single { it.id == original }
            assertEquals("Next: reproduce CI failure", originalTask.returnCue)
            assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == original }.state)
        }

    private suspend fun capture(title: String): String {
        repository.capture(title)
        return snapshot().tasks.single { it.title == title }.id
    }

    private suspend fun snapshot(): FocusSnapshot = repository.observeSnapshot().first()

    private suspend fun openToday() = snapshot().tasks.filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }

    private suspend fun task(id: String) = snapshot().tasks.single { it.id == id }
}

private suspend fun FocusRepository.captureReturning(title: String): String {
    capture(title)
    return observeSnapshot().first().tasks.single { it.title == title }.id
}

private class MutableClock(
    private var now: Long,
) : AppClock {
    override fun nowEpochMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }
}
