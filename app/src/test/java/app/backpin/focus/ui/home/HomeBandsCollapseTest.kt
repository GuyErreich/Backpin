// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeBandsCollapseTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pastIsCollapsedSummaryOpenIsExpandedFutureIsHeaderAndCountAnytimeHasNoToggle() {
        show(noonBands())
        compose.onNodeWithText("MORNING").assertIsDisplayed()
        compose.onNodeWithText("MORNING").assertHasClickAction()
        compose.onNodeWithText("Morning pills", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(doneOf(1, 1)).assertIsDisplayed()

        compose.onNodeWithText("NOON · NOW").assertIsDisplayed()
        compose.onNodeWithText("NOON · NOW").assertHasClickAction()
        compose.onNodeWithText("Noon walk", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(things(1)).assertIsDisplayed()

        compose.onNodeWithText("NIGHT").assertIsDisplayed()
        compose.onNodeWithText("NIGHT").assertHasClickAction()
        compose.onNodeWithText(futureCount(2), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Night stretch", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithText("ANYTIME").assertIsDisplayed()
        compose.onNodeWithText("ANYTIME").assertHasNoClickAction()
        compose.onNodeWithText("Loose thread", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tappingPastSummaryAndFutureHeaderExpandsRows() {
        show(noonBands())
        tap("MORNING")
        compose.onNodeWithText("MORNING").assertIsDisplayed()
        compose.onNodeWithText("Morning pills", useUnmergedTree = true).assertIsDisplayed()
        tap("NIGHT")
        compose.onNodeWithText("Night stretch", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Noon walk", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Loose thread", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun expandedStateIsKeptPerBandUntilThatPhaseChanges() {
        var bands by mutableStateOf(noonBands())
        compose.setContent {
            FocusStackTheme {
                HomeBandsSection(
                    bands = bands,
                    filled = filledFrom(bands),
                    hasRoom = false,
                    todayLimit = 3,
                    reducedMotion = true,
                    somethingCurrent = false,
                    onOpenFocus = {},
                    onStart = {},
                    onDone = {},
                    onBringBack = { _, _ -> },
                    onInterrupt = {},
                    onReorder = {},
                    onEditTask = {},
                    onCheckOffRepeat = {},
                    onEditRepeat = {},
                )
            }
        }
        compose.waitForIdle()
        tap("NIGHT")
        compose.onNodeWithText("Night stretch", useUnmergedTree = true).assertIsDisplayed()
        tap("NOON · NOW")
        compose.onNodeWithText("Noon walk", useUnmergedTree = true).assertDoesNotExist()
        compose.waitForIdle()
        compose.onNodeWithText("Night stretch", useUnmergedTree = true).assertIsDisplayed()

        compose.runOnIdle { bands = nightBands() }
        compose.waitForIdle()
        compose.onNodeWithText("Noon walk", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("NOON").assertIsDisplayed()
        compose.onNodeWithText("Night stretch", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("NIGHT · NOW").assertIsDisplayed()
        compose.onNodeWithText("Loose thread", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun show(bands: HomeBands) {
        compose.setContent {
            FocusStackTheme {
                HomeBandsSection(
                    bands = bands,
                    filled = filledFrom(bands),
                    hasRoom = false,
                    todayLimit = 3,
                    reducedMotion = true,
                    somethingCurrent = false,
                    onOpenFocus = {},
                    onStart = {},
                    onDone = {},
                    onBringBack = { _, _ -> },
                    onInterrupt = {},
                    onReorder = {},
                    onEditTask = {},
                    onCheckOffRepeat = {},
                    onEditRepeat = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.waitForIdle()
        val node = compose.onNode(hasText(text) and hasClickAction())
        node.assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun doneOf(
        done: Int,
        total: Int,
    ): String = compose.activity.getString(R.string.home_band_count_of_done, done, total)

    private fun things(count: Int): String = compose.activity.resources.getQuantityString(R.plurals.home_band_count_things, count, count)

    private fun futureCount(left: Int): String = compose.activity.getString(R.string.home_band_count, left)

    private fun noonBands(): HomeBands = HomeBands(
        morning =
        group(
            HomeBand.MORNING,
            BandPhase.PAST,
            repeats = listOf(repeatRow("Morning pills", RepeatBand.MORNING, RepeatDayStatus.DONE)),
            counts = HomeBandCounts(done = 1, missed = 1, left = 0),
        ),
        noon =
        group(
            HomeBand.NOON,
            BandPhase.OPEN,
            tasks = listOf(task("noon", "Noon walk")),
            counts = HomeBandCounts(left = 1),
        ),
        night =
        group(
            HomeBand.NIGHT,
            BandPhase.FUTURE,
            repeats = listOf(repeatRow("Night stretch", RepeatBand.NIGHT, RepeatDayStatus.OPEN)),
            counts = HomeBandCounts(left = 2),
        ),
        anytime =
        group(
            HomeBand.ANYTIME,
            BandPhase.ALWAYS_OPEN,
            tasks = listOf(task("any", "Loose thread")),
        ),
        openBand = HomeBand.NOON,
    )

    private fun nightBands(): HomeBands = HomeBands(
        morning =
        group(
            HomeBand.MORNING,
            BandPhase.PAST,
            repeats = listOf(repeatRow("Morning pills", RepeatBand.MORNING, RepeatDayStatus.DONE)),
            counts = HomeBandCounts(done = 1, missed = 1, left = 0),
        ),
        noon =
        group(
            HomeBand.NOON,
            BandPhase.PAST,
            tasks = listOf(task("noon", "Noon walk")),
            counts = HomeBandCounts(left = 1),
        ),
        night =
        group(
            HomeBand.NIGHT,
            BandPhase.OPEN,
            repeats = listOf(repeatRow("Night stretch", RepeatBand.NIGHT, RepeatDayStatus.OPEN)),
            counts = HomeBandCounts(left = 2),
        ),
        anytime =
        group(
            HomeBand.ANYTIME,
            BandPhase.ALWAYS_OPEN,
            tasks = listOf(task("any", "Loose thread")),
        ),
        openBand = HomeBand.NIGHT,
    )

    private fun group(
        band: HomeBand,
        phase: BandPhase,
        tasks: List<Task> = emptyList(),
        repeats: List<RepeatRow> = emptyList(),
        counts: HomeBandCounts = HomeBandCounts(),
    ): HomeBandGroup = HomeBandGroup(
        band = band,
        tasks = tasks,
        repeats = repeats,
        counts = counts,
        phase = phase,
    )

    private fun filledFrom(bands: HomeBands): List<Pair<SlotUi, TaskUi>> = bands.inOrder().flatMap { group ->
        group.tasks.map { task ->
            val ui = TaskUi(id = task.id, title = task.title, returnCue = task.returnCue)
            SlotUi(SlotRole.OPEN, ui) to ui
        }
    }

    private fun task(
        id: String,
        title: String,
    ): Task = Task(
        id = id,
        title = title,
        bucket = TaskBucket.TODAY,
        status = TaskStatus.OPEN,
        returnCue = null,
        sortOrder = 0,
        createdAtEpochMillis = 0,
        completedAtEpochMillis = null,
    )

    private fun repeatRow(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
    ): RepeatRow = RepeatRow(
        occurrence =
        RepeatOccurrence(
            repeat =
            Repeat(
                id = title,
                title = title,
                band = band,
                everyNDays = 1,
                anchorEpochDay = 0,
                reminderOn = true,
                sortOrder = 0,
                createdAtEpochMillis = 0,
                archivedAtEpochMillis = null,
            ),
            epochDay = 0,
            status = status,
        ),
        everyNDays = 1,
    )
}
