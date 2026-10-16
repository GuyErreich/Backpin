package app.backpin.focus.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BandTimeSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearExactAlarmPrompt() {
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("backpin_repeat_ui", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun defaultsShowAndTheExactAlarmPromptCanBeDismissed() {
        var allowed = false
        compose.setContent {
            FocusStackTheme {
                SettingsScreen(
                    preferences = UserPreferences(),
                    onNudgeSelected = { _, _ -> },
                    onQuietHours = { _, _ -> },
                    onCurrentNotification = {},
                    onTodayLimit = {},
                    onAddPhrase = {},
                    onRenamePhrase = { _, _ -> },
                    onRemovePhrase = {},
                    onBandTimes = {},
                    canScheduleExactAlarms = { allowed },
                )
            }
        }
        compose.onNodeWithText("Repeats").assertExists()
        compose.onNodeWithText("07:00").assertExists()
        compose.onNodeWithText("12:00").assertExists()
        compose.onNodeWithText("18:00").assertExists()
        compose.onNodeWithText("23:00").assertExists()
        compose.onNodeWithText("On-time reminders").assertExists()
        compose.onNodeWithText(
            "Repeat reminders arrive on time when exact alarms are allowed. If you skip this, they can still land a few minutes late. Home, capture, and check-off keep working either way.",
        ).assertExists()
        compose.onNodeWithText("Not now").performScrollTo()
        tap("Not now")
        compose.waitForIdle()
        compose.onNodeWithText("On-time reminders").assertDoesNotExist()
        compose.onNodeWithText("Allow exact alarms").assertDoesNotExist()
        assertEquals(false, allowed)
    }

    @Test
    fun customBandTimesShowAndThePromptStaysHiddenWhenExactAlarmsWork() {
        compose.setContent {
            FocusStackTheme {
                SettingsScreen(
                    preferences =
                        UserPreferences(
                            bandTimes = BandTimes(8 * 60, 13 * 60, 19 * 60, 22 * 60),
                        ),
                    onNudgeSelected = { _, _ -> },
                    onQuietHours = { _, _ -> },
                    onCurrentNotification = {},
                    onTodayLimit = {},
                    onAddPhrase = {},
                    onRenamePhrase = { _, _ -> },
                    onRemovePhrase = {},
                    onBandTimes = {},
                    canScheduleExactAlarms = { true },
                )
            }
        }
        compose.onNodeWithText("08:00").assertExists()
        compose.onNodeWithText("13:00").assertExists()
        compose.onNodeWithText("19:00").assertExists()
        compose.onNodeWithText("22:00").assertExists()
        compose.onNodeWithText("On-time reminders").assertDoesNotExist()
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }
}
