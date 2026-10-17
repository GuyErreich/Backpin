// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.FocusStackApp
import app.backpin.focus.MainActivity
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.themeqa.labelColor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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
    private val seenBackgrounds = CopyOnWriteArrayList<Color>()
    private val stopPoll = AtomicBoolean(false)
    private var poller: Thread? = null

    @get:Rule(order = 0)
    val seedStoredEmber =
        TestRule { base: Statement, _: Description ->
            object : Statement() {
                override fun evaluate() {
                    val app = ApplicationProvider.getApplicationContext<FocusStackApp>()
                    runBlocking { app.container.preferences.setThemeId(ThemeId.EMBER) }
                    activePalette = SENTINEL
                    seenBackgrounds.clear()
                    seenBackgrounds.add(activePalette.background)
                    stopPoll.set(false)
                    poller =
                        thread(name = "theme-first-frame", isDaemon = true) {
                            var last = activePalette.background
                            while (!stopPoll.get()) {
                                val current = activePalette.background
                                if (current != last) {
                                    seenBackgrounds.add(current)
                                    last = current
                                }
                                Thread.sleep(1)
                            }
                        }
                    try {
                        base.evaluate()
                    } finally {
                        stopPoll.set(true)
                        poller?.join(1_000)
                        activePalette = paletteFor(ThemeId.SIGNAL)
                    }
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

    @After
    fun restorePalette() {
        activePalette = paletteFor(ThemeId.SIGNAL)
    }

    @Test
    fun firstComposedFrameUsesTheStoredNonDefaultPalette() {
        val ember = paletteFor(ThemeId.EMBER)
        val signal = paletteFor(ThemeId.SIGNAL)
        val composed =
            seenBackgrounds.filter { it != SENTINEL.background }
        assertTrue(
            "MainActivity must compose a palette. seen=$seenBackgrounds",
            composed.isNotEmpty(),
        )
        assertEquals(
            "the first composed frame must already be Ember, not a Signal flash then a switch. seen=$seenBackgrounds",
            ember.background,
            composed.first(),
        )
        assertNotEquals(signal.background, composed.first())
        assertEquals(ember.background, activePalette.background)
        compose.onNodeWithText("FOCUS STACK").assertExists()
        assertEquals(ember.textSecondary, compose.labelColor("FOCUS STACK"))
    }

    private companion object {
        val SENTINEL =
            ThemePalette(
                background = Color(0xFF010203),
                surface = Color(0xFF040506),
                surfaceRaised = Color(0xFF070809),
                outline = Color(0xFF0A0B0C),
                textPrimary = Color(0xFF0D0E0F),
                textSecondary = Color(0xFF101112),
                accent = Color(0xFF131415),
                onAccent = Color(0xFF161718),
                now = Color(0xFF191A1B),
                missed = Color(0xFF1C1D1E),
                done = Color(0xFF1F2021),
                danger = Color(0xFF222324),
            )
    }
}
