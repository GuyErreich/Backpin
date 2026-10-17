// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.BandEdge
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BandTimeSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun defaultsShowAndTheExactAlarmPromptCanBeDismissed() {
        compose.setContent {
            var showPrompt by remember { mutableStateOf(true) }
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
                    bandTimes = BandTimes.DEFAULT,
                    onBandTime = { _, _ -> },
                    showExactAlarmPrompt = showPrompt,
                    onDismissExactAlarmPrompt = { showPrompt = false },
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
    }

    @Test
    fun customBandTimesShowAndThePromptStaysHiddenWhenExactAlarmsWork() {
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
                    bandTimes = BandTimes(8 * 60, 13 * 60, 19 * 60, 22 * 60),
                    onBandTime = { _, _ -> },
                    showExactAlarmPrompt = false,
                )
            }
        }
        compose.onNodeWithText("08:00").assertExists()
        compose.onNodeWithText("13:00").assertExists()
        compose.onNodeWithText("19:00").assertExists()
        compose.onNodeWithText("22:00").assertExists()
        compose.onNodeWithText("On-time reminders").assertDoesNotExist()
    }

    @Test
    fun savingABandTimeWritesThroughOnBandTime() {
        val writes = mutableListOf<Pair<BandEdge, Int>>()
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
                    bandTimes = BandTimes.DEFAULT,
                    onBandTime = { edge, minute -> writes += edge to minute },
                    showExactAlarmPrompt = false,
                )
            }
        }
        compose.onNodeWithText("07:00").performScrollTo()
        tap("07:00")
        compose.onNodeWithText("Morning starts").assertExists()
        tap("Save")
        compose.waitForIdle()
        assertEquals(listOf(BandEdge.MORNING to 7 * 60), writes.toList())
        compose.onNodeWithText("Morning starts").assertDoesNotExist()
    }

    @Test
    fun settingsReadsBandTimesAndSetBandTimePersists() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "band-times-${UUID.randomUUID()}"),
                clock = AppClock { System.currentTimeMillis() },
            )
        val repeats = RepeatsService(repository)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(FocusUseCases(repository), repeats) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            compose.setContent {
                val times by viewModel.bandTimes.collectAsState()
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
                        bandTimes = times,
                        onBandTime = viewModel::setBandTime,
                        showExactAlarmPrompt = false,
                    )
                }
            }
            compose.onNodeWithText("07:00").assertExists()
            viewModel.setBandTime(BandEdge.NOON, 13 * 60)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.bandTimes.value.noonStartMinute == 13 * 60
            }
            compose.onNodeWithText("13:00").assertExists()
            compose.onNodeWithText("12:00").assertDoesNotExist()
            assertEquals(13 * 60, viewModel.bandTimes.value.noonStartMinute)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }
}
