// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
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
        compose
            .onNodeWithText(
                "Nudges can arrive a few minutes late. Repeat reminders are on time when exact alarms are allowed.",
            ).assertExists()
        compose.onNodeWithText("Quiet from").assertExists()
        compose.onNodeWithText("Quiet until").assertExists()
        compose.onNodeWithText("A silent notice of what is open.").assertExists()
        compose.onNodeWithText("How many open tasks Today can hold", substring = true).assertDoesNotExist()
        compose.onNodeWithText("An ongoing notice while Today has an open task", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("End").assertDoesNotExist()
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
