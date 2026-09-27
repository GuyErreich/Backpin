package app.backpin.focus.ui.capture

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureSheetActionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todayStartLaterAndSchedulePlaceTheTypedTitle() {
        val placed = mutableListOf<String>()
        show(frameActive = true, placed = placed)
        typeTitle("Call Dana")
        tap("Today")
        tap("Start")
        tap("Interrupt now")
        tap("Later")
        tap("Schedule")
        tap("Tomorrow")
        assertEquals(
            listOf(
                "today:Call Dana",
                "start:Call Dana",
                "interrupt:Call Dana",
                "later:Call Dana",
            ),
            placed.dropLast(1),
        )
        val scheduled = placed.last()
        assertTrue(scheduled.startsWith("schedule:Call Dana:"))
        assertTrue(scheduled.substringAfterLast(":").toLong() > LocalDate.now().toEpochDay())
    }

    @Test
    fun inboxStillSaves() {
        val placed = mutableListOf<String>()
        show(frameActive = false, placed = placed)
        typeTitle("Buy milk")
        tap("Save to Inbox")
        assertEquals(listOf("inbox:Buy milk"), placed)
    }

    @Test
    fun emptyTitleCreatesNothing() {
        val placed = mutableListOf<String>()
        show(frameActive = true, placed = placed)
        tap("Save to Inbox")
        tap("Today")
        tap("Start")
        tap("Interrupt now")
        tap("Later")
        tap("Schedule")
        assertTrue(placed.isEmpty())
    }

    @Test
    fun interruptIsAbsentWhenNothingIsActive() {
        val placed = mutableListOf<String>()
        show(frameActive = false, placed = placed)
        compose.onNodeWithText("Interrupt now").assertDoesNotExist()
        compose.onNodeWithText("Today").assertExists()
    }

    @Test
    fun speechResultLandsInTheTitleAndDoesNotSave() {
        val placed = mutableListOf<String>()
        val fake = FakeRecognizer()
        show(frameActive = false, placed = placed, recognizer = fake)
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performClick()
        compose.runOnIdle { fake.deliver("Buy milk") }
        compose.onNodeWithText("Buy milk").assertExists()
        assertTrue(placed.isEmpty())
        compose.onNodeWithContentDescription("Close capture").performClick()
        assertTrue(fake.cancelled)
    }

    @Test
    fun unavailableSpeechIsToldInTheSheet() {
        val placed = mutableListOf<String>()
        show(frameActive = false, placed = placed, recognizer = FakeRecognizer(available = false))
        compose.onNodeWithContentDescription("Speak the title").performClick()
        compose.onNodeWithText("Speech isn't available on this phone. You can still type.").assertExists()
        assertTrue(placed.isEmpty())
    }

    private fun show(
        frameActive: Boolean,
        placed: MutableList<String>,
        recognizer: UtteranceRecognizer? = AllowAllRecognizer,
    ) {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = frameActive,
                    onDismiss = {},
                    onSaveToInbox = { placed += "inbox:$it" },
                    onInterruptNow = { placed += "interrupt:$it" },
                    onPlaceToday = { placed += "today:$it" },
                    onPlaceStart = { placed += "start:$it" },
                    onPlaceLater = { placed += "later:$it" },
                    onSchedule = { title, day -> placed += "schedule:$title:$day" },
                    recognizer = recognizer,
                )
            }
        }
    }

    private fun typeTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextInput(title)
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
}

private class FakeRecognizer(
    private val available: Boolean = true,
) : UtteranceRecognizer {
    var cancelled: Boolean = false
    private var onFinished: ((String) -> Unit)? = null

    override fun available(): Boolean = available

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean {
        this.onFinished = onFinished
        return available
    }

    override fun cancel() {
        cancelled = true
    }

    fun deliver(phrase: String) {
        onFinished?.invoke(phrase)
    }
}

/** Keeps the sheet off the device recognizer. Clicks do not listen. */
private object AllowAllRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
