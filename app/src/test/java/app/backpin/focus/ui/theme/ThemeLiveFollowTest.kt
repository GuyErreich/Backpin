// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.themeqa.allTextColors
import app.backpin.focus.themeqa.awaitUntil
import app.backpin.focus.themeqa.labelColor
import app.backpin.focus.themeqa.rgbEquals
import app.backpin.focus.themeqa.roleColors
import app.backpin.focus.themeqa.tap
import app.backpin.focus.themeqa.themeQaEnv
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A live theme switch must recolor screens that Compose would otherwise skip
 * because their parameters did not change. FocusStackTheme keys on themeId so
 * Home, Repeat, and Focus redraw even when their own arguments are stable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeLiveFollowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun changingThemeInSettingsRecolorsHomeRepeatAndFocusEvenWhenTheyWouldSkip() {
        val env = themeQaEnv()
        try {
            compose.setContent {
                var themeId by remember { mutableStateOf(ThemeId.SIGNAL) }
                val state by env.viewModel.uiState.collectAsState()
                val onOpen = remember { {} }
                FocusStackTheme(themeId) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HomeScreen(
                            state = state,
                            viewModel = env.viewModel,
                            onOpenFocus = onOpen,
                            onOpenInbox = onOpen,
                            onOpenLater = onOpen,
                            onOpenScheduled = onOpen,
                            onOpenDone = onOpen,
                        )
                        FocusScreen(state = state, viewModel = env.viewModel)
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
                            onThemeId = { themeId = it },
                            showHomeLayout = false,
                        )
                    }
                }
            }
            compose.awaitUntil { env.viewModel.uiState.value.ready }
            val signalMuted = paletteFor(ThemeId.SIGNAL).textSecondary
            val emberMuted = paletteFor(ThemeId.EMBER).textSecondary
            assertNotEquals(signalMuted, emberMuted)
            compose.onNodeWithText("FOCUS STACK").assertExists()
            compose.onNodeWithText("REPEAT").assertExists()
            compose.onNodeWithText("FOCUS").assertExists()
            assertEquals(signalMuted, compose.labelColor("FOCUS STACK"))
            assertEquals(signalMuted, compose.labelColor("REPEAT"))
            assertEquals(signalMuted, compose.labelColor("FOCUS"))
            compose.onNodeWithText("Signal").assertIsSelected()
            compose.tap("Ember")
            compose.waitForIdle()
            compose.onNodeWithText("Ember").assertIsSelected()
            assertEquals(
                "Home must recolor on a live theme switch even if HomeScreen would skip",
                emberMuted,
                compose.labelColor("FOCUS STACK"),
            )
            assertEquals(
                "Repeat must recolor on a live theme switch even if RepeatSection would skip",
                emberMuted,
                compose.labelColor("REPEAT"),
            )
            assertEquals(
                "Focus must recolor on a live theme switch even if FocusScreen would skip",
                emberMuted,
                compose.labelColor("FOCUS"),
            )
            val ember = paletteFor(ThemeId.EMBER)
            assertEquals(ember.textPrimary, compose.labelColor("Keep your place."))
            assertEquals(ember.textPrimary, compose.labelColor("Nothing is in focus."))
            val sampled = compose.allTextColors()
            assertTrue("Home, Repeat, and Focus must expose text to sample", sampled.isNotEmpty())
            val signalRoles = paletteFor(ThemeId.SIGNAL).roleColors()
            val leftovers =
                sampled.filter { (_, color) ->
                    signalRoles.any { signal -> color.rgbEquals(signal) } &&
                        ember.roleColors().none { role -> color.rgbEquals(role) }
                }
            assertTrue(
                "no Signal colors may remain on Home, Repeat, or Focus after a live switch: $leftovers",
                leftovers.isEmpty(),
            )
            assertEquals(ember.background, activePalette.background)
            assertEquals(ember.textPrimary, activePalette.textPrimary)
        } finally {
            env.close()
            activePalette = paletteFor(ThemeId.SIGNAL)
        }
    }
}
