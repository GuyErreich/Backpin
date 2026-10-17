// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.themeqa.awaitUntil
import app.backpin.focus.themeqa.labelColor
import app.backpin.focus.themeqa.tap
import app.backpin.focus.themeqa.themeQaEnv
import app.backpin.focus.ui.FocusApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * key(themeId) recreates the themed tree. Scroll and input reset is acceptable.
 * This test applies the new palette and records which ephemeral state reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeKeyResetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun keyThemeIdAppliesTheNewPaletteAndEphemeralStateResetIsAcceptable() {
        val env = themeQaEnv()
        val holder = ThemeChange()
        try {
            compose.setContent {
                var themeId by remember { mutableStateOf(ThemeId.SIGNAL) }
                holder.setTheme = { themeId = it }
                FocusStackTheme(themeId) {
                    FocusApp(
                        viewModel = env.viewModel,
                        themeId = themeId,
                        onThemeId = { themeId = it },
                    )
                }
            }
            compose.awaitUntil { env.viewModel.uiState.value.ready }
            compose.tap("Settings")
            compose.onNodeWithText("SETTINGS").assertExists()
            compose.onNode(hasSetTextAction()).performTextReplacement("Talk to Dana")
            compose.onNodeWithText("Show current task").performScrollTo()
            compose.onNodeWithText("Show current task").assertExists()

            holder.setTheme(ThemeId.EMBER)
            compose.waitForIdle()

            val ember = paletteFor(ThemeId.EMBER)
            assertEquals(ember.background, activePalette.background)
            val onHome = exists("FOCUS STACK")
            val onSettings = exists("SETTINGS")
            val phraseKept = exists("Talk to Dana")
            val scrolledRowKept = exists("Show current task")
            assertTrue(
                "a themed screen must remain after key(themeId)",
                onHome || onSettings,
            )
            if (onHome) {
                assertEquals(ember.textSecondary, compose.labelColor("FOCUS STACK"))
            }
            if (onSettings) {
                assertEquals(ember.textSecondary, compose.labelColor("SETTINGS"))
            }
            val report =
                buildString {
                    appendLine("key(themeId) ephemeral state after Signal -> Ember")
                    appendLine("navigation_reset_to_home=${onHome && !onSettings}")
                    appendLine("stayed_on_settings=$onSettings")
                    appendLine("typed_phrase_kept=$phraseKept")
                    appendLine("settings_scroll_row_kept=$scrolledRowKept")
                    appendLine("active_palette=${if (activePalette.background == ember.background) "EMBER" else "OTHER"}")
                }
            val artifacts = File("/opt/cursor/artifacts")
            val dest = if (artifacts.exists() || artifacts.mkdirs()) artifacts else File("app/build/outputs/theme-qa")
            dest.mkdirs()
            File(dest, "theme_key_resets.txt").writeText(report)
        } finally {
            env.close()
            activePalette = paletteFor(ThemeId.SIGNAL)
        }
    }

    private fun exists(text: String): Boolean = compose
        .onAllNodesWithText(text, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

    private class ThemeChange {
        var setTheme: (ThemeId) -> Unit = {}
    }
}
