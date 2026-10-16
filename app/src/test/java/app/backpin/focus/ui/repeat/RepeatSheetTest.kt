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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
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
    fun createSheetSavesBandIntervalAndReminder() {
        var created: Created? = null
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds"),
                    onDismiss = {},
                    onCreate = { title, band, everyNDays, reminderOn ->
                        created = Created(title, band, everyNDays, reminderOn)
                    },
                    onUpdate = { _, _, _, _, _, _, _ -> },
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
        assertEquals(Created("Take meds", RepeatBand.NOON, 2, true), created)
    }

    @Test
    fun editSheetArchivesAndCanTurnTheReminderOff() {
        var archived: String? = null
        var updated: Updated? = null
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
                    onDismiss = {},
                    onCreate = { _, _, _, _ -> },
                    onUpdate = { id, title, band, everyNDays, reminderOn, sortOrder, anchor ->
                        updated = Updated(id, title, band, everyNDays, reminderOn, sortOrder, anchor)
                    },
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
            Updated("r1", "Water the plants", RepeatBand.NIGHT, 1, true, 4, 20_000),
            updated,
        )
        tap("Archive")
        assertEquals("r1", archived)
    }

    @Test
    fun blankTitleDoesNotCreate() {
        var created = 0
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(),
                    onDismiss = {},
                    onCreate = { _, _, _, _ -> created += 1 },
                    onUpdate = { _, _, _, _, _, _, _ -> },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(0, created)
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private data class Created(
        val title: String,
        val band: RepeatBand,
        val everyNDays: Int,
        val reminderOn: Boolean,
    )

    private data class Updated(
        val id: String,
        val title: String,
        val band: RepeatBand,
        val everyNDays: Int,
        val reminderOn: Boolean,
        val sortOrder: Long,
        val anchorEpochDay: Long,
    )
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
