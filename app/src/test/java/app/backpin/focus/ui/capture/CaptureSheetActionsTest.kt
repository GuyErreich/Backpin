// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.Manifest
import android.content.Intent
import android.graphics.Insets
import android.os.Build
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CaptureSheetActionsTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todayStartNowAndBringBackPlaceTheTypedTitle() {
        val placed = mutableListOf<String>()
        var dismissed = 0
        show(placed = placed, onDismiss = { dismissed += 1 })
        typeTitle("Call Dana")
        DESTINATIONS.forEach { case -> tap(case.label) }
        assertEquals(0, dismissed)
        assertEquals(DESTINATIONS.map { "${it.recorded}:Call Dana" }, placed)
    }

    @Test
    fun touchWritesTodayAndStartNowThroughTheViewModel() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                var dismissed = 0
                compose.setContent {
                    FocusStackTheme {
                        CaptureSheet(
                            phrases = emptyList(),
                            onDismiss = { dismissed += 1 },
                            onSaveToInbox = viewModel::saveToInbox,
                            onInterruptNow = viewModel::interruptNow,
                            onPlaceToday = viewModel::placeOnToday,
                            onPlaceStart = viewModel::placeAndStart,
                            onBringBack = viewModel::captureThenBringBack,
                            recognizer = AllowAllRecognizer,
                        )
                    }
                }
                typeTitle("Write the notes")
                tap("Today")
                awaitSnapshot(fixture.useCases, "Write the notes on Today") { snapshot ->
                    snapshot.tasks.any { it.title == "Write the notes" && it.bucket == TaskBucket.TODAY } &&
                        snapshot.frames.isEmpty()
                }
                assertEquals(0, dismissed)

                mainDispatcherRule.runTest {
                    val current =
                        fixture.useCases
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Write the notes" }
                            .id
                    fixture.useCases.start(current)
                }
                awaitNow(viewModel, "Write the notes is NOW") { it.title == "Write the notes" }

                replaceTitle("Call Dana")
                tap("Start now")
                awaitSnapshot(fixture.useCases, "Call Dana started") { snapshot ->
                    val started = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE }
                    started != null &&
                        started.parentFrameId == null &&
                        snapshot.tasks.any { it.title == "Call Dana" && it.id == started.taskId }
                }
                assertEquals(0, dismissed)
                awaitNow(viewModel, "Call Dana is NOW") { it.title == "Call Dana" }
            }
        }
    }

    @Test
    fun filledBlankAndDestinationsOnScreenEachTapRuns() {
        val placed = mutableListOf<String>()
        show(
            placed = placed,
            phrases = listOf("Talk to my wife", "Talk to <name>"),
        )
        compose.onNodeWithText("Talk to …").performTouchInput { click() }
        compose.onNodeWithText("name").performTextInput("GUY")
        compose.waitForIdle()
        compose.onNodeWithText("Talk to GUY").assertExists()
        compose.onNodeWithText("Next").assertExists()
        compose.onNodeWithText("Save to Inbox").assertExists()
        compose.onNodeWithText("OR, WHEN?").assertExists()
        compose.onNodeWithContentDescription("Speak the missing word").performTouchInput { click() }
        compose.onNodeWithText("Speech isn't available on this phone. You can still type.").assertExists()
        tap("Save to Inbox")
        DESTINATIONS.drop(1).forEach { case -> tap(case.label) }
        compose.onNodeWithText("Where does this go?").assertDoesNotExist()
        compose.onNodeWithText("One line. Choose where it goes…").assertDoesNotExist()
        assertEquals(
            listOf(
                "inbox:Talk to GUY",
                "today:Talk to GUY",
                "start:Talk to GUY",
                "bringBack:Talk to GUY",
            ),
            placed,
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h800dp")
    fun visibleCentersPlaceEachDestinationWhenTheKeyboardIsOpen() {
        val placed = mutableListOf<String>()
        show(
            placed = placed,
            phrases = listOf("Talk to my wife", "Talk to <name>"),
        )
        typeTitle("Call Dana")
        val dialog = ShadowDialog.getLatestDialog()
        check(dialog != null && Build.VERSION.SDK_INT >= 30)
        dialog.window!!.decorView.dispatchApplyWindowInsets(
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 48, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 620))
                .build(),
        )
        compose.waitForIdle()
        tapVisibleCenter("Save to Inbox")
        DESTINATIONS.drop(1).forEach { case -> tapVisibleCenter(case.label) }
        assertEquals(
            listOf(
                "inbox:Call Dana",
                "today:Call Dana",
                "start:Call Dana",
                "bringBack:Call Dana",
            ),
            placed,
        )
    }

    @Test
    fun createSheetShowsOrWhenAndDropsOldDestinations() {
        val placed = mutableListOf<String>()
        show(placed = placed)
        compose.onNodeWithText("OR, WHEN?").assertExists()
        compose.onNodeWithText("Save to Inbox").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Start now").assertExists()
        compose.onNodeWithText("Bring back…").assertExists()
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithText("Schedule").assertDoesNotExist()
        compose.onNodeWithText("Interrupt now").assertDoesNotExist()
        compose.onNodeWithText("Interrupt · do this now").assertDoesNotExist()
        compose.onNodeWithText("Repeat").assertDoesNotExist()
        compose.onNodeWithText("One line. Choose where it goes…").assertDoesNotExist()
        compose.onNodeWithText("Where does this go?").assertDoesNotExist()
        compose.onNodeWithText("Start").assertDoesNotExist()
    }

    @Test
    fun inboxStillSaves() {
        val placed = mutableListOf<String>()
        show(placed = placed)
        typeTitle("Buy milk")
        tap("Save to Inbox")
        assertEquals(listOf("inbox:Buy milk"), placed)
    }

    @Test
    fun emptyTitleCreatesNothing() {
        val placed = mutableListOf<String>()
        var dismissed = 0
        show(placed = placed, onDismiss = { dismissed += 1 })
        tap("Save to Inbox")
        DESTINATIONS.drop(1).forEach { case -> tap(case.label) }
        assertTrue(placed.isEmpty())
        assertEquals(0, dismissed)
        compose.onNodeWithText("Save to Inbox").assertExists()
    }

    @Test
    fun interruptModeShowsDoThisNowAndHidesOrWhen() {
        val placed = mutableListOf<String>()
        show(
            placed = placed,
            interrupts = true,
            pausedTitle = "Hold this",
        )
        compose.onNodeWithText("What came up?").assertExists()
        compose.onNodeWithText("“Hold this” is paused and waits at the top.").assertExists()
        compose.onNodeWithText("Interrupt · do this now").assertExists()
        compose.onNodeWithText("OR, WHEN?").assertDoesNotExist()
        compose.onNodeWithText("Today").assertDoesNotExist()
        compose.onNodeWithText("Start now").assertDoesNotExist()
        compose.onNodeWithText("Bring back…").assertDoesNotExist()
        compose.onNodeWithText("Save to Inbox").assertDoesNotExist()
        compose.onNodeWithText("Interrupt now").assertDoesNotExist()
        typeTitle("Quick note")
        tap("Interrupt · do this now")
        assertEquals(listOf("interrupt:Quick note"), placed)
    }

    @Test
    fun speechResultLandsInTheTitleAndDoesNotSave() {
        val placed = mutableListOf<String>()
        val fake = FakeRecognizer()
        show(placed = placed, recognizer = fake)
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performClick()
        compose.runOnIdle { fake.deliver("Buy milk") }
        compose.onNodeWithText("Buy milk").assertExists()
        assertTrue(placed.isEmpty())
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        assertTrue(fake.cancelled)
    }

    @Test
    fun unavailableSpeechIsToldInTheSheet() {
        val placed = mutableListOf<String>()
        show(placed = placed, recognizer = FakeRecognizer(available = false))
        compose.onNodeWithContentDescription("Speak the title").performClick()
        compose.onNodeWithText("Speech isn't available on this phone. You can still type.").assertExists()
        assertTrue(placed.isEmpty())
    }

    @Test
    fun failedRecognizerFallsBackIntoTheTitleAndDoesNotSave() {
        val placed = mutableListOf<String>()
        var launched: Intent? = null
        show(
            placed = placed,
            recognizer = ProblemRecognizer(SpeechProblem.NoNetwork),
            onSystemSpeech = { intent, deliver ->
                launched = intent
                deliver("Buy milk")
            },
        )
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performTouchInput { click() }
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, launched?.action)
        assertFalse(launched?.hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE) == true)
        compose.onNodeWithText("Buy milk").assertExists()
        assertTrue(placed.isEmpty())
    }

    @Test
    fun unavailableRecognizerFallsBackIntoTheTitleAndDoesNotSave() {
        val placed = mutableListOf<String>()
        var launched: Intent? = null
        show(
            placed = placed,
            recognizer = ProblemRecognizer(SpeechProblem.NoService, available = false),
            onSystemSpeech = { intent, deliver ->
                launched = intent
                deliver("Call Dana")
            },
        )
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performTouchInput { click() }
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, launched?.action)
        compose.onNodeWithText("Call Dana").assertExists()
        assertTrue(placed.isEmpty())
    }

    @Test
    fun deniedSpeechStaysInTheSheet() {
        assertProblemStaysInTheSheet(
            SpeechProblem.Denied,
            "Microphone permission is off. You can still type.",
        )
    }

    @Test
    fun missedUtteranceStaysInTheSheet() {
        assertProblemStaysInTheSheet(
            SpeechProblem.Unheard,
            "Didn't catch that. You can still type.",
        )
    }

    @Test
    fun networkFailureWithoutASystemScreenSaysWhy() {
        val placed = mutableListOf<String>()
        show(placed = placed, recognizer = ProblemRecognizer(SpeechProblem.NoNetwork))
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performTouchInput { click() }
        compose.onNodeWithText("No network for speech. You can still type.").assertExists()
        assertTrue(placed.isEmpty())
    }

    private fun assertProblemStaysInTheSheet(
        problem: SpeechProblem,
        note: String,
    ) {
        var launches = 0
        show(
            placed = mutableListOf(),
            recognizer = ProblemRecognizer(problem),
            onSystemSpeech = { _, _ -> launches += 1 },
        )
        shadowOf(compose.activity).grantPermissions(Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Speak the title").performTouchInput { click() }
        assertEquals(0, launches)
        compose.onNodeWithText(note).assertExists()
    }

    private fun show(
        placed: MutableList<String>,
        recognizer: UtteranceRecognizer? = AllowAllRecognizer,
        phrases: List<String> = emptyList(),
        onDismiss: () -> Unit = {},
        onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
        interrupts: Boolean = false,
        pausedTitle: String? = null,
    ) {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = phrases,
                    onDismiss = onDismiss,
                    onSaveToInbox = { placed += "inbox:$it" },
                    onInterruptNow = { placed += "interrupt:$it" },
                    onPlaceToday = { placed += "today:$it" },
                    onPlaceStart = { placed += "start:$it" },
                    onBringBack = { placed += "bringBack:$it" },
                    interrupts = interrupts,
                    pausedTitle = pausedTitle,
                    recognizer = recognizer,
                    onSystemSpeech = onSystemSpeech,
                )
            }
        }
    }

    private fun typeTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextInput(title)
    }

    private fun replaceTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(title)
    }

    private fun tap(label: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        return node
    }

    /**
     * Touch the button's visible center. Scroll a clipped button into the sheet
     * a short step at a time. A zero-height target has nothing to hit.
     */
    private fun tapVisibleCenter(label: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(label)
        repeat(8) {
            val raw = node.fetchSemanticsNode()
            val bounds = raw.boundsInRoot
            if (bounds.height >= 48f && bounds.top >= 0f) return@repeat
            val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val buttonTop = raw.positionInRoot.y
            val buttonBottom = buttonTop + raw.size.height
            val delta =
                when {
                    buttonTop < viewport.top -> buttonTop - viewport.top
                    buttonBottom > viewport.bottom -> buttonBottom - viewport.bottom
                    else -> 0f
                }
            if (delta == 0f) return@repeat
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                action(0f, delta)
            }
            compose.waitForIdle()
        }
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        return node
    }

    private fun awaitSnapshot(
        useCases: FocusUseCases,
        step: String,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        compose.waitUntil(conditionDescription = step, timeoutMillis = 5_000) {
            pump()
            kotlinx.coroutines.runBlocking { predicate(useCases.observeSnapshot().first()) }
        }
    }

    private fun awaitNow(
        viewModel: FocusStackViewModel,
        step: String,
        predicate: (app.backpin.focus.ui.NowUi) -> Boolean,
    ) {
        compose.waitUntil(conditionDescription = step, timeoutMillis = 5_000) {
            pump()
            viewModel.uiState.value.now?.let(predicate) == true
        }
    }

    private fun pump() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }

    private companion object {
        val DESTINATIONS =
            listOf(
                Destination("Save to Inbox", "inbox"),
                Destination("Today", "today"),
                Destination("Start now", "start"),
                Destination("Bring back…", "bringBack"),
            )
    }

    private data class Destination(
        val label: String,
        val recorded: String,
    )
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

private class ProblemRecognizer(
    private val problem: SpeechProblem,
    private val available: Boolean = true,
) : UtteranceRecognizer {
    override fun available(): Boolean = available

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean {
        if (!available) return false
        onProblem(problem)
        return true
    }

    override fun cancel() = Unit
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
