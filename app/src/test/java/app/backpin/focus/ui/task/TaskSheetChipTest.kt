// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.TaskSheetRole
import app.backpin.focus.ui.TaskSheetUi
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TaskSheetChipTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todayOpenWithoutFocusShowsStartDoneLaterEdit() {
        val calls = mutableListOf<String>()
        show(
            role = TaskSheetRole.TODAY_OPEN,
            canInterrupt = false,
            calls = calls,
        )
        assertEquals(listOf("Start", "Done", "Later", "Edit"), sheetActions())
        tap("Start")
        tap("Done")
        tap("Later")
        tap("Edit")
        assertEquals(listOf("start", "done", "later", "edit"), calls.toList())
    }

    @Test
    fun todayOpenInFocusAddsInterruptBetweenLaterAndEdit() {
        val calls = mutableListOf<String>()
        show(
            role = TaskSheetRole.TODAY_OPEN,
            canInterrupt = true,
            calls = calls,
        )
        assertEquals(listOf("Start", "Done", "Later", "Interrupt", "Edit"), sheetActions())
        tap("Interrupt")
        assertEquals(listOf("interrupt"), calls.toList())
    }

    @Test
    fun waitingShowsResumeLaterEditAndNeverInterrupt() {
        val calls = mutableListOf<String>()
        show(
            role = TaskSheetRole.TODAY_WAITING,
            canInterrupt = true,
            calls = calls,
        )
        assertEquals(listOf("Resume", "Later", "Edit"), sheetActions())
        tap("Resume")
        assertEquals(listOf("resume"), calls.toList())
    }

    @Test
    fun pileWithoutFocusShowsTodayStartEdit() {
        show(
            role = TaskSheetRole.PILE,
            canInterrupt = false,
            calls = mutableListOf(),
        )
        assertEquals(listOf("Today", "Start", "Edit"), sheetActions())
    }

    @Test
    fun pileInFocusAddsInterruptLikeTodayOpen() {
        val calls = mutableListOf<String>()
        show(
            role = TaskSheetRole.PILE,
            canInterrupt = true,
            calls = calls,
        )
        assertEquals(listOf("Today", "Start", "Interrupt", "Edit"), sheetActions())
        tap("Today")
        tap("Start")
        tap("Interrupt")
        tap("Edit")
        assertEquals(listOf("today", "start", "interrupt", "edit"), calls.toList())
    }

    private fun show(
        role: TaskSheetRole,
        canInterrupt: Boolean,
        calls: MutableList<String>,
    ) {
        compose.setContent {
            FocusStackTheme {
                TaskSheet(
                    state =
                    TaskSheetUi(
                        taskId = "task-1",
                        title = "Park me",
                        role = role,
                        canInterrupt = canInterrupt,
                        frameId = "frame-1",
                    ),
                    onDismiss = { calls += "dismiss" },
                    onStart = { calls += "start" },
                    onDone = { calls += "done" },
                    onEdit = { calls += "edit" },
                    onInterrupt = { calls += "interrupt" },
                    onToday = { calls += "today" },
                    onResume = { calls += "resume" },
                    onBringBack = { calls += "bringBack" },
                )
            }
        }
        compose.onNodeWithText("This task.").assertExists()
        compose.onNodeWithText("Park me").assertExists()
    }

    private fun sheetActions(): List<String> {
        data class Hit(
            val label: String,
            val left: androidx.compose.ui.unit.Dp,
            val top: androidx.compose.ui.unit.Dp,
        )

        val hits = mutableListOf<Hit>()
        CHIP_LABELS.forEach { label ->
            val nodes = compose.onAllNodesWithText(label)
            val found = nodes.fetchSemanticsNodes()
            found.indices.forEach { index ->
                val bounds = nodes[index].getBoundsInRoot()
                if (bounds.bottom - bounds.top >= SHEET_CHIP_MIN) {
                    hits += Hit(label, bounds.left, bounds.top)
                }
            }
        }
        return hits.sortedWith(compareBy({ it.top }, { it.left })).map { it.label }
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        val bounds = node.getBoundsInRoot()
        assertTrue("$label is a sheet chip", bounds.bottom - bounds.top >= SHEET_CHIP_MIN)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private companion object {
        val SHEET_CHIP_MIN = 50.dp
        val CHIP_LABELS =
            listOf(
                "Start",
                "Done",
                "Later",
                "Edit",
                "Interrupt",
                "Resume",
                "Today",
                "Done now",
                "Skip today",
                "Tomorrow",
                "Pick a day",
                "Pick a date",
            )
    }
}
