// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsSectionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eachSectionIsATitleALineAndTheChoice() {
        show(UserPreferences())
        compose.onNodeWithText("Tasks stay on this phone. There is no account.").assertExists()
        listOf("LOOK", "DAY", "REMINDERS", "PHRASES").forEach { section ->
            compose.onNodeWithText(section).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("How many tasks Today holds. Empty places stay empty.").assertExists()
        compose.onNodeWithText("Months, once a date is a month away.").assertExists()
        compose.onNodeWithText("A reminder uses the start of that band.").assertExists()
        compose.onNodeWithText("Nudges can arrive a few minutes late.").assertExists()
        compose.onNodeWithText("Quiet from").assertExists()
        compose.onNodeWithText("Quiet until").assertExists()
        compose.onNodeWithText("Clear").assertDoesNotExist()
        compose.onNodeWithText("A silent notice of what is open.").assertExists()
        compose.onNodeWithText("How many open tasks Today can hold", substring = true).assertDoesNotExist()
        compose.onNodeWithText("An ongoing notice while Today has an open task", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("End").assertDoesNotExist()
    }

    @Test
    fun remindersKeepFourNudgesOnOneLineAndTwoQuietHourButtons() {
        show(
            UserPreferences(
                nudgesEnabled = true,
                nudgeMinutes = 30,
                quietStart = "22:00",
                quietEnd = "08:00",
            ),
        )
        compose.onNodeWithText("Today").performScrollTo()
        assertOneRow(bounds("1"), bounds("2"), bounds("3"), bounds("4"), bounds("5"))
        compose.onNodeWithText("Quiet until").performScrollTo()
        val off = bounds("Off")
        val thirty = bounds("30 min")
        val oneHour = bounds("1 h")
        val twoHours = bounds("2 h")
        assertOneRow(off, thirty, oneHour, twoHours)
        val from = bounds("Quiet from")
        val until = bounds("Quiet until")
        val clear = bounds("Clear")
        assertTrue(from.top > off.bottom)
        assertEquals(from.left.value, until.left.value, 1f)
        assertEquals(from.right.value - from.left.value, until.right.value - until.left.value, 1f)
        assertTrue(until.top > from.bottom)
        assertTrue(clear.left.value < from.left.value)
        assertTrue(clear.top > from.top)
        assertTrue(buttonWidth("Off") < 70)
        assertTrue(buttonHeight("Off") >= 56)
    }

    @Test
    fun removePhraseAsksBeforeItDeletes() {
        var removed = -1
        show(UserPreferences(savedPhrases = listOf("Talk to <name>")), onRemovePhrase = { removed = it })
        compose.onNodeWithText("Remove").performScrollTo().performTouchInput { click(center) }
        assertEquals(-1, removed)
        compose.onNodeWithText("Remove this phrase?").assertIsDisplayed()
        compose.onAllNodesWithText("Talk to <name>").assertCountEquals(2)
        compose.onNodeWithText("Cancel").performTouchInput { click(center) }
        assertEquals(-1, removed)
        compose.onNodeWithText("Remove this phrase?").assertDoesNotExist()
        compose.onNodeWithText("Remove").performScrollTo().performTouchInput { click(center) }
        compose.onAllNodesWithText("Remove")[1].performTouchInput { click(center) }
        assertEquals(0, removed)
        compose.onNodeWithText("Remove this phrase?").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun bounds(label: String): DpRect = compose.onNodeWithText(label).getUnclippedBoundsInRoot()

    private fun buttonWidth(label: String): Int = buttonNode(label).size.width

    private fun buttonHeight(label: String): Int = buttonNode(label).size.height

    private fun buttonNode(label: String) = generateSequence(
        compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode(),
    ) { it.parent }.first { it.config.contains(SemanticsActions.OnClick) }

    private fun assertOneRow(vararg boxes: DpRect) {
        val top = boxes.first().top.value
        boxes.forEach { box -> assertEquals(top, box.top.value, 1f) }
        for (index in 1 until boxes.size) {
            assertTrue(boxes[index].left > boxes[index - 1].right)
        }
    }

    private fun show(
        preferences: UserPreferences,
        onRemovePhrase: (Int) -> Unit = {},
    ) {
        compose.setContent {
            FocusStackTheme {
                SettingsScreen(
                    preferences = preferences,
                    onNudgeSelected = { _, _ -> },
                    onQuietHours = { _, _ -> },
                    onCurrentNotification = {},
                    onTodayLimit = {},
                    onAddPhrase = {},
                    onRenamePhrase = { _, _ -> },
                    onRemovePhrase = onRemovePhrase,
                    bandTimes = BandTimes.DEFAULT,
                )
            }
        }
    }
}
