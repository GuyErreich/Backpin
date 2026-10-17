// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        var themeId by mutableStateOf(ThemeId.SIGNAL)
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
        ThemeId.entries.forEach { id ->
            themeId = id
            compose.waitForIdle()
            val palette = paletteFor(id)
            assertEquals(id.name, palette.done, labelColor("Done"))
            assertEquals(id.name, palette.now, labelColor("Interrupt"))
            assertEquals(id.name, palette.textPrimary, labelColor("Start"))
            assertEquals(id.name, palette.textPrimary, labelColor("Later"))
            assertEquals(id.name, palette.textPrimary, labelColor("Edit"))
            assertNotEquals(id.name, palette.danger, labelColor("Done"))
            assertNotEquals(id.name, palette.danger, labelColor("Interrupt"))
            if (id != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(id.name, signal.done, labelColor("Done"))
                assertNotEquals(id.name, signal.now, labelColor("Interrupt"))
                assertNotEquals(id.name, signal.textPrimary, labelColor("Start"))
            }
        }
    }

    @Test
    fun missedSheetsFollowEachThemeWithoutErrorRed() {
        var themeId by mutableStateOf(ThemeId.SIGNAL)
        var showRepeat by mutableStateOf(false)
        compose.setContent {
            FocusStackTheme(themeId = themeId) {
                if (showRepeat) {
                    MissedRepeatSheet(
                        state = MissedRepeatSheetUi("repeat-1", 0L, "Take meds"),
                        onDismiss = {},
                        onDoneNow = {},
                        onSkipToday = {},
                    )
                } else {
                    MissedTaskSheet(
                        state = MissedTaskSheetUi("task-1", "Call Dana"),
                        onDismiss = {},
                        onDoneNow = {},
                        onTomorrow = {},
                        onPickDay = {},
                    )
                }
            }
        }
        ThemeId.entries.forEach { id ->
            themeId = id
            showRepeat = false
            compose.waitForIdle()
            val palette = paletteFor(id)
            assertEquals(id.name, palette.done, labelColor("Done now"))
            assertEquals(id.name, palette.missed, labelColor("Tomorrow"))
            assertEquals(id.name, palette.missed, labelColor("Pick a day"))
            assertNotEquals(id.name, palette.danger, labelColor("Done now"))
            assertNotEquals(id.name, palette.danger, labelColor("Tomorrow"))
            assertNotEquals(id.name, Color.Red, labelColor("Tomorrow"))
            if (id != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(id.name, signal.done, labelColor("Done now"))
                assertNotEquals(id.name, signal.missed, labelColor("Tomorrow"))
            }
            showRepeat = true
            compose.waitForIdle()
            assertEquals(id.name, palette.done, labelColor("Done now"))
            assertEquals(id.name, palette.textPrimary, labelColor("Skip today"))
            assertNotEquals(id.name, palette.danger, labelColor("Skip today"))
        }
    }

    @Test
    fun missedRepeatRowsStayMutedAmberAndNeverErrorRedInEveryTheme() {
        var themeId by mutableStateOf(ThemeId.SIGNAL)
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
        ThemeId.entries.forEach { id ->
            themeId = id
            compose.waitForIdle()
            val palette = paletteFor(id)
            assertEquals(id.name, FocusColors.Muted, labelColor("Take meds"))
            assertEquals(id.name, palette.textSecondary, labelColor("Take meds"))
            assertEquals(id.name, palette.missed, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(id.name, palette.danger, labelColor("Take meds"))
            assertNotEquals(id.name, palette.danger, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(id.name, Color.Red, labelColor("Missed at morning · muted until end of day"))
            if (id != ThemeId.SIGNAL) {
                val signal = paletteFor(ThemeId.SIGNAL)
                assertNotEquals(id.name, signal.missed, labelColor("Missed at morning · muted until end of day"))
                assertNotEquals(id.name, signal.textSecondary, labelColor("Take meds"))
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
