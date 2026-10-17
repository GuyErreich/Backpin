// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Switching theme from Settings must keep the Settings route, typed text,
 * and scroll position, and apply Ember in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeKeyResetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switchingThemeKeepsSettingsScrollAndTypedTextAndAppliesEmber() {
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
            compose.onNodeWithText("SETTINGS").assertIsDisplayed()

            compose.onNode(hasSetTextAction()).performTextReplacement("Talk to Dana")
            compose.waitForIdle()
            compose.onNode(hasSetTextAction() and hasText("Talk to Dana")).assertExists()

            compose.onNodeWithText("Show current task").performScrollTo()
            compose.waitForIdle()
            compose.onNodeWithText("Show current task").assertIsDisplayed()
            val scrollTop = compose.onNodeWithText("Show current task").fetchSemanticsNode().boundsInRoot.top

            holder.setTheme(ThemeId.EMBER)
            compose.waitForIdle()

            compose.onNodeWithText("SETTINGS").assertExists()
            compose.onNodeWithText("FOCUS STACK").assertDoesNotExist()
            compose.onNode(hasSetTextAction() and hasText("Talk to Dana")).assertExists()
            compose.onNodeWithText("Show current task").assertIsDisplayed()
            assertEquals(
                "Settings scroll must stay put across a live theme switch",
                scrollTop,
                compose.onNodeWithText("Show current task").fetchSemanticsNode().boundsInRoot.top,
                1f,
            )
            assertEquals(
                paletteFor(ThemeId.EMBER).textSecondary,
                compose.labelColor("SETTINGS"),
            )
        } finally {
            env.close()
        }
    }

    private class ThemeChange {
        var setTheme: (ThemeId) -> Unit = {}
    }
}
