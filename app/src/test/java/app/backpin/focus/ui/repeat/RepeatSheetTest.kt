// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.toDraft
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.edit.CaptureEdit
import app.backpin.focus.ui.edit.EditTaskSheet
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun taskEditSheetDoesNotOfferRepeat() {
        compose.setContent {
            FocusStackTheme {
                EditTaskSheet(
                    edit = CaptureEdit(taskId = "task-1", title = "Water plants", place = CapturePlace.INBOX),
                    onDismiss = {},
                    onCommitEdit = { _, _, _ -> },
                    recognizer = SilentRecognizer,
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
        compose.onNodeWithText("How often").assertExists()
        compose.onNodeWithText("A gentle reminder at the start of that band.").assertExists()
        compose.onNodeWithText("Days").assertDoesNotExist()
        tap("Noon")
        tap("Every 2 days")
        tap("Done")
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
        tap("Done")
        assertEquals(
            existing.toDraft().copy(title = "Water the plants", band = RepeatBand.NIGHT),
            committed,
        )
        tap("Archive")
        assertEquals("r1", archived)
    }

    @Test
    fun daysFieldShowsOnlyForOtherAndMapsToCadence() {
        val commits = mutableListOf<RepeatDraft>()
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Water plants"),
                    onCommit = { commits += it },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("Every day").assertExists()
        compose.onNodeWithText("Every 2 days").assertExists()
        compose.onNodeWithText("Every 3 days").assertExists()
        compose.onNodeWithText("Every 7 days").assertExists()
        compose.onNodeWithText("Other").assertExists()
        compose.onNodeWithText("Days").assertDoesNotExist()
        tap("Other")
        compose.onNodeWithText("Other").assertIsSelected()
        compose.onNodeWithText("Every day").assertIsNotSelected()
        compose.onNodeWithText("Days").assertExists()
        compose.onNode(hasSetTextAction() and hasText("1")).performTextReplacement("5")
        tap("Done")
        assertEquals(
            listOf(RepeatDraft(title = "Water plants", everyNDays = 5, reminderOn = true)),
            commits,
        )
        tap("Every day")
        compose.onNodeWithText("Days").assertDoesNotExist()
    }

    @Test
    fun customCadenceOpensOnOther() {
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Water plants", everyNDays = 5),
                    onCommit = {},
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("Other").assertIsSelected()
        compose.onNodeWithText("Every day").assertIsNotSelected()
        compose.onNodeWithText("Days").assertExists()
        compose.onNode(hasSetTextAction() and hasText("5")).assertExists()
        tap("Every 2 days")
        compose.onNodeWithText("Days").assertDoesNotExist()
        compose.onNodeWithText("Other").assertIsNotSelected()
        compose.onNodeWithText("Every 2 days").assertIsSelected()
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
        compose.onNodeWithText("Done").assertIsNotEnabled()
        assertNull(committed)
    }

    @Test
    fun backAfterEditingAFieldSavesTheChange() {
        val commits = mutableListOf<RepeatDraft>()
        var dismissed = false
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds"),
                    onCommit = { commits += it },
                    onDismiss = { dismissed = true },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Take meds")).performTextReplacement("Take vitamins")
        tap("Noon")
        tap("Every 2 days")
        compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(
            listOf(
                RepeatDraft(
                    title = "Take vitamins",
                    band = RepeatBand.NOON,
                    everyNDays = 2,
                    reminderOn = true,
                ),
            ),
            commits,
        )
        assertFalse("Back with a titled draft commits instead of dismissing", dismissed)
    }

    @Test
    fun backWithEmptyTitleWritesNothingAndCloses() {
        val commits = mutableListOf<RepeatDraft>()
        var dismissed = false
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(),
                    onCommit = { commits += it },
                    onDismiss = { dismissed = true },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals("Back must not call onCommit for a blank title", emptyList<RepeatDraft>(), commits)
        assertTrue("UI must dismissRepeatSheet when commit returns false", dismissed)
    }

    @Test
    fun backAfterClearingTheTitleWritesNothingAndCloses() {
        val commits = mutableListOf<RepeatDraft>()
        var dismissed = false
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds", band = RepeatBand.NOON),
                    onCommit = { commits += it },
                    onDismiss = { dismissed = true },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Take meds")).performTextReplacement("   ")
        compose.onNodeWithContentDescription("Close").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals("Clearing the title must not persist a blank draft", emptyList<RepeatDraft>(), commits)
        assertTrue(dismissed)
    }

    @Test
    fun doneWithEmptyTitleKeepsTheSheetOpen() {
        val commits = mutableListOf<RepeatDraft>()
        var dismissed = false
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(),
                    onCommit = { commits += it },
                    onDismiss = { dismissed = true },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Done").performScrollTo()
        compose.onNodeWithText("Done").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(emptyList<RepeatDraft>(), commits)
        assertFalse(dismissed)
        compose.onNodeWithText("A small repeating thing.").assertExists()
        compose.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun doneAfterClearingTheTitleKeepsTheSheetOpenAndWritesNothing() {
        val commits = mutableListOf<RepeatDraft>()
        var dismissed = false
        compose.setContent {
            FocusStackTheme {
                RepeatSheet(
                    draft = RepeatDraft(title = "Take meds"),
                    onCommit = { commits += it },
                    onDismiss = { dismissed = true },
                    onArchive = {},
                    recognizer = SilentRecognizer,
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Take meds")).performTextReplacement("")
        compose.onNodeWithText("Done").assertIsNotEnabled()
        compose.onNodeWithText("Done").performScrollTo()
        compose.onNodeWithText("Done").performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(emptyList<RepeatDraft>(), commits)
        assertFalse(dismissed)
        compose.onNodeWithText("A small repeating thing.").assertExists()
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.performScrollTo()
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
