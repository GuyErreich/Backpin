// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.theme.paletteFor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = SCREENSHOT_QUALIFIERS)
class CaptureSignalTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capturesAt412DpInSignalWithThePinnedClock() {
        val clock = FakeAppClock()
        val zoneLabel = clock.zone.id
        val dateLabel = clock.localDate().toString()
        compose.captureSignalComposable(
            fileName = "kit-signal-clock.png",
            clock = clock,
            ready = { hasText(zoneLabel) && hasText(dateLabel) },
        ) {
            Text("$zoneLabel $dateLabel")
        }
        val bitmap = recorded("kit-signal-clock.png")
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(SCREENSHOT_WIDTH_DP, (bitmap.width / density).toInt())
        assertEquals(SCREENSHOT_HEIGHT_DP, (bitmap.height / density).toInt())
        val midnight = paletteFor(ThemeId.SIGNAL).background.toArgb()
        assertEquals(midnight, bitmap.getPixel(0, 0))
        assertEquals(DEFAULT_TEST_ZONE, clock.zone)
        assertEquals("2026-10-16", dateLabel)
    }

    @Test
    fun waitsForRenderedStateBeforeCapture() {
        val clock = FakeAppClock()
        compose.captureSignalComposable(
            fileName = "kit-signal-clock-wait.png",
            clock = clock,
            ready = { hasText(clock.zone.id) && hasText("2026-10-16") },
        ) {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(clock.zone) { shown = true }
            if (shown) {
                Text("${clock.zone.id} ${clock.localDate()}")
            }
        }
        assertTrue(recorded("kit-signal-clock-wait.png").width > 0)
    }

    @Test
    fun honorsThePassedZoneOnTheCapture() {
        val rows =
            listOf(
                ClockShot(
                    name = "Auckland date crossing",
                    clock = FakeAppClock(DATE_CROSSING_INSTANT, ZoneId.of("Pacific/Auckland")),
                    fileName = "kit-signal-clock-auckland.png",
                    zoneId = "Pacific/Auckland",
                    date = "2026-10-16",
                    nyDate = "2026-10-15",
                ),
            )
        val row = rows.single()
        compose.captureSignalComposable(
            fileName = row.fileName,
            clock = row.clock,
            ready = { hasText(row.zoneId) && hasText(row.date) },
        ) {
            Text("${row.clock.zone.id} ${row.clock.localDate()}")
        }
        assertEquals(row.name, row.zoneId, row.clock.zone.id)
        assertEquals(row.name, row.date, row.clock.localDate().toString())
        assertTrue(row.name, row.clock.localDate().toString() != row.nyDate)
        assertTrue(row.name, recorded(row.fileName).width > 0)
    }

    private fun hasText(value: String): Boolean = compose.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun recorded(fileName: String): Bitmap {
        val output = File(screenshotPath(fileName))
        val baseline = File(screenshotBaselinePath(fileName))
        assertTrue("missing recorded screenshot $fileName", output.isFile)
        assertTrue("missing committed baseline $fileName", baseline.isFile)
        assertTrue(
            "test run must write under build/, not overwrite $SCREENSHOT_BASELINE_DIR",
            output.canonicalFile != baseline.canonicalFile,
        )
        assertArrayEquals(
            "recorded $fileName must match committed baseline",
            baseline.readBytes(),
            output.readBytes(),
        )
        return BitmapFactory.decodeFile(output.absolutePath)
            ?: error("undecodable screenshot $fileName")
    }

    private data class ClockShot(
        val name: String,
        val clock: FakeAppClock,
        val fileName: String,
        val zoneId: String,
        val date: String,
        val nyDate: String,
    )
}
