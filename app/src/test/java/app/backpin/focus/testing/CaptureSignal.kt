// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.TimeZone

/** Phone width for kit screenshots. */
const val SCREENSHOT_WIDTH_DP = 412

/** Phone height paired with [SCREENSHOT_WIDTH_DP]. */
const val SCREENSHOT_HEIGHT_DP = 891

/** Robolectric qualifiers for a 412×891 dp xxhdpi frame. */
const val SCREENSHOT_QUALIFIERS = "w412dp-h891dp-xxhdpi"

/** Directory for recorded kit screenshots, relative to the `:app` module. */
const val SCREENSHOT_DIR = "src/test/screenshots"

/**
 * Sets [content] in the Signal theme at [SCREENSHOT_WIDTH_DP] dp, with a
 * pinned [FakeAppClock] and zone. Waits until [ready] matches rendered
 * state, then captures. Call once per test: [ComposeContentTestRule.setContent]
 * cannot run twice on the same activity. No real clock and no sleeps.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun ComposeContentTestRule.captureSignalComposable(
    fileName: String,
    clock: FakeAppClock = FakeAppClock(),
    ready: () -> Boolean = { true },
    content: @Composable () -> Unit,
) {
    val previousZone = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(clock.zone.id))
    RuntimeEnvironment.setQualifiers(SCREENSHOT_QUALIFIERS)
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resolver = context.contentResolver
    val previousAnimator =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val previousTransition =
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
    try {
        setContent {
            FocusStackTheme(themeId = ThemeId.SIGNAL) {
                Surface(
                    color = FocusColors.Live.Midnight,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content()
                }
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            waitForIdle()
            ready()
        }
        captureScreenRoboImage(screenshotPath(fileName))
    } finally {
        TimeZone.setDefault(previousZone)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousAnimator)
        Settings.Global.putFloat(
            resolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            previousTransition,
        )
    }
}

/** Absolute path for a screenshot [fileName] under [SCREENSHOT_DIR]. */
fun screenshotPath(fileName: String): String {
    val dir = File(SCREENSHOT_DIR)
    dir.mkdirs()
    return File(dir, fileName).absolutePath
}
