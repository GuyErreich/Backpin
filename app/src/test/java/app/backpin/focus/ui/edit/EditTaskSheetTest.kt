package app.backpin.focus.ui.edit

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.ui.capture.CaptureEdit
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditTaskSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<EditCommit>()
    private var dismissed = 0

    @Test
    fun doneWritesTheTitleAndKeepsThePlace() {
        show()
        compose.onNode(hasSetTextAction() and hasText("Hold this")).assertExists()
        compose.onNodeWithContentDescription("Speak the title").assertExists()
        compose.onNodeWithText("Save").assertDoesNotExist()
        compose.onNodeWithText("Save title").assertDoesNotExist()
        replaceTitle("Talk to Sarah")
        tap("Done")
        assertEquals(listOf(EditCommit("Talk to Sarah", CapturePlace.INBOX, null)), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun aBlankTitleKeepsTheOldOne() {
        show()
        replaceTitle("   ")
        tap("Done")
        assertEquals(listOf(EditCommit("Hold this", CapturePlace.INBOX, null)), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun theArrowKeepsTheEdit() {
        show()
        replaceTitle("Talk to Sarah")
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(EditCommit("Talk to Sarah", CapturePlace.INBOX, null)), saved.toList())
        assertEquals(1, dismissed)
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun systemBackKeepsTheEdit() {
        show()
        replaceTitle("Talk to Sarah")
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        compose.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf(EditCommit("Talk to Sarah", CapturePlace.INBOX, null)), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun laterThenDoneMovesTheTask() {
        show()
        tap("Later")
        tap("Done")
        assertEquals(listOf(EditCommit("Hold this", CapturePlace.LATER, null)), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun scheduleTomorrowWritesTheDay() {
        show()
        tap("Schedule")
        tap("Tomorrow")
        val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
        assertEquals(listOf(EditCommit("Hold this", CapturePlace.SCHEDULE, tomorrow)), saved.toList())
        assertEquals(1, dismissed)
    }

    @Test
    fun doneIsASheetButtonAndTheArrowSitsLeft() {
        show()
        val done = compose.onNodeWithText("Done").getBoundsInRoot()
        assertTrue("Done is ${done.bottom - done.top} tall", done.bottom - done.top >= 56.dp - 0.5.dp)
        val arrow = compose.onNodeWithContentDescription("Close").getBoundsInRoot()
        val heading = compose.onNodeWithText("Edit task").getBoundsInRoot()
        assertTrue("The arrow sits left of the heading", arrow.right <= heading.left)
        assertTrue("The arrow has a 48dp target", arrow.bottom - arrow.top >= 48.dp - 0.5.dp)
        compose.onNodeWithText("Get it out of your head.").assertDoesNotExist()
        compose.onNodeWithText("Talk to …").assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("Interrupt now").assertDoesNotExist()
        compose.onNodeWithText("Same sheet as capture.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Empty title reverts.", substring = true).assertDoesNotExist()
    }

    @Test
    fun editSheetStacksTheFieldThenPlacesThenDone() {
        show()
        compose.onNodeWithText("Same sheet as capture.", substring = true).assertDoesNotExist()
        val field = compose.onNode(hasSetTextAction()).getBoundsInRoot()
        val inbox = compose.onNodeWithText("Inbox").getBoundsInRoot()
        val today = compose.onNodeWithText("Today").getBoundsInRoot()
        val later = compose.onNodeWithText("Later").getBoundsInRoot()
        val schedule = compose.onNodeWithText("Schedule").getBoundsInRoot()
        val done = compose.onNodeWithText("Done").getBoundsInRoot()
        assertTrue("The Task field sits above Inbox", field.bottom <= inbox.top + 0.5.dp)
        assertTrue("Places stay stacked", today.top >= inbox.bottom + 12.dp - 0.5.dp)
        assertTrue("Places stay stacked", later.top >= today.bottom + 12.dp - 0.5.dp)
        assertTrue("Places stay stacked", schedule.top >= later.bottom + 12.dp - 0.5.dp)
        assertTrue("Schedule sits above Done", schedule.bottom <= done.top + 0.5.dp)
        assertTrue("Sheet gaps are 12dp", inbox.top - field.bottom >= 12.dp - 0.5.dp)
        assertTrue("Done is mint", labelColor("Done") == FocusColors.Mint)
        val heading = compose.onNodeWithText("Edit task").getBoundsInRoot()
        assertTrue(
            "The sheet sits at the bottom, with room above: heading.top=${heading.top}",
            heading.top > 80.dp,
        )
    }

    private fun labelColor(text: String): Color {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
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
                    edit =
                        CaptureEdit(
                            title = "Hold this",
                            place = CapturePlace.INBOX,
                        ),
                    onCommitEdit = { title, place, day ->
                        saved += EditCommit(title, place, day)
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

    private data class EditCommit(
        val title: String,
        val place: CapturePlace?,
        val scheduledEpochDay: Long?,
    )

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
