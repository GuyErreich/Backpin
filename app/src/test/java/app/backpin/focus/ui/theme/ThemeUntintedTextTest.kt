// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.themeqa.awaitUntil
import app.backpin.focus.themeqa.labelColor
import app.backpin.focus.themeqa.themeQaEnv
import app.backpin.focus.ui.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThemeUntintedTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun textWithoutAnExplicitColorUsesTheActiveThemeTextColorInEveryTheme() {
        ThemeId.entries.forEach { themeId ->
            val expected = paletteFor(themeId).textPrimary
            compose.setContent {
                FocusStackTheme(themeId) {
                    Text("Untinted")
                }
            }
            compose.waitForIdle()
            assertEquals(
                "Untinted text must use ${themeId.name} textPrimary",
                expected,
                compose.labelColor("Untinted"),
            )
        }
        val env = themeQaEnv()
        try {
            ThemeId.entries.forEach { themeId ->
                compose.setContent {
                    val state by env.viewModel.uiState.collectAsState()
                    FocusStackTheme(themeId) {
                        HomeScreen(
                            state = state,
                            viewModel = env.viewModel,
                            onOpenFocus = {},
                            onOpenInbox = {},
                            onOpenLater = {},
                            onOpenScheduled = {},
                            onOpenDone = {},
                        )
                    }
                }
                compose.awaitUntil { env.viewModel.uiState.value.ready }
                compose.onNodeWithText("Keep your place.").assertExists()
                assertEquals(
                    "Home headline must use ${themeId.name} textPrimary when it has no explicit color",
                    paletteFor(themeId).textPrimary,
                    compose.labelColor("Keep your place."),
                )
            }
        } finally {
            env.close()
            activePalette = paletteFor(ThemeId.SIGNAL)
        }
    }
}
