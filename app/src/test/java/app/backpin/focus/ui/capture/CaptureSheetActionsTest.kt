package app.backpin.focus.ui.capture

import android.Manifest
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CaptureSheetActionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun todayStartLaterAndSchedulePlaceTheTypedTitle() {
        val placed = mutableListOf<String>()
        var dismissed = 0
        show(frameActive = true, placed = placed, onDismiss = { dismissed += 1 })
        typeTitle("Call Dana")
        tap("Today")
        tap("Start")
        tap("Interrupt now")
        tap("Later")
        tap("Schedule")
        tap("Tomorrow")
        assertEquals(0, dismissed)
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
    fun touchWritesEachDestinationThroughTheViewModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "capture-touch-${UUID.randomUUID()}"),
                    clock =
                        object : AppClock {
                            override fun nowEpochMillis(): Long = 5_000L
                        },
                ),
            )
        val viewModel = FocusStackViewModel(useCases)
        var dismissed = 0
        try {
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    CaptureSheet(
                        phrases = emptyList(),
                        frameActive = state.now != null,
                        onDismiss = { dismissed += 1 },
                        onSaveToInbox = viewModel::saveToInbox,
                        onInterruptNow = viewModel::interruptNow,
                        onPlaceToday = viewModel::placeOnToday,
                        onPlaceStart = viewModel::placeAndStart,
                        onPlaceLater = viewModel::placeLater,
                        onSchedule = viewModel::placeScheduled,
                        recognizer = AllowAllRecognizer,
                    )
                }
            }
            typeTitle("Write the notes")
            tap("Today")
            awaitSnapshot(useCases) { snapshot ->
                snapshot.tasks.any { it.title == "Write the notes" && it.bucket == TaskBucket.TODAY } &&
                    snapshot.frames.isEmpty()
            }
            assertEquals(0, dismissed)

            val current = runBlocking { useCases.observeSnapshot().first().tasks.single { it.title == "Write the notes" }.id }
            runBlocking { useCases.start(current) }
            awaitNow(viewModel) { it.taskId == current }

            replaceTitle("Call Dana")
            tap("Start")
            awaitSnapshot(useCases) { snapshot ->
                val started = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE }
                started != null &&
                    started.parentFrameId == null &&
                    snapshot.tasks.any { it.title == "Call Dana" && it.id == started.taskId }
            }
            assertEquals(0, dismissed)
            awaitNow(viewModel) { it.title == "Call Dana" }

            replaceTitle("Ping the vendor")
            tap("Interrupt now")
            awaitSnapshot(useCases) { snapshot ->
                val child = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE }
                val danaId = snapshot.tasks.singleOrNull { it.title == "Call Dana" }?.id
                val parent = snapshot.frames.singleOrNull { it.taskId == danaId }
                child != null &&
                    parent != null &&
                    child.parentFrameId == parent.id &&
                    parent.state == FrameState.PAUSED &&
                    snapshot.tasks.any { it.title == "Ping the vendor" && it.bucket == TaskBucket.INBOX }
            }
            assertEquals(0, dismissed)

            replaceTitle("Call the vendor")
            tap("Later")
            awaitSnapshot(useCases) { snapshot ->
                val parked = snapshot.tasks.singleOrNull { it.title == "Call the vendor" }
                parked != null && parked.bucket == TaskBucket.LATER && snapshot.frames.none { it.taskId == parked.id }
            }
            assertEquals(0, dismissed)

            replaceTitle("Talk to Dana")
            tap("Schedule")
            tap("Tomorrow")
            val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
            awaitSnapshot(useCases) { snapshot ->
                val scheduled = snapshot.tasks.singleOrNull { it.title == "Talk to Dana" }
                scheduled != null &&
                    scheduled.bucket == TaskBucket.SCHEDULED &&
                    scheduled.scheduledOnEpochDay == tomorrow &&
                    snapshot.frames.none { it.taskId == scheduled.id }
            }
            assertEquals(0, dismissed)
        } finally {
            database.close()
        }
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
        var dismissed = 0
        show(frameActive = true, placed = placed, onDismiss = { dismissed += 1 })
        tap("Save to Inbox")
        tap("Today")
        tap("Start")
        tap("Interrupt now")
        tap("Later")
        tap("Schedule")
        assertTrue(placed.isEmpty())
        assertEquals(0, dismissed)
        compose.onNodeWithText("Save to Inbox").assertExists()
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
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = frameActive,
                    onDismiss = onDismiss,
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

    private fun replaceTitle(title: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(title)
    }

    private fun tap(label: String): SemanticsNodeInteraction {
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        // Pointer input, not the semantics click action. performClick can pass
        // when a finger never reaches the button.
        node.performTouchInput { click() }
        return node
    }

    private fun awaitSnapshot(
        useCases: FocusUseCases,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { predicate(useCases.observeSnapshot().first()) }
        }
    }

    private fun awaitNow(
        viewModel: FocusStackViewModel,
        predicate: (app.backpin.focus.ui.NowUi) -> Boolean,
    ) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.uiState.value.now?.let(predicate) == true
        }
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
