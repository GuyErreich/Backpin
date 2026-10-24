// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.theme.ChoicePillHeight
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
            compose.onNodeWithText(section).assertDoesNotExist()
        }
        compose.onNodeWithText("Theme").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("How many tasks Today holds. Empty places stay empty.").assertDoesNotExist()
        compose.onNodeWithText("Months, once a date is a month away.").assertExists()
        compose.onNodeWithText("Repeats").assertExists()
        compose.onNodeWithText("A reminder uses the start of that band.").assertDoesNotExist()
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
    fun remindersKeepFourNudgesOnOneLineAndQuietHoursOnTwoRows() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var saved: Pair<String?, String?>? = null
        try {
            show(
                UserPreferences(
                    nudgesEnabled = true,
                    nudgeMinutes = 30,
                    quietStart = "22:00",
                    quietEnd = "08:00",
                ),
                onQuietHours = { start, end -> saved = start to end },
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
            val start = bounds("22:00")
            val end = bounds("08:00")
            val clear = bounds("Clear")
            assertTrue(from.top > off.bottom)
            assertEquals(from.left.value, until.left.value, 1f)
            assertTrue(until.top > from.bottom)
            assertTrue(start.left > from.right)
            assertTrue(end.left > until.right)
            assertTrue(start.top < from.bottom && start.bottom > from.top)
            assertTrue(end.top < until.bottom && end.bottom > until.top)
            assertEquals(start.right.value, end.right.value, 1f)
            assertTrue(clear.top > until.bottom)
            assertEquals(clear.left.value, from.left.value, 1f)
            assertTrue(buttonWidth("Off") < 70)
            val offHeight = bounds("Off").bottom - bounds("Off").top
            assertTrue("Off is $offHeight tall", offHeight <= ChoicePillHeight + 0.5.dp)
            assertTrue("Off is $offHeight tall", offHeight >= ChoicePillHeight - 0.5.dp)
            compose.onNodeWithText("22:00").performTouchInput { click(center) }
            compose.onNodeWithText("Quiet hours start").assertIsDisplayed()
            compose.onNodeWithContentDescription("Hour").assertExists()
            compose.onNodeWithContentDescription("Minute").assertExists()
            compose.onNodeWithText("21").performTouchInput { click(center) }
            compose.waitForIdle()
            compose.onNodeWithText("Save").performTouchInput { click(center) }
            assertEquals("21:00" to "08:00", saved)
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, animator)
        }
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
        onQuietHours: (String?, String?) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            FocusStackTheme {
                SettingsScreen(
                    preferences = preferences,
                    onNudgeSelected = { _, _ -> },
                    onQuietHours = onQuietHours,
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
