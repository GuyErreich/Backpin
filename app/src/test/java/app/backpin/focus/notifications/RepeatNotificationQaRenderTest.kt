package app.backpin.focus.notifications

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class RepeatNotificationQaRenderTest {
    @Test
    fun writesSevenTwentyBySixteenHundredMorningFrames() {
        val dir = RepeatNotificationQaRender.outputDir()
        val bundled = File(dir, "repeats-morning-bundled-3.png")
        val afterDone = File(dir, "repeats-morning-after-done.png")

        val three =
            RepeatNotificationQaRender.render(
                openTitles = listOf("Take meds", "Make bed", "Vitamins"),
                caption = "Summary alerts. Each open repeat is a quiet child with its own Done.",
            )
        val two =
            RepeatNotificationQaRender.render(
                openTitles = listOf("Take meds", "Vitamins"),
                caption = "Make bed marked Done from the shade. Summary now says 2 things.",
            )
        try {
            assertEquals(RepeatNotificationQaRender.WIDTH, three.width)
            assertEquals(RepeatNotificationQaRender.HEIGHT, three.height)
            assertEquals(RepeatNotificationQaRender.WIDTH, two.width)
            assertEquals(RepeatNotificationQaRender.HEIGHT, two.height)
            RepeatNotificationQaRender.writePng(three, bundled)
            RepeatNotificationQaRender.writePng(two, afterDone)
        } finally {
            three.recycle()
            two.recycle()
        }

        assertTrue(bundled.length() > 8_000)
        assertTrue(afterDone.length() > 8_000)
        val bundledRead = BitmapFactory.decodeFile(bundled.absolutePath)
        val afterRead = BitmapFactory.decodeFile(afterDone.absolutePath)
        try {
            assertEquals(720, bundledRead.width)
            assertEquals(1600, bundledRead.height)
            assertEquals(720, afterRead.width)
            assertEquals(1600, afterRead.height)
        } finally {
            bundledRead.recycle()
            afterRead.recycle()
        }
    }
}
