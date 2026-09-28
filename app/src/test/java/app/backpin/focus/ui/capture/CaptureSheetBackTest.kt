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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    fun scheduleOffersTomorrowInSevenDaysAndPickADate() {
        show()
        typeTitle("Call Dana")
        tap("Schedule")
        compose.onNodeWithText("When should it come back?").assertExists()
        compose.onNodeWithText("Tomorrow").assertExists()
        compose.onNodeWithText("In 7 days").assertExists()
        compose.onNodeWithText("Pick a date").assertExists()
        compose.onNodeWithText("In a couple of days").assertDoesNotExist()
        compose.onNodeWithText("Next week").assertDoesNotExist()
        compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Add a time").assertDoesNotExist()
        compose.onNodeWithText("Change date").assertDoesNotExist()
        assertEquals(listOf("Tomorrow", "In 7 days", "Pick a date"), labelsTopToBottom("Tomorrow", "In 7 days", "Pick a date"))
    }

    @Test
    fun captureButtonsAreTallAndSpacedApart() {
        show()
        val destinations = listOf("Save to Inbox", "Today", "Start", "Interrupt now", "Later", "Schedule")
        assertTallAndSpaced(destinations)
        typeTitle("Call Dana")
        tap("Schedule")
        assertTallAndSpaced(listOf("Tomorrow", "In 7 days", "Pick a date"))
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
        compose.onNodeWithText("Where does this go?").assertExists()
        assertArrow(BACK)
        goBack()
        compose.onNodeWithText("Where does this go?").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertExists()
        assertArrow(CLOSE)

        tap("Talk to my wife")
        tap("Schedule")
        compose.onNodeWithText("When should it come back?").assertExists()
        assertArrow(BACK)
        goBack()
        compose.onNodeWithText("Where does this go?").assertExists()
        goBack()
        compose.onNodeWithText("Talk to …").assertExists()

        typeTitle("Call Dana")
        tap("Schedule")
        compose.onNodeWithText("When should it come back?").assertExists()
        goBack()
        compose.onNodeWithText("When should it come back?").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Call Dana")).assertExists()
        assertEquals(0, dismissed)

        assertArrow(CLOSE)
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

    private fun assertTallAndSpaced(labels: List<String>) {
        val bounds = labels.map { compose.onNodeWithText(it).getBoundsInRoot() }.sortedBy { it.top }
        bounds.forEachIndexed { index, rect ->
            assertTrue("${labels[index]} is ${rect.bottom - rect.top} tall", rect.bottom - rect.top >= 56.dp - Slack)
        }
        bounds.zipWithNext { above, below ->
            assertTrue("Gap is ${below.top - above.bottom}", below.top - above.bottom >= 12.dp - Slack)
        }
    }

    private fun labelsTopToBottom(vararg labels: String): List<String> =
        labels.sortedBy { compose.onNodeWithText(it).getBoundsInRoot().top.value }

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
                    frameActive = true,
                    onDismiss = { dismissed += 1 },
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                    onPlaceLater = {},
                    onSchedule = { _, _, _ -> },
                    recognizer = SilentRecognizer,
                )
            }
        }
    }

    private fun typeTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextInput(title)
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
