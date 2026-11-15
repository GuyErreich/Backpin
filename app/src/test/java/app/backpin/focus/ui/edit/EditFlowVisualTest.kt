// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.capture.CaptureEdit
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.home.InboxScreen
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class EditFlowVisualTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inboxTitleLeadsAndChipRowIsGone() {
        showInbox("Call the bank about card", "Buy printer ink")
        assertEquals(22.sp, fontSizeOf("Call the bank about card"))
        assertEquals(22.sp, fontSizeOf("Buy printer ink"))
        listOf("Today", "Start", "Interrupt", "Edit").forEach { label ->
            assertTrue(
                "$label chip must not appear on the resting card",
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun writeApprovedFrameScreenshots() {
        var first by mutableStateOf("Call the bank about card")
        var editTitle by mutableStateOf<String?>(null)
        compose.setContent {
            FocusStackTheme {
                Surface(color = FocusColors.Midnight, modifier = Modifier.fillMaxSize()) {
                    InboxScreen(
                        tasks =
                            listOf(
                                TaskUi(id = "1", title = first, returnCue = null),
                                TaskUi(id = "2", title = "Buy printer ink", returnCue = null),
                            ),
                        ready = true,
                        canInterrupt = false,
                        onBack = {},
                        onToday = {},
                        onStart = {},
                        onInterrupt = {},
                        onEdit = {},
                    )
                    editTitle?.let { title ->
                        CaptureSheet(
                            phrases = emptyList(),
                            frameActive = false, today = app.backpin.focus.testing.FakeAppClock().localDate(),
                            onDismiss = {},
                            onSaveToInbox = {},
                            onInterruptNow = {},
                            onPlaceToday = {},
                            onPlaceStart = {},
                            onPlaceLater = {},
                            onSchedule = { _, _, _ -> },
                            edit =
                                CaptureEdit(
                                    title = title,
                                    place = CapturePlace.INBOX,
                                ),
                            onCommitEdit = { _, _, _ -> },
                            recognizer = SilentRecognizer,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        savePng("04-inbox-before-edit.png", fromDialog = false)

        compose.runOnIdle { editTitle = "Call the bank about the card" }
        compose.waitForIdle()
        savePng("05-edit-task-sheet.png", fromDialog = true)

        compose.runOnIdle {
            editTitle = null
            first = "Call the bank about the card"
        }
        compose.waitForIdle()
        savePng("06-inbox-after-edit.png", fromDialog = false)
    }

    private fun showInbox(
        first: String,
        second: String,
    ) {
        compose.setContent {
            FocusStackTheme {
                Surface(color = FocusColors.Midnight, modifier = Modifier.fillMaxSize()) {
                    InboxScreen(
                        tasks =
                            listOf(
                                TaskUi(id = "1", title = first, returnCue = null),
                                TaskUi(id = "2", title = second, returnCue = null),
                            ),
                        ready = true,
                        canInterrupt = false,
                        onBack = {},
                        onToday = {},
                        onStart = {},
                        onInterrupt = {},
                        onEdit = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun savePng(
        name: String,
        fromDialog: Boolean,
    ) {
        compose.waitForIdle()
        val view =
            if (fromDialog) {
                val dialog = ShadowDialog.getLatestDialog()
                dialog.window?.decorView ?: compose.activity.window.decorView
            } else {
                compose.activity.window.decorView
            }
        val width = 720
        val height = 1600
        if (view.width <= 0 || view.height <= 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, width, height)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaleX = width.toFloat() / view.width.coerceAtLeast(1)
        val scaleY = height.toFloat() / view.height.coerceAtLeast(1)
        canvas.scale(scaleX, scaleY)
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        try {
            view.draw(canvas)
        } catch (_: NullPointerException) {
            compose.activity.window.decorView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            compose.activity.window.decorView.draw(canvas)
        }
        screenshotDir().let { dir ->
            dir.mkdirs()
            File(dir, name).outputStream().use { out ->
                assertTrue("PNG $name did not compress", bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
        }
    }

    private fun screenshotDir(): File {
        val override = System.getenv("BACKPIN_SCREENSHOT_DIR")
        if (!override.isNullOrBlank()) {
            return File(override).apply { mkdirs() }
        }
        val artifacts = File("/opt/cursor/artifacts")
        if (artifacts.isDirectory && artifacts.canWrite()) return artifacts
        return File("app/build/outputs/screenshots").apply { mkdirs() }
    }

    private fun fontSizeOf(text: String) = textLayout(text).layoutInput.style.fontSize

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single()
    }

    private object SilentRecognizer : UtteranceRecognizer {
        override fun available(): Boolean = false

        override fun listen(
            onPartial: (String) -> Unit,
            onFinished: (String) -> Unit,
            onProblem: (SpeechProblem) -> Unit,
        ): Boolean = false

        override fun cancel() = Unit
    }
}
