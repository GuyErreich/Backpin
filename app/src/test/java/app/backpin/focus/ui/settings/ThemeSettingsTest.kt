// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.backpin.focus.BuildConfig
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.runOffTestThread
import app.backpin.focus.themeqa.awaitUntil
import app.backpin.focus.themeqa.tap
import app.backpin.focus.themeqa.themeQaEnv
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeSettingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun themeRowListsExactlyTheFourThemesShowsTheCurrentOneAndPersistsASelection() {
        val env = themeQaEnv()
        try {
            compose.setContent {
                val themeId by env.preferences.themeId.collectAsState(initial = ThemeId.SIGNAL)
                val scope = rememberCoroutineScope()
                FocusStackTheme(themeId) {
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
                        themeId = themeId,
                        onThemeId = { id -> scope.launch { env.preferences.setThemeId(id) } },
                        showHomeLayout = false,
                    )
                }
            }
            compose.onNodeWithText("Theme").assertExists()
            val labels = listOf("Signal", "Still Water", "Ember", "Clear Line")
            labels.forEach { label ->
                compose.onNodeWithText(label).assertExists()
                compose.onAllNodesWithText(label).assertCountEquals(1)
            }
            compose.onNodeWithText("Signal").assertIsSelected()
            labels.drop(1).forEach { label ->
                compose.onNodeWithText(label).assertIsNotSelected()
            }
            compose.tap("Clear Line")
            compose.awaitUntil {
                runOffTestThread { env.preferences.themeId.first() } == ThemeId.CLEAR_LINE
            }
            compose.onNodeWithText("Clear Line").assertIsSelected()
            compose.onNodeWithText("Signal").assertIsNotSelected()
            assertEquals(ThemeId.CLEAR_LINE, runOffTestThread { env.preferences.themeId.first() })
        } finally {
            env.close()
        }
    }

    @Test
    fun homeLayoutSwitchIsShownOnlyInDebugOrPreviewAndHiddenForRelease() {
        val shownInThisBuild = BuildConfig.DEBUG || BuildConfig.BUILD_TYPE == "preview"
        assertTrue(
            "debug unit tests must be a debug or preview config so the Home layout switch is on",
            shownInThisBuild,
        )
        val showLayout = mutableStateOf(false)
        compose.setContent {
            val showHomeLayout by showLayout
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
                    homeLayout = HomeLayout.FLAT,
                    showHomeLayout = showHomeLayout,
                )
            }
        }
        compose.onNodeWithText("Theme").assertExists()
        compose.onNodeWithText("Home layout").assertDoesNotExist()
        compose.onNodeWithText("Flat").assertDoesNotExist()
        compose.onNodeWithText("Bands").assertDoesNotExist()
        showLayout.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Home layout").performScrollTo()
        compose.onNodeWithText("Home layout").assertExists()
        compose.onNodeWithText("Flat").assertExists()
        compose.onNodeWithText("Bands").assertExists()
        compose.onNodeWithText("Flat").assertIsSelected()
        compose.onNodeWithText("Bands").assertIsNotSelected()
    }

    @Test
    fun selectingBandsPersistsAndShowsItSelected() {
        val env = themeQaEnv()
        try {
            compose.setContent {
                var homeLayout by remember { mutableStateOf(HomeLayout.FLAT) }
                val scope = rememberCoroutineScope()
                FocusStackTheme {
                    FocusApp(
                        viewModel = env.viewModel,
                        homeLayout = homeLayout,
                        onHomeLayout = { layout ->
                            homeLayout = layout
                            scope.launch { env.preferences.setHomeLayout(layout) }
                        },
                        showHomeLayout = true,
                    )
                }
            }
            compose.awaitUntil { env.viewModel.uiState.value.ready }
            compose.tap("Settings")
            compose.onNodeWithText("Home layout").performScrollTo()
            compose.onNodeWithText("Bands").performScrollTo()
            compose.tap("Bands")
            compose.awaitUntil {
                runOffTestThread { env.preferences.homeLayout.first() } == HomeLayout.BANDS
            }
            assertEquals(HomeLayout.BANDS, runOffTestThread { env.preferences.homeLayout.first() })
            compose.onNodeWithText("Bands").assertIsSelected()
            compose.onNodeWithText("Flat").assertIsNotSelected()
        } finally {
            env.close()
        }
    }
}
