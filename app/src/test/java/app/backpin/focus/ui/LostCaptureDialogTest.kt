// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.runOffTestThread
import app.backpin.focus.testing.TestFocusFixture
import app.backpin.focus.testing.defaultTodayTitles
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LostCaptureDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullTodayDialogShowsTodayIsFullWithKeepInInboxAsTheDefault() {
        compose.setContent {
            FocusStackTheme {
                TodayFullSwapDialog(
                    incoming = TaskUi("incoming", "Call the school", null),
                    swapChoices =
                    defaultTodayTitles().mapIndexed { index, title ->
                        TaskUi("today-$index", title, null)
                    },
                    canInterrupt = false,
                    onKeepInInbox = {},
                    onInterrupt = {},
                    onReplace = {},
                )
            }
        }
        awaitText("Today is full")
        awaitText("Swap one out, or keep \"Call the school\" in Inbox. Nothing gets lost.")
        awaitText("Swap one out")
        awaitText("Keep in Inbox")
        val swap = compose.onNodeWithText("Swap one out").getBoundsInRoot()
        val keep = compose.onNodeWithText("Keep in Inbox").getBoundsInRoot()
        assertTrue("Keep in Inbox is the default action on the right", keep.left > swap.left)
        compose.onNodeWithText("Keep in Inbox").assertHasClickAction()
    }

    @Test
    fun dialogDismissPathsCallKeepInInbox() {
        var generation by mutableIntStateOf(0)
        var kept = 0
        compose.setContent {
            key(generation) {
                FocusStackTheme {
                    TodayFullSwapDialog(
                        incoming = TaskUi("incoming", "Call the school", null),
                        swapChoices =
                        defaultTodayTitles().mapIndexed { index, title ->
                            TaskUi("today-$index", title, null)
                        },
                        canInterrupt = false,
                        onKeepInInbox = {
                            kept += 1
                            generation += 1
                        },
                        onInterrupt = {},
                        onReplace = {},
                    )
                }
            }
        }
        val rows =
            listOf(
                LeaveRow("Keep in Inbox") { tap("Keep in Inbox") },
                LeaveRow("tap outside") { tapOutside() },
                LeaveRow("back") { pressBack() },
            )
        for (row in rows) {
            val before = kept
            awaitText("Today is full")
            row.leave()
            await { kept == before + 1 }
        }
        assertEquals(rows.size, kept)
    }

    @Test
    fun cancelTapOutsideAndBackKeepTheCaptureAndShowKeptInInbox() {
        var generation by mutableIntStateOf(0)
        var hosted by mutableStateOf<FocusStackViewModel?>(null)
        compose.setContent {
            key(generation) {
                hosted?.let { viewModel ->
                    FocusStackTheme { FocusApp(viewModel) }
                }
            }
        }
        val rows =
            listOf(
                LeaveRow("Keep in Inbox") { tap("Keep in Inbox") },
                LeaveRow("tap outside") { tapOutside() },
                LeaveRow("back") { pressBack() },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                fixture.withViewModel { viewModel ->
                    seedToday(fixture)
                    hosted = viewModel
                    generation += 1
                    compose.waitForIdle()
                    await { viewModel.uiState.value.ready }
                    viewModel.placeOnToday("Call the school")
                    awaitText("Today is full")
                    val incomingId =
                        awaitUi(viewModel) { it.swapIncoming?.title == "Call the school" }
                            .swapIncoming!!
                            .id
                    row.leave()
                    awaitUi(viewModel) { it.swapIncoming == null }
                    awaitText("Kept in Inbox")
                    awaitText("Undo")
                    val kept = runOffTestThread {
                        fixture.repository.observeSnapshot().first().tasks.single { it.id == incomingId }
                    }
                    assertEquals(row.name, TaskBucket.INBOX, kept.bucket)
                    assertEquals(row.name, "Call the school", kept.title)
                    val undo = viewModel.uiState.value.undo
                    assertTrue(row.name, undo?.target is UndoTarget.DiscardCapture)
                    assertEquals(row.name, incomingId, (undo?.target as UndoTarget.DiscardCapture).taskId)
                    hosted = null
                    generation += 1
                    compose.waitForIdle()
                }
            }
        }
    }

    @Test
    fun keptInInboxUndoRemovesTheCapture() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                seedToday(fixture)
                showApp(viewModel)
                await { viewModel.uiState.value.ready }
                viewModel.placeOnToday("Call the school")
                awaitText("Today is full")
                val incomingId =
                    awaitUi(viewModel) { it.swapIncoming?.title == "Call the school" }
                        .swapIncoming!!
                        .id
                tap("Keep in Inbox")
                awaitText("Kept in Inbox")
                tap("Undo")
                awaitUi(viewModel) { state ->
                    state.undo == null && state.inboxTasks.none { it.id == incomingId }
                }
                awaitGone("Kept in Inbox")
                val after = runOffTestThread {
                    fixture.repository.observeSnapshot().first()
                }
                assertTrue(after.tasks.none { it.id == incomingId })
                assertTrue(after.tasks.none { it.title == "Call the school" })
            }
        }
    }

    @Test
    fun replaceUndoReturnsTheCaptureToInbox() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                val today = seedToday(fixture)
                val outgoing = today[1]
                showApp(viewModel)
                await { viewModel.uiState.value.ready }
                viewModel.placeOnToday("Call the school")
                awaitText("Today is full")
                val incomingId =
                    awaitUi(viewModel) { it.swapIncoming?.title == "Call the school" }
                        .swapIncoming!!
                        .id
                tap("Swap one out")
                tap("Replace “${outgoing.title}”")
                awaitUi(viewModel) { state ->
                    state.swapIncoming == null &&
                        state.todaySlots.any { it.task?.id == incomingId } &&
                        state.undo?.target is UndoTarget.Tasks
                }
                awaitText("Swapped into Today")
                val placed = runOffTestThread {
                    fixture.repository.observeSnapshot().first().tasks.single { it.id == incomingId }
                }
                assertEquals(TaskBucket.TODAY, placed.bucket)
                tap("Undo")
                awaitUi(viewModel) { state ->
                    state.undo == null && state.inboxTasks.any { it.id == incomingId }
                }
                val restored = runOffTestThread {
                    fixture.repository.observeSnapshot().first()
                }
                assertEquals(TaskBucket.INBOX, restored.tasks.single { it.id == incomingId }.bucket)
                assertEquals(TaskBucket.TODAY, restored.tasks.single { it.id == outgoing.id }.bucket)
            }
        }
    }

    @Test
    fun interruptUsesTheSavedCapture() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                val today = seedToday(fixture)
                runOffTestThread { fixture.repository.start(today.first().id) }
                showApp(viewModel)
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.now?.taskId == today.first().id
                }
                viewModel.placeOnToday("Call the school")
                awaitText("Today is full")
                awaitText("Interrupt current task")
                val incomingId =
                    awaitUi(viewModel) { it.swapIncoming?.title == "Call the school" }
                        .swapIncoming!!
                        .id
                tap("Interrupt current task")
                awaitUi(viewModel) { state ->
                    val now = state.now
                    state.swapIncoming == null &&
                        now != null &&
                        now.taskId == incomingId &&
                        now.isInterruption
                }
                val after = runOffTestThread {
                    fixture.repository.observeSnapshot().first()
                }
                assertEquals(1, after.tasks.count { it.title == "Call the school" })
                assertEquals(incomingId, after.frames.single { it.state == FrameState.ACTIVE }.taskId)
                assertEquals(TaskBucket.LATER, after.tasks.single { it.id == today.first().id }.bucket)
            }
        }
    }

    private fun seedToday(fixture: TestFocusFixture) = runOffTestThread {
        fixture.repository.seedFullToday()
    }

    private fun showApp(viewModel: FocusStackViewModel) {
        compose.setContent {
            FocusStackTheme {
                FocusApp(viewModel)
            }
        }
    }

    private fun tap(label: String) {
        awaitText(label)
        val clickable = compose.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes()
        if (clickable.isNotEmpty()) {
            val index = clickable.indices.minBy { clickable[it].boundsInRoot.width }
            compose.onAllNodes(hasText(label) and hasClickAction())[index]
                .performTouchInput { click(center) }
        } else {
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performTouchInput { click(center) }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Compose DialogWrapper treats outside as DOWN then UP; cancel() is a no-op. */
    private fun tapOutside() {
        compose.waitForIdle()
        val dialog = shownDialog()
        val now = SystemClock.uptimeMillis()

        fun outside(action: Int): MotionEvent = MotionEvent.obtain(now, now, action, -10f, -10f, 0)
        compose.runOnUiThread {
            val down = outside(MotionEvent.ACTION_DOWN)
            val up = outside(MotionEvent.ACTION_UP)
            try {
                check(dialog.onTouchEvent(down)) { "outside ACTION_DOWN was ignored" }
                check(dialog.onTouchEvent(up)) { "outside ACTION_UP was ignored" }
            } finally {
                down.recycle()
                up.recycle()
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun pressBack() {
        compose.waitForIdle()
        val dialog = shownDialog()
        compose.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun shownDialog(): ComponentDialog {
        val shown =
            ShadowDialog.getShownDialogs().filter { it.isShowing }.map { it as ComponentDialog }
        check(shown.isNotEmpty()) { "No showing dialog" }
        return shown.last()
    }

    private fun awaitText(text: String) {
        await {
            nodes(text, unmerged = false).isNotEmpty() || nodes(text, unmerged = true).isNotEmpty()
        }
        val unmerged = nodes(text, unmerged = true).isNotEmpty() && nodes(text, unmerged = false).isEmpty()
        compose.onNodeWithText(text, useUnmergedTree = unmerged).assertExists()
    }

    private fun awaitGone(text: String) {
        await {
            nodes(text, unmerged = false).isEmpty() && nodes(text, unmerged = true).isEmpty()
        }
    }

    private fun nodes(
        text: String,
        unmerged: Boolean,
    ) = compose.onAllNodesWithText(text, useUnmergedTree = unmerged).fetchSemanticsNodes()

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitUi(
        viewModel: FocusStackViewModel,
        predicate: (FocusUiState) -> Boolean,
    ): FocusUiState {
        await { predicate(viewModel.uiState.value) }
        return viewModel.uiState.value
    }

    private data class LeaveRow(
        val name: String,
        val leave: () -> Unit,
    )
}
