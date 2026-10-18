// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
        var live by mutableStateOf(LiveSheetColors.SIGNAL)
        compose.setContent {
            FocusStackTheme(themeId = themeId) {
                CaptureLiveColors { live = it }
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
                    onEdit = {},
                    onInterrupt = {},
                    onToday = {},
                    onResume = {},
                    onBringBack = {},
                )
            }
        }
        ThemeId.entries.forEach { id ->
            themeId = id
            compose.waitForIdle()
            val palette = liveColors(id, live)
            assertEquals(id.name, palette.mint, labelColor("Done"))
            assertEquals(id.name, palette.lavender, labelColor("Interrupt"))
            assertEquals(id.name, palette.text, labelColor("Start"))
            assertEquals(id.name, palette.text, labelColor("Later"))
            assertEquals(id.name, palette.text, labelColor("Edit"))
            assertNotEquals(id.name, palette.danger, labelColor("Done"))
            assertNotEquals(id.name, palette.danger, labelColor("Interrupt"))
            if (id != ThemeId.SIGNAL) {
                assertNotEquals(id.name, FocusColors.Mint, labelColor("Done"))
                assertNotEquals(id.name, FocusColors.Lavender, labelColor("Interrupt"))
                assertNotEquals(id.name, FocusColors.Text, labelColor("Start"))
            }
        }
    }

    @Test
    fun missedSheetsFollowEachThemeWithoutErrorRed() {
        var themeId by mutableStateOf(ThemeId.SIGNAL)
        var showRepeat by mutableStateOf(false)
        var live by mutableStateOf(LiveSheetColors.SIGNAL)
        compose.setContent {
            FocusStackTheme(themeId = themeId) {
                CaptureLiveColors { live = it }
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
            val palette = liveColors(id, live)
            assertEquals(id.name, palette.mint, labelColor("Done now"))
            assertEquals(id.name, palette.text, labelColor("Tomorrow"))
            assertEquals(id.name, palette.text, labelColor("Pick a day"))
            assertNotEquals(id.name, palette.amber, labelColor("Tomorrow"))
            assertNotEquals(id.name, palette.amber, labelColor("Pick a day"))
            assertNotEquals(id.name, palette.mint, labelColor("Tomorrow"))
            assertNotEquals(id.name, palette.danger, labelColor("Done now"))
            assertNotEquals(id.name, palette.danger, labelColor("Tomorrow"))
            assertNotEquals(id.name, Color.Red, labelColor("Tomorrow"))
            if (id != ThemeId.SIGNAL) {
                assertNotEquals(id.name, FocusColors.Mint, labelColor("Done now"))
                assertNotEquals(id.name, FocusColors.Text, labelColor("Tomorrow"))
            }
            showRepeat = true
            compose.waitForIdle()
            val skipPalette = liveColors(id, live)
            assertEquals(id.name, skipPalette.mint, labelColor("Done now"))
            assertEquals(id.name, skipPalette.text, labelColor("Skip today"))
            assertNotEquals(id.name, skipPalette.danger, labelColor("Skip today"))
            if (id != ThemeId.SIGNAL) {
                assertNotEquals(id.name, FocusColors.Text, labelColor("Skip today"))
            }
        }
    }

    @Test
    fun emberMissedTaskActionChipsDoNotUseAmberWarning() {
        var live by mutableStateOf(LiveSheetColors.SIGNAL)
        compose.setContent {
            FocusStackTheme(themeId = ThemeId.EMBER) {
                CaptureLiveColors { live = it }
                MissedTaskSheet(
                    state = MissedTaskSheetUi("task-1", "Call Dana"),
                    onDismiss = {},
                    onDoneNow = {},
                    onTomorrow = {},
                    onPickDay = {},
                )
            }
        }
        compose.waitForIdle()
        val palette = liveColors(ThemeId.EMBER, live)
        assertEquals("Done now", palette.mint, labelColor("Done now"))
        listOf("Tomorrow", "Pick a day").forEach { label ->
            assertEquals(label, palette.text, labelColor(label))
            assertNotEquals(label, palette.amber, labelColor(label))
            assertNotEquals(label, palette.mint, labelColor(label))
        }
    }

    @Test
    fun missedRepeatRowsStayMutedAmberAndNeverErrorRedInEveryTheme() {
        var themeId by mutableStateOf(ThemeId.SIGNAL)
        var live by mutableStateOf(LiveSheetColors.SIGNAL)
        compose.setContent {
            FocusStackTheme(themeId = themeId) {
                CaptureLiveColors { live = it }
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
            val palette = liveColors(id, live)
            assertEquals(id.name, palette.muted, labelColor("Take meds"))
            assertEquals(id.name, palette.amber, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(id.name, palette.danger, labelColor("Take meds"))
            assertNotEquals(id.name, palette.danger, labelColor("Missed at morning · muted until end of day"))
            assertNotEquals(id.name, Color.Red, labelColor("Missed at morning · muted until end of day"))
            if (id != ThemeId.SIGNAL) {
                assertNotEquals(id.name, FocusColors.Amber, labelColor("Missed at morning · muted until end of day"))
                assertNotEquals(id.name, FocusColors.Muted, labelColor("Take meds"))
            }
        }
    }

    @Composable
    private fun CaptureLiveColors(onLive: (LiveSheetColors) -> Unit) {
        val colors =
            LiveSheetColors(
                text = FocusColors.Live.Text,
                muted = FocusColors.Live.Muted,
                mint = FocusColors.Live.Mint,
                amber = FocusColors.Live.Amber,
                lavender = FocusColors.Live.Lavender,
                danger = FocusColors.Live.Danger,
            )
        SideEffect { onLive(colors) }
    }

    /**
     * [FocusColors.Live] for the selected theme. Plain [FocusColors] stay Signal;
     * a sheet that reads those instead of Live fails the non-Signal asserts.
     */
    private fun liveColors(
        id: ThemeId,
        live: LiveSheetColors,
    ): LiveSheetColors {
        val expected = paletteFor(id)
        assertEquals(id.name, expected.textPrimary, live.text)
        assertEquals(id.name, expected.textSecondary, live.muted)
        assertEquals(id.name, expected.done, live.mint)
        assertEquals(id.name, expected.missed, live.amber)
        assertEquals(id.name, expected.now, live.lavender)
        assertEquals(id.name, expected.danger, live.danger)
        return live
    }

    private data class LiveSheetColors(
        val text: Color,
        val muted: Color,
        val mint: Color,
        val amber: Color,
        val lavender: Color,
        val danger: Color,
    ) {
        companion object {
            val SIGNAL =
                LiveSheetColors(
                    text = paletteFor(ThemeId.SIGNAL).textPrimary,
                    muted = paletteFor(ThemeId.SIGNAL).textSecondary,
                    mint = paletteFor(ThemeId.SIGNAL).done,
                    amber = paletteFor(ThemeId.SIGNAL).missed,
                    lavender = paletteFor(ThemeId.SIGNAL).now,
                    danger = paletteFor(ThemeId.SIGNAL).danger,
                )
        }
    }

    private fun labelColor(text: String): Color {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }
}
