// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.DEFAULT_TODAY_LIMIT
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.awaitState
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for #66: closing a nested frame may Inbox only the task that just
 * lost its parent link. [requireInvariants] checks and never writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChildFrameCloseOverflowTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher = UnconfinedTestDispatcher())

    @get:Rule
    val focus = InMemoryFocusRule()

    @Test
    fun nestEndPathsParkOnlyTheStillOpenChild() = runBlocking {
        val rows =
            listOf(
                NestEndRow(
                    name = "detachInterruption when Today is full",
                    path = NestEndPath.DetachInterruption,
                    todayTitles = fullTodayTitles,
                    expectedChildBucket = TaskBucket.INBOX,
                    expectedChildStatus = TaskStatus.OPEN,
                    expectNeedsSwap = true,
                ),
                NestEndRow(
                    name = "detachInterruption when Today has room",
                    path = NestEndPath.DetachInterruption,
                    todayTitles = roomTodayTitles,
                    expectedChildBucket = TaskBucket.TODAY,
                    expectedChildStatus = TaskStatus.OPEN,
                    expectNeedsSwap = false,
                ),
                NestEndRow(
                    name = "completeActive when Today is full",
                    path = NestEndPath.CompleteActive,
                    todayTitles = fullTodayTitles,
                    expectedChildBucket = TaskBucket.TODAY,
                    expectedChildStatus = TaskStatus.DONE,
                    expectNeedsSwap = false,
                ),
                NestEndRow(
                    name = "completeActive when Today has room",
                    path = NestEndPath.CompleteActive,
                    todayTitles = roomTodayTitles,
                    expectedChildBucket = TaskBucket.TODAY,
                    expectedChildStatus = TaskStatus.DONE,
                    expectNeedsSwap = false,
                ),
                NestEndRow(
                    name = "completeTask when Today is full",
                    path = NestEndPath.CompleteTask,
                    todayTitles = fullTodayTitles,
                    expectedChildBucket = TaskBucket.TODAY,
                    expectedChildStatus = TaskStatus.DONE,
                    expectNeedsSwap = false,
                ),
                NestEndRow(
                    name = "completeTask when Today has room",
                    path = NestEndPath.CompleteTask,
                    todayTitles = roomTodayTitles,
                    expectedChildBucket = TaskBucket.TODAY,
                    expectedChildStatus = TaskStatus.DONE,
                    expectNeedsSwap = false,
                ),
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val nest = nestChild(fixture.repository, row.todayTitles)
                val outcome = closeNest(fixture.repository, nest.childId, row.path)
                val snapshot =
                    fixture.repository.observeSnapshot().awaitState { state ->
                        nestEnded(state, nest, row)
                    }
                assertEquals(row.name, row.expectNeedsSwap, outcome is PromoteOutcome.NeedsSwap)
                if (outcome is PromoteOutcome.NeedsSwap) {
                    assertEquals(row.name, nest.childId, outcome.incomingTaskId)
                    assertEquals(row.name, false, outcome.canInterrupt)
                }
                val child = snapshot.tasks.single { it.id == nest.childId }
                assertEquals(row.name, row.expectedChildBucket, child.bucket)
                assertEquals(row.name, row.expectedChildStatus, child.status)
                assertEquals(row.name, nest.others, otherPlaces(snapshot, nest.childId))
                assertInvariantsHold(row.name, snapshot)
            }
        }
    }

    @Test
    fun pauseKeepsTheNestAndStartBringsTheChildBackFirst() = runBlocking {
        val nest = nestChild(focus.repository, fullTodayTitles)
        focus.repository.pauseActive()
        val paused =
            focus.repository.observeSnapshot().awaitState { state ->
                val child = state.frames.singleOrNull { it.taskId == nest.childId }
                val parent = state.frames.singleOrNull { it.id == nest.parentFrameId }
                child?.state == FrameState.PAUSED &&
                    child.parentFrameId == nest.parentFrameId &&
                    parent?.state == FrameState.PAUSED &&
                    state.frames.none { it.state == FrameState.ACTIVE }
            }
        assertEquals(nest.others, otherPlaces(paused, nest.childId))
        assertInvariantsHold("paused nest", paused)

        val started = focus.repository.start(nest.childId)
        assertEquals(PromoteOutcome.Promoted, started)
        val resumed =
            focus.repository.observeSnapshot().awaitState { state ->
                val child = state.frames.singleOrNull { it.state == FrameState.ACTIVE }
                val parent = state.frames.singleOrNull { it.id == nest.parentFrameId }
                child?.taskId == nest.childId &&
                    child.parentFrameId == nest.parentFrameId &&
                    parent?.state == FrameState.PAUSED
            }
        assertEquals(nest.others, otherPlaces(resumed, nest.childId))
        assertInvariantsHold("child started first", resumed)

        val refused = focus.repository.start(nest.parentId)
        assertTrue(refused is PromoteOutcome.Rejected)
        assertEquals(
            "Task still has an open interruption",
            (refused as PromoteOutcome.Rejected).reason,
        )
        val afterRefuse =
            focus.repository.observeSnapshot().awaitState { state ->
                state.frames.singleOrNull { it.state == FrameState.ACTIVE }?.taskId == nest.childId
            }
        assertEquals(nest.others, otherPlaces(afterRefuse, nest.childId))
        val child = afterRefuse.frames.single { it.state == FrameState.ACTIVE }
        assertEquals(nest.childId, child.taskId)
        assertEquals(nest.parentFrameId, child.parentFrameId)
        assertEquals(
            FrameState.PAUSED,
            afterRefuse.frames.single { it.id == nest.parentFrameId }.state,
        )
        assertInvariantsHold("parent start refused", afterRefuse)
    }

    @Test
    fun requireInvariantsThrowsOnOverflowAndLeavesAValidSnapshotAlone() = runBlocking {
        val today = focus.repository.seedFullToday(fullTodayTitles)
        val valid =
            focus.repository.observeSnapshot().awaitState { state ->
                state.tasks.count { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN } ==
                    today.size
            }
        val validPlaces = allPlaces(valid)
        focus.repository.releaseDueScheduled()
        val stillValid =
            focus.repository.observeSnapshot().awaitState { state ->
                allPlaces(state) == validPlaces
            }
        assertEquals(validPlaces, allPlaces(stillValid))
        assertInvariantsHold("valid check", stillValid)

        focus.repository.setTodayLimit(4)
        val extra = focus.repository.seedInbox("Overflow extra").single()
        assertTrue(focus.repository.promote(extra.id) is PromoteOutcome.Promoted)
        val four =
            focus.repository.observeSnapshot().awaitState { state ->
                state.tasks.count { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN } == 4
            }
        assertEquals(4, four.tasks.count { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN })
        focus.fixture.preferences.setTodayLimit(3)
        focus.repository.preferences.awaitState { it.todayLimit == 3 }
        val invalid =
            focus.repository.observeSnapshot().awaitState { state ->
                state.tasks.count { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN } == 4
            }
        val invalidPlaces = allPlaces(invalid)
        try {
            focus.repository.releaseDueScheduled()
            fail("requireInvariants should throw on an over-cap Today")
        } catch (error: IllegalStateException) {
            assertTrue(
                error.message ?: "",
                error.message?.contains("Today cannot hold more than") == true,
            )
        }
        val afterThrow =
            focus.repository.observeSnapshot().awaitState { state ->
                allPlaces(state) == invalidPlaces
            }
        assertEquals(invalidPlaces, allPlaces(afterThrow))
    }

    private suspend fun nestChild(
        repository: FocusRepository,
        todayTitles: List<String>,
    ): NestedChild {
        val today = repository.seedFullToday(todayTitles)
        val parent = today.first()
        repository.start(parent.id)
        val child = repository.seedInbox("Ping the vendor").single()
        repository.interruptWith(child.id)
        val snapshot =
            repository.observeSnapshot().awaitState { state ->
                val active = state.frames.singleOrNull { it.state == FrameState.ACTIVE }
                active?.taskId == child.id && active.parentFrameId != null
            }
        val childFrame = snapshot.frames.single { it.state == FrameState.ACTIVE }
        return NestedChild(
            parentId = parent.id,
            childId = child.id,
            parentFrameId = childFrame.parentFrameId!!,
            others = otherPlaces(snapshot, child.id),
        )
    }

    private suspend fun closeNest(
        repository: FocusRepository,
        childId: String,
        path: NestEndPath,
    ): PromoteOutcome? = when (path) {
        NestEndPath.DetachInterruption ->
            repository.detachInterruption(childId, DetachDestination.Today)
        NestEndPath.CompleteActive -> {
            repository.completeActive()
            null
        }
        NestEndPath.CompleteTask -> {
            repository.pauseActive()
            repository.completeTask(childId)
            null
        }
    }

    private fun nestEnded(
        state: FocusSnapshot,
        nest: NestedChild,
        row: NestEndRow,
    ): Boolean {
        val child = state.tasks.singleOrNull { it.id == nest.childId } ?: return false
        if (child.bucket != row.expectedChildBucket) return false
        if (child.status != row.expectedChildStatus) return false
        val parent = state.frames.singleOrNull { it.id == nest.parentFrameId } ?: return false
        return parent.state == FrameState.AWAITING_RESUME &&
            state.frames.none { it.taskId == nest.childId && it.state != FrameState.CLOSED }
    }

    private fun assertInvariantsHold(
        name: String,
        snapshot: FocusSnapshot,
    ) {
        val nested =
            snapshot.frames
                .filter { it.state != FrameState.CLOSED && it.parentFrameId != null }
                .map { it.taskId }
                .toSet()
        val counted =
            snapshot.tasks.count { task ->
                task.bucket == TaskBucket.TODAY &&
                    task.status == TaskStatus.OPEN &&
                    task.id !in nested
            }
        assertTrue(
            "$name: counted open Today is $counted",
            counted <= DEFAULT_TODAY_LIMIT,
        )
        assertTrue(
            "$name: more than one active frame",
            snapshot.frames.count { it.state == FrameState.ACTIVE } <= 1,
        )
    }

    private fun otherPlaces(
        snapshot: FocusSnapshot,
        childId: String,
    ): Map<String, TaskPlace> = snapshot.tasks
        .filter { it.id != childId }
        .associate { task ->
            task.id to TaskPlace(task.bucket, task.status, task.sortOrder)
        }

    private fun allPlaces(snapshot: FocusSnapshot): SnapshotPlaces {
        val tasks =
            snapshot.tasks.associate { task ->
                task.id to TaskPlace(task.bucket, task.status, task.sortOrder)
            }
        val frames =
            snapshot.frames.associate { frame ->
                frame.id to FramePlace(frame.taskId, frame.parentFrameId, frame.state)
            }
        return SnapshotPlaces(tasks, frames)
    }

    private data class NestEndRow(
        val name: String,
        val path: NestEndPath,
        val todayTitles: List<String>,
        val expectedChildBucket: TaskBucket,
        val expectedChildStatus: TaskStatus,
        val expectNeedsSwap: Boolean,
    )

    private enum class NestEndPath {
        DetachInterruption,
        CompleteActive,
        CompleteTask,
    }

    private data class NestedChild(
        val parentId: String,
        val childId: String,
        val parentFrameId: String,
        val others: Map<String, TaskPlace>,
    )

    private data class TaskPlace(
        val bucket: TaskBucket,
        val status: TaskStatus,
        val sortOrder: Long,
    )

    private data class FramePlace(
        val taskId: String,
        val parentFrameId: String?,
        val state: FrameState,
    )

    private data class SnapshotPlaces(
        val tasks: Map<String, TaskPlace>,
        val frames: Map<String, FramePlace>,
    )

    private companion object {
        val fullTodayTitles: List<String> = listOf("Write the brief", "Call Dana", "File the form")
        val roomTodayTitles: List<String> = listOf("Write the brief", "Call Dana")
    }
}
