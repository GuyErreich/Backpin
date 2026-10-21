// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CaptureSheetBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dismissed = 0

    @Test
    fun theArrowStepsBackFromEachStepAndClosesOnTheFirst() {
        walkEveryStep(goBack = ::tapArrow)
    }

    @Test
    fun systemBackDoesWhatTheArrowDoes() {
        walkEveryStep(goBack = ::pressSystemBack)
    }

    @Test
    fun captureButtonsMatchSaveHeightAndTheWhenRow() {
        show()
        compose.onNodeWithText("OR, WHEN?").assertExists()
        compose.onNodeWithText("One line. Choose where it goes…").assertDoesNotExist()
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithText("Schedule").assertDoesNotExist()
        compose.onNodeWithText("Interrupt now").assertDoesNotExist()
        compose.onNodeWithText("Repeat").assertDoesNotExist()
        val save = compose.onNodeWithText("Save to Inbox").getBoundsInRoot()
        assertTrue("Save to Inbox is ${save.bottom - save.top} tall", save.bottom - save.top >= 56.dp - Slack)
        val today = compose.onNodeWithText("Today").getBoundsInRoot()
        val startNow = compose.onNodeWithText("Start now").getBoundsInRoot()
        val bringBack = compose.onNodeWithText("Bring back…").getBoundsInRoot()
        listOf("Today" to today, "Start now" to startNow, "Bring back…" to bringBack).forEach { (label, rect) ->
            val height = rect.bottom - rect.top
            assertTrue("$label is $height tall", height >= 56.dp - Slack)
        }
        assertTrue("Today and Start now share a row", kotlin.math.abs((today.top - startNow.top).value) <= 0.5f)
        val underRow = bringBack.top - startNow.bottom
        assertTrue("Bring back sits 12dp under the row, gap is $underRow", kotlin.math.abs((underRow - ButtonGap).value) <= 1f)
        val todayWidth = today.right - today.left
        val startWidth = startNow.right - startNow.left
        val saveWidth = save.right - save.left
        val bringWidth = bringBack.right - bringBack.left
        assertTrue("Today and Start now match", kotlin.math.abs((todayWidth - startWidth).value) <= 1f)
        assertTrue("Bring back matches Save", kotlin.math.abs((bringWidth - saveWidth).value) <= 1f)
        assertTrue("Bring back shares Save's left edge", kotlin.math.abs((bringBack.left - save.left).value) <= 1f)
        assertTrue("Today shares Save's left edge", kotlin.math.abs((today.left - save.left).value) <= 1f)
        assertTrue("Start now shares Save's right edge", kotlin.math.abs((startNow.right - save.right).value) <= 1f)
    }

    private fun walkEveryStep(goBack: () -> Unit) {
        show()
        assertArrow(CLOSE)

        tap("Talk to …")
        compose.onNodeWithText("name").assertExists()
        assertArrow(BACK)
        goBack()
        compose.onNodeWithText("name").assertDoesNotExist()
        compose.onNodeWithText("Talk to …").assertExists()
        assertArrow(CLOSE)

        tap("Talk to my wife")
        compose.onNode(hasSetTextAction() and hasText("Talk to my wife")).assertExists()
        compose.onNodeWithText("Where does this go?").assertDoesNotExist()
        assertArrow(CLOSE)
        assertEquals(0, dismissed)

        goBack()
        assertEquals(1, dismissed)
    }

    private fun assertArrow(label: String) {
        compose.onNodeWithContentDescription(label).assertIsEnabled()
        val other = if (label == BACK) CLOSE else BACK
        compose.onNodeWithContentDescription(other).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close capture").assertDoesNotExist()
        compose.onNodeWithText("Back").assertDoesNotExist()
        val arrow = compose.onNodeWithContentDescription(label).getBoundsInRoot()
        val heading = compose.onNodeWithText("Get it out of your head.").getBoundsInRoot()
        assertTrue("The arrow sits left of the heading", arrow.right <= heading.left)
        assertTrue("The arrow has a 48dp target", arrow.bottom - arrow.top >= 48.dp - Slack)
    }

    private fun tapArrow() {
        val label = if (compose.onAllNodes(hasContentDescription(BACK)).fetchSemanticsNodes().isNotEmpty()) BACK else CLOSE
        compose.onNodeWithContentDescription(label).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun pressSystemBack() {
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun show() {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = listOf("Talk to my wife", "Talk to <name>"),
                    onDismiss = { dismissed += 1 },
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                    onBringBack = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).assertIsEnabled().performTouchInput { click() }
        compose.waitForIdle()
    }

    private companion object {
        const val BACK = "Back"
        const val CLOSE = "Close"
        val Slack: Dp = 0.5.dp
    }
}

private object SilentRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
