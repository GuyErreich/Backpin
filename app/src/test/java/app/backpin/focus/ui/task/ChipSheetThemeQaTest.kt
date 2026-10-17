// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.MissedRepeatSheetUi
import app.backpin.focus.ui.MissedTaskSheetUi
import app.backpin.focus.ui.TaskSheetRole
import app.backpin.focus.ui.TaskSheetUi
import app.backpin.focus.ui.home.RepeatSection
import app.backpin.focus.ui.repeat.MissedRepeatSheet
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.theme.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ChipSheetThemeQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun taskSheetChipsFollowEachThemeWithoutSignalLeftovers() {
        ThemeId.entries.forEach { themeId ->
            compose.setContent {
                FocusStackTheme(themeId = themeId) {
                    TaskSheet(
                        state =
                        TaskSheetUi(
                            taskId = "task-1",
                            title = "Park me",
                            role = TaskSheetRole.TODAY_OPEN,
                            canInterrupt = true,
                        ),
                        onDismiss = {},
                        onStart = {},
                        onDone = {},
                        onLater = {},
                        onEdit = {},
                        onInterrupt = {},
                        onToday = {},
                        onResume = {},
                    )
                }
            }
            val palette = paletteFor(themeId)
            assertEquals(themeId.name, palette.done, labelColor("Done"))
            assertEquals(themeId.name, palette.now, labelColor("Interrupt"))
            assertEquals(themeId.name, palette.textPrimary, labelColor("Start"))
            assertEquals(themeId.name, palette.textPrimary, labelColor("Later"))
            assertEquals(themeId.name, palette.textPrimary, labelColor("Edit"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Done"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Interrupt"))
            if (themeId != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(themeId.name, signal.done, labelColor("Done"))
                assertNotEquals(themeId.name, signal.now, labelColor("Interrupt"))
                assertNotEquals(themeId.name, signal.textPrimary, labelColor("Start"))
            }
        }
    }

    @Test
    fun missedSheetsFollowEachThemeWithoutErrorRed() {
        ThemeId.entries.forEach { themeId ->
            compose.setContent {
                FocusStackTheme(themeId = themeId) {
                    MissedTaskSheet(
                        state = MissedTaskSheetUi("task-1", "Call Dana"),
                        onDismiss = {},
                        onDoneNow = {},
                        onTomorrow = {},
                        onPickDay = {},
                    )
                }
            }
            val palette = paletteFor(themeId)
            assertEquals(themeId.name, palette.done, labelColor("Done now"))
            assertEquals(themeId.name, palette.missed, labelColor("Tomorrow"))
            assertEquals(themeId.name, palette.missed, labelColor("Pick a day"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Done now"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Tomorrow"))
            assertNotEquals(themeId.name, Color.Red, labelColor("Tomorrow"))
            if (themeId != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(themeId.name, signal.done, labelColor("Done now"))
                assertNotEquals(themeId.name, signal.missed, labelColor("Tomorrow"))
            }

            compose.setContent {
                FocusStackTheme(themeId = themeId) {
                    MissedRepeatSheet(
                        state = MissedRepeatSheetUi("repeat-1", 0L, "Take meds"),
                        onDismiss = {},
                        onDoneNow = {},
                        onSkipToday = {},
                    )
                }
            }
            assertEquals(themeId.name, paletteFor(themeId).done, labelColor("Done now"))
            assertEquals(themeId.name, paletteFor(themeId).textPrimary, labelColor("Skip today"))
            assertNotEquals(themeId.name, paletteFor(themeId).danger, labelColor("Skip today"))
        }
    }

    @Test
    fun missedRepeatRowsStayMutedAmberAndNeverErrorRedInEveryTheme() {
        ThemeId.entries.forEach { themeId ->
            compose.setContent {
                FocusStackTheme(themeId = themeId) {
                    RepeatSection(
                        rows =
                        RepeatRowsState(
                            rows =
                            listOf(
                                RepeatRow(
                                    occurrence =
                                    RepeatOccurrence(
                                        repeat =
                                        Repeat(
                                            id = "repeat-1",
                                            title = "Take meds",
                                            band = RepeatBand.MORNING,
                                            everyNDays = 1,
                                            anchorEpochDay = 0L,
                                            reminderOn = true,
                                            sortOrder = 0L,
                                            createdAtEpochMillis = 0L,
                                            archivedAtEpochMillis = null,
                                        ),
                                        epochDay = 0L,
                                        status = RepeatDayStatus.MISSED,
                                    ),
                                    everyNDays = 1,
                                ),
                            ),
                            leftCount = 0,
                            total = 1,
                        ),
                        reducedMotion = true,
                        onCheckOff = {},
                        onOpenMissed = {},
                        onEdit = {},
                        onCreate = {},
                    )
                }
            }
            val palette = paletteFor(themeId)
            assertEquals(themeId.name, FocusColors.Muted, labelColor("Take meds"))
            assertEquals(themeId.name, palette.textSecondary, labelColor("Take meds"))
            assertEquals(themeId.name, palette.missed, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Take meds"))
            assertNotEquals(themeId.name, palette.danger, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(themeId.name, Color.Red, labelColor("Missed at morning · muted until end of day"))
            if (themeId != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(themeId.name, signal.missed, labelColor("Missed at morning · muted until end of day"))
                assertNotEquals(themeId.name, signal.textSecondary, labelColor("Take meds"))
            }
        }
    }

    private fun labelColor(text: String): Color {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }
}
