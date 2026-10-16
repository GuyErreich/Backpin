package app.backpin.focus.ui.repeat

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.toDraft
import app.backpin.focus.ui.capture.CaptureEdit
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureRepeatOpensTheRepeatSheetWithThatTitle() {
        var captured: String? = null
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = false,
                    onDismiss = {},
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                    onPlaceLater = {},
                    onSchedule = { _, _, _ -> },
                    recognizer = SilentRecognizer,
                    onRepeat = { captured = it },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Take meds")
        compose.onNodeWithText("Repeat").performScrollTo()
        tap("Repeat")
        assertEquals("Take meds", captured)
    }

    @Test
    fun taskEditSheetDoesNotOfferRepeat() {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = false,
                    onDismiss = {},
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                    onPlaceLater = {},
                    onSchedule = { _, _, _ -> },
                    edit = CaptureEdit(title = "Water plants", place = CapturePlace.INBOX),
                    onCommitEdit = { _, _, _ -> },
                    recognizer = SilentRecognizer,
                    onRepeat = {},
                )
            }
        }
        compose.onNodeWithText("Edit task").assertExists()
        compose.onNodeWithText("Repeat").assertDoesNotExist()
    }

    @Test
    fun createSheetSavesBandIntervalAndReminder() {
        var committed: RepeatDraft? = null
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds"),
                    onCommit = { committed = it },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("A small repeating thing.").assertExists()
        compose.onNodeWithText("Repeats stay off Today. Checking them off is enough.").assertExists()
        tap("Noon")
        tap("Every 2 days")
        tap("Save")
        assertEquals(
            RepeatDraft(title = "Take meds", band = RepeatBand.NOON, everyNDays = 2, reminderOn = true),
            committed,
        )
    }

    @Test
    fun backCommitsTheDraft() {
        var committed: RepeatDraft? = null
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds", band = RepeatBand.NOON),
                    onCommit = { committed = it },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithContentDescription("Close").assertExists()
        compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(
            RepeatDraft(title = "Take meds", band = RepeatBand.NOON, everyNDays = 1, reminderOn = true),
            committed,
        )
    }

    @Test
    fun editSheetArchivesAndCanTurnTheReminderOff() {
        var archived: String? = null
        var committed: RepeatDraft? = null
        val existing =
            Repeat(
                id = "r1",
                title = "Water plants",
                band = RepeatBand.NOON,
                everyNDays = 1,
                anchorEpochDay = 20_000,
                reminderOn = true,
                sortOrder = 4,
                createdAtEpochMillis = 1,
                archivedAtEpochMillis = null,
            )
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = existing.toDraft(),
                    onCommit = { committed = it },
                    onArchive = { archived = it },
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("This repeat.").assertExists()
        compose.onNodeWithText("Archive").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Water plants")).performTextReplacement("Water the plants")
        tap("Night")
        tap("Save")
        assertEquals(
            existing.toDraft().copy(title = "Water the plants", band = RepeatBand.NIGHT),
            committed,
        )
        tap("Archive")
        assertEquals("r1", archived)
    }

    @Test
    fun blankTitleDoesNotCreate() {
        var committed: RepeatDraft? = null
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(),
                    onCommit = { committed = it },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertNull(committed)
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
