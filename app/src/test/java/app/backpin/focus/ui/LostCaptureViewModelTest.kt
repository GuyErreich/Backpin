// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.awaitState
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LostCaptureViewModelTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule(dispatcher = UnconfinedTestDispatcher())

    @get:Rule(order = 1)
    val focus = InMemoryFocusRule()

    @Test
    fun replaceMovesTheSavedCaptureThroughSwapIntoTodayAndUndoReturnsIt() = runBlocking {
        val today = focus.repository.seedFullToday()
        val outgoing = today[1]
        focus.fixture.withViewModel { viewModel ->
            viewModel.placeOnToday("Call the school")
            val waiting =
                viewModel.uiState.awaitState {
                    it.swapIncoming?.title == "Call the school" && it.swapChoices.size == 3
                }
            val incomingId = waiting.swapIncoming!!.id
            assertEquals(
                TaskBucket.INBOX,
                snapshot().tasks.single { it.id == incomingId }.bucket,
            )
            viewModel.confirmReplace(outgoing.id)
            val placed =
                viewModel.uiState.awaitState { state ->
                    state.swapIncoming == null &&
                        state.todaySlots.any { it.task?.id == incomingId } &&
                        state.undo?.target is UndoTarget.Tasks
                }
            val afterReplace = snapshot()
            assertEquals(1, afterReplace.tasks.count { it.title == "Call the school" })
            assertEquals(TaskBucket.TODAY, afterReplace.tasks.single { it.id == incomingId }.bucket)
            assertEquals(TaskBucket.LATER, afterReplace.tasks.single { it.id == outgoing.id }.bucket)
            assertTrue(afterReplace.frames.none { it.taskId == incomingId })
            viewModel.undo(placed.undo!!.id)
            viewModel.uiState.awaitState { state ->
                state.undo == null &&
                    state.inboxTasks.any { it.id == incomingId } &&
                    state.todaySlots.any { it.task?.id == outgoing.id }
            }
            val restored = snapshot()
            assertEquals(TaskBucket.INBOX, restored.tasks.single { it.id == incomingId }.bucket)
            assertEquals(TaskBucket.TODAY, restored.tasks.single { it.id == outgoing.id }.bucket)
            assertEquals(1, restored.tasks.count { it.title == "Call the school" })
        }
    }

    @Test
    fun interruptReusesTheSavedCaptureThroughInterruptWith() = runBlocking {
        val today = focus.repository.seedFullToday()
        focus.repository.start(today.first().id)
        val parentFrame =
            snapshot().frames.single { it.state == FrameState.ACTIVE }.id
        focus.fixture.withViewModel { viewModel ->
            viewModel.placeOnToday("Call the school")
            val waiting =
                viewModel.uiState.awaitState {
                    it.swapIncoming?.title == "Call the school"
                }
            val incomingId = waiting.swapIncoming!!.id
            viewModel.confirmInterrupt()
            viewModel.uiState.awaitState { state ->
                val now = state.now
                state.swapIncoming == null &&
                    now != null &&
                    now.taskId == incomingId &&
                    now.isInterruption
            }
            val after = snapshot()
            assertEquals(1, after.tasks.count { it.title == "Call the school" })
            assertEquals(incomingId, after.frames.single { it.state == FrameState.ACTIVE }.taskId)
            assertEquals(parentFrame, after.frames.single { it.state == FrameState.ACTIVE }.parentFrameId)
            assertEquals(TaskBucket.TODAY, after.tasks.single { it.id == incomingId }.bucket)
            assertEquals(TaskBucket.LATER, after.tasks.single { it.id == today.first().id }.bucket)
            assertEquals(FrameState.PAUSED, after.frames.single { it.id == parentFrame }.state)
        }
    }

    @Test
    fun keepInInboxUndoDiscardsAFreshCapture() = runBlocking {
        focus.repository.seedFullToday()
        focus.fixture.withViewModel { viewModel ->
            viewModel.placeOnToday("Call the school")
            val waiting =
                viewModel.uiState.awaitState { it.swapIncoming?.title == "Call the school" }
            val incomingId = waiting.swapIncoming!!.id
            viewModel.keepInInbox()
            val kept =
                viewModel.uiState.awaitState { state ->
                    state.swapIncoming == null &&
                        state.undo?.target is UndoTarget.DiscardCapture &&
                        state.inboxTasks.any { it.id == incomingId }
                }
            val undo = checkNotNull(kept.undo)
            val target = undo.target as UndoTarget.DiscardCapture
            assertEquals(incomingId, target.taskId)
            viewModel.undo(undo.id)
            viewModel.uiState.awaitState { state ->
                state.undo == null && state.inboxTasks.none { it.id == incomingId }
            }
            assertTrue(snapshot().tasks.none { it.id == incomingId })
        }
    }

    @Test
    fun keepingAnExistingTaskDoesNotOfferDiscardUndo() = runBlocking {
        focus.repository.seedFullToday()
        val existing = focus.repository.seedInbox("Already waiting").single()
        focus.fixture.withViewModel { viewModel ->
            viewModel.promote(existing.id)
            viewModel.uiState.awaitState { it.swapIncoming?.id == existing.id }
            viewModel.keepInInbox()
            viewModel.uiState.awaitState { it.swapIncoming == null }
            assertNull(viewModel.uiState.value.undo)
            assertEquals(TaskBucket.INBOX, snapshot().tasks.single { it.id == existing.id }.bucket)
            assertEquals(TaskStatus.OPEN, snapshot().tasks.single { it.id == existing.id }.status)
        }
    }

    @Test
    fun noSwapPathLosesAFreshCapture() = runBlocking {
        val rows =
            listOf(
                SwapPath("Keep in Inbox") { viewModel, _ -> viewModel.keepInInbox() },
                SwapPath("cancelSwap") { viewModel, _ -> viewModel.cancelSwap() },
                SwapPath("Replace") { viewModel, outgoingId -> viewModel.confirmReplace(outgoingId) },
                SwapPath("Interrupt") { viewModel, _ -> viewModel.confirmInterrupt() },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val today = fixture.repository.seedFullToday()
                if (row.name == "Interrupt") {
                    fixture.repository.start(today.first().id)
                }
                fixture.withViewModel { viewModel ->
                    viewModel.placeOnToday("Call the school")
                    val waiting =
                        viewModel.uiState.awaitState { it.swapIncoming?.title == "Call the school" }
                    val incomingId = waiting.swapIncoming!!.id
                    row.leave(viewModel, today[1].id)
                    viewModel.uiState.awaitState { it.swapIncoming == null }
                    val after = fixture.repository.observeSnapshot().first()
                    val kept = after.tasks.single { it.id == incomingId }
                    assertEquals(row.name, "Call the school", kept.title)
                    assertEquals(row.name, TaskStatus.OPEN, kept.status)
                    assertEquals(row.name, 1, after.tasks.count { it.title == "Call the school" })
                }
            }
        }
    }

    private suspend fun snapshot() = focus.repository.observeSnapshot().first()

    private data class SwapPath(
        val name: String,
        val leave: (FocusStackViewModel, String) -> Unit,
    )
}
