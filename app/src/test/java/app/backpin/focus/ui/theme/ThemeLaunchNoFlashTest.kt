// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import android.os.Looper
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.FocusStackApp
import app.backpin.focus.MainActivity
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.themeqa.labelColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * MainActivity must read the stored theme before the first setContent so the
 * first composed frame is already that palette, not Signal then a switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [36],
    application = FocusStackApp::class,
    qualifiers = "w411dp-h891dp",
)
class ThemeLaunchNoFlashTest {
    @get:Rule(order = 0)
    val seedStoredEmber =
        TestRule { base: Statement, _: Description ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<FocusStackApp>()
                    runBlocking { app.container.preferences.setThemeId(ThemeId.EMBER) }
                    base.evaluate()
                }
            }
        }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun idleUntilHome() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText("FOCUS STACK").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    @Test
    fun firstComposedFrameUsesTheStoredNonDefaultPalette() {
        val ember = paletteFor(ThemeId.EMBER)
        val signal = paletteFor(ThemeId.SIGNAL)
        compose.onNodeWithText("FOCUS STACK").assertExists()
        val homeChrome = compose.labelColor("FOCUS STACK")
        assertEquals(
            "the first composed Home chrome must already be Ember, not Signal",
            ember.textSecondary,
            homeChrome,
        )
        assertNotEquals(signal.textSecondary, homeChrome)
    }
}
