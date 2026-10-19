// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.testing.sampleTask
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeCompactPinTagTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longTitleEllipsizesAndKeepsTheMainTagWithAnEightDpGap() {
        val title =
            "A very long Today title that must ellipsize before the MAIN pin " +
                "tag so the tag stays on the card instead of being pushed off."
        val task = sampleTask(id = "pin-1", title = title)
        val bands =
            HomeBands(
                morning = HomeBandGroup(HomeBand.MORNING, phase = BandPhase.PAST),
                noon =
                HomeBandGroup(
                    band = HomeBand.NOON,
                    tasks = listOf(task),
                    counts = HomeBandCounts(left = 1),
                    phase = BandPhase.OPEN,
                ),
                night = HomeBandGroup(HomeBand.NIGHT, phase = BandPhase.FUTURE),
                anytime = HomeBandGroup(HomeBand.ANYTIME, phase = BandPhase.ALWAYS_OPEN),
                openBand = HomeBand.NOON,
            )
        val ui = TaskUi(id = task.id, title = task.title, returnCue = task.returnCue)
        compose.setContent {
            FocusStackTheme {
                HomeBandsSection(
                    bands = bands,
                    filled = listOf(SlotUi(SlotRole.OPEN, ui) to ui),
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
        val pin = compose.activity.getString(R.string.home_task_pin, 3)
        val card = compose.onNode(hasContentDescription(title, substring = true)).getBoundsInRoot()
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).getBoundsInRoot()
        val tag = compose.onNodeWithText(pin, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("MAIN tag sits inside the card", tag.left >= card.left && tag.right <= card.right + 0.5.dp)
        assertTrue("MAIN tag stays vertically on the card", tag.top >= card.top && tag.bottom <= card.bottom)
        assertTrue(
            "MAIN sits at the card's right edge: pad=${card.right - tag.right}",
            card.right - tag.right <= HomeMetrics.TaskPaddingHorizontal + 0.5.dp,
        )
        assertTrue("title and MAIN tag do not overlap", titleBounds.right <= tag.left + 0.5.dp)
        assertTrue(
            "gap before MAIN is at least 8dp: ${tag.left - titleBounds.right}",
            tag.left - titleBounds.right >= 8.dp - 0.5.dp,
        )
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertTrue("the title ellipsizes", layout.isLineEllipsized(0) || layout.didOverflowWidth)
    }
}
