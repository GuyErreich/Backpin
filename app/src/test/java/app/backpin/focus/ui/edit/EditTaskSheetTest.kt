package app.backpin.focus.ui.edit

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
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
class EditTaskSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<String>()
    private var dismissed = 0

    @Test
    fun saveWritesATitleAndBackLeavesItAlone() {
        show()
        compose.onNode(hasSetTextAction() and hasText("Hold this")).assertExists()
        compose.onNodeWithContentDescription("Speak the title").assertExists()
        compose.onNodeWithText("Save title").assertDoesNotExist()
        replaceTitle("Talk to Sarah")
        tap("Save")
        assertEquals(listOf("Talk to Sarah"), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun aBlankTitleStaysOnTheSheet() {
        show()
        replaceTitle("   ")
        tap("Save")
        assertTrue(saved.isEmpty())
        assertEquals(0, dismissed)
        compose.onNodeWithText("Save").assertExists()
        compose.onNode(hasSetTextAction()).assertExists()
    }

    @Test
    fun theArrowClosesWithoutWriting() {
        show()
        replaceTitle("Talk to Sarah")
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(saved.isEmpty())
        assertEquals(1, dismissed)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun systemBackClosesWithoutWriting() {
        show()
        replaceTitle("Talk to Sarah")
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(saved.isEmpty())
        assertEquals(1, dismissed)
    }

    @Test
    fun saveIsASheetButtonAndTheArrowSitsLeft() {
        show()
        val save = compose.onNodeWithText("Save").getBoundsInRoot()
        assertTrue("Save is ${save.bottom - save.top} tall", save.bottom - save.top >= 56.dp - 0.5.dp)
        val arrow = compose.onNodeWithContentDescription("Close").getBoundsInRoot()
        val heading = compose.onNodeWithText("Edit").getBoundsInRoot()
        assertTrue("The arrow sits left of the heading", arrow.right <= heading.left)
        assertTrue("The arrow has a 48dp target", arrow.bottom - arrow.top >= 48.dp - 0.5.dp)
    }

    private fun show() {
        compose.setContent {
            FocusStackTheme {
                EditTaskSheet(
                    initialTitle = "Hold this",
                    onDismiss = { dismissed += 1 },
                    onSave = { title ->
                        saved += title
                        dismissed += 1
                    },
                    recognizer = SilentRecognizer,
                )
            }
        }
    }

    private fun replaceTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(title)
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).assertIsEnabled().performTouchInput { click() }
        compose.waitForIdle()
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
}
