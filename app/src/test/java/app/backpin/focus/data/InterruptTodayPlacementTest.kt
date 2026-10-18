// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.TestFocusFixture
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InterruptTodayPlacementTest {
    @get:Rule
    val focus = InMemoryFocusRule()

    private val repository get() = focus.repository

    @Test
    fun interruptKeepsTheParentOnTodayPausedUnderTheChild() = runBlocking {
        val rows =
            listOf(
                InterruptRow("Inbox interruptWith") { repo ->
                    val incoming = repo.seedInbox("Call the school").single()
                    repo.interruptWith(incoming.id)
                    incoming.id
                },
                InterruptRow("fresh-capture Interrupt") { repo ->
                    val blocked = repo.captureIntoToday("Call the school") as PromoteOutcome.NeedsSwap
                    repo.interruptWith(blocked.incomingTaskId)
                    blocked.incomingTaskId
                },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val repo = fixture.repository
                val today = repo.seedFullToday()
                val parent = today.first()
                repo.start(parent.id)
                val parentFrame = snapshot(repo).frames.single { it.state == FrameState.ACTIVE }
                val incomingId = row.interrupt(repo)
                val after = snapshot(repo)
                val active = after.frames.single { it.state == FrameState.ACTIVE }
                assertEquals(row.name, incomingId, active.taskId)
                assertEquals(row.name, parentFrame.id, active.parentFrameId)
                assertEquals(row.name, FrameState.PAUSED, after.frames.single { it.id == parentFrame.id }.state)
                assertEquals(row.name, TaskBucket.TODAY, task(repo, parent.id).bucket)
                assertEquals(row.name, TaskBucket.TODAY, task(repo, incomingId).bucket)
                assertTrue(row.name, after.tasks.none { it.id == parent.id && it.bucket != TaskBucket.TODAY })
                assertEquals(
                    row.name,
                    (today.map { it.id } + incomingId).toSet(),
                    openToday(repo).map { it.id }.toSet(),
                )
                repo.completeActive()
                val recovered = snapshot(repo)
                val waiting = recovered.frames.single { it.state == FrameState.AWAITING_RESUME }
                assertEquals(row.name, parent.id, waiting.taskId)
                assertEquals(row.name, TaskBucket.TODAY, task(repo, parent.id).bucket)
                assertEquals(row.name, TaskStatus.DONE, task(repo, incomingId).status)
                assertTrue(row.name, recovered.tasks.none { it.id == parent.id && it.bucket != TaskBucket.TODAY })
            }
        }
    }

    @Test
    fun openChildOfTheCurrentFrameDoesNotCountTowardTheTodayCap() = runBlocking {
        val today = repository.seedFullToday()
        repository.start(today.first().id)
        val incoming = repository.seedInbox("Nested work").single()
        repository.interruptWith(incoming.id)
        assertEquals(4, openToday(repository).size)
        assertEquals(
            (today.map { it.id } + incoming.id).toSet(),
            openToday(repository).map { it.id }.toSet(),
        )
        val blocked = repository.captureIntoToday("One more")
        assertTrue(blocked is PromoteOutcome.NeedsSwap)
        assertEquals(TaskBucket.INBOX, taskNamed(repository, "One more").bucket)
        assertEquals(4, openToday(repository).size)
    }

    @Test
    fun unNestedOpenChildGoesToInboxWithNeedsSwapWhenTodayIsFull() = runBlocking {
        val today = repository.seedFullToday()
        val parent = today.first()
        repository.start(parent.id)
        val incoming = repository.seedInbox("Nested work").single()
        repository.interruptWith(incoming.id)
        val outcome = repository.detachInterruption(incoming.id, DetachDestination.Today)
        val swap = outcome as PromoteOutcome.NeedsSwap
        assertEquals(false, swap.canInterrupt)
        assertEquals(incoming.id, swap.incomingTaskId)
        assertEquals(today.map { it.id }.toSet(), swap.todayTasks.map { it.id }.toSet())
        assertEquals(TaskBucket.INBOX, task(repository, incoming.id).bucket)
        assertEquals(3, openToday(repository).size)
        assertEquals(today.map { it.id }.toSet(), openToday(repository).map { it.id }.toSet())
        assertEquals(
            FrameState.AWAITING_RESUME,
            snapshot(repository).frames.single { it.taskId == parent.id }.state,
        )
        assertTrue(snapshot(repository).frames.none { it.state == FrameState.ACTIVE })
    }

    @Test
    fun detachLaterPlacesTheTaskInInbox() = runBlocking {
        val rows =
            listOf(
                DetachRow("nested Today child") { fixture ->
                    val repo = fixture.repository
                    val parent = repo.seedFullToday(listOf("Write the brief")).single()
                    repo.start(parent.id)
                    val incoming = repo.seedInbox("Call the school").single()
                    repo.interruptWith(incoming.id)
                    incoming.id to parent.id
                },
                DetachRow("arrived countdown") { fixture ->
                    val repo = fixture.repository
                    val parent = repo.seedFullToday(listOf("Write the brief")).single()
                    repo.start(parent.id)
                    repo.captureCountdown("Dentist", fixture.clock.localEpochDay())
                    val arrived = repo.releaseDueCountdowns() ?: error("Countdown did not arrive")
                    arrived.id to parent.id
                },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val repo = fixture.repository
                val (childId, parentId) = row.nest(fixture)
                val outcome = repo.detachInterruption(childId, DetachDestination.Later)
                assertEquals(row.name, PromoteOutcome.Promoted, outcome)
                assertEquals(row.name, TaskBucket.INBOX, task(repo, childId).bucket)
                assertTrue(row.name, snapshot(repo).tasks.none { it.id == childId && it.bucket == TaskBucket.LATER })
                assertEquals(row.name, TaskBucket.TODAY, task(repo, parentId).bucket)
                assertEquals(
                    row.name,
                    FrameState.AWAITING_RESUME,
                    snapshot(repo).frames.single { it.taskId == parentId }.state,
                )
                assertTrue(row.name, snapshot(repo).frames.none { it.state == FrameState.ACTIVE })
            }
        }
    }

    private suspend fun snapshot(repo: FocusRepository): FocusSnapshot = repo.observeSnapshot().first()

    private suspend fun openToday(repo: FocusRepository) = snapshot(repo).tasks.filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }

    private suspend fun task(
        repo: FocusRepository,
        id: String,
    ) = snapshot(repo).tasks.single { it.id == id }

    private suspend fun taskNamed(
        repo: FocusRepository,
        title: String,
    ) = snapshot(repo).tasks.single { it.title == title }

    private data class InterruptRow(
        val name: String,
        val interrupt: suspend (FocusRepository) -> String,
    )

    private data class DetachRow(
        val name: String,
        val nest: suspend (TestFocusFixture) -> Pair<String, String>,
    )
}
