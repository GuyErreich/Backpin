package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class CountdownTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun addCountdownSavesTheEventAndShowsItsDayCount() {
        withViewModel { viewModel, useCases, _ ->
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    CountdownScreen(
                        countdowns = state.countdowns,
                        ready = state.ready,
                        onAdd = viewModel::addCountdown,
                        onChangeDate = viewModel::changeCountdownDate,
                        onRemove = viewModel::removeCountdown,
                        recognizer = QuietRecognizer,
                    )
                }
            }
            await { viewModel.uiState.value.ready }
            compose
                .onNodeWithText("Nothing to count down to. Add an event and it will interrupt you on the day.")
                .assertExists()

            tap("Add countdown")
            compose.onNodeWithText("Save countdown").assertIsEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("Dana's birthday")
            tap("Pick a date")
            tap("OK")
            tap("Save countdown")
            val eventDay = LocalDate.now().plusDays(7).toEpochDay()
            awaitSnapshot(useCases) { snapshot ->
                val task = snapshot.tasks.singleOrNull { it.title == "Dana's birthday" }
                task != null &&
                    task.bucket == TaskBucket.COUNTDOWN &&
                    task.scheduledOnEpochDay == eventDay &&
                    snapshot.frames.isEmpty()
            }
            await { viewModel.uiState.value.countdowns.singleOrNull()?.dayLabel == "7 days" }
            compose.onNodeWithText("7 days").assertExists()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()

            tap("Change date")
            tap("OK")
            compose.onNodeWithText("7 days").assertExists()
            tap("Remove")
            awaitSnapshot(useCases) { snapshot -> snapshot.tasks.none { it.title == "Dana's birthday" } }
        }
    }

    @Test
    fun saveCountdownNamesWhatIsMissingUntilBothAreSet() {
        withViewModel { viewModel, useCases, _ ->
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    CountdownScreen(
                        countdowns = state.countdowns,
                        ready = state.ready,
                        onAdd = viewModel::addCountdown,
                        onChangeDate = viewModel::changeCountdownDate,
                        onRemove = viewModel::removeCountdown,
                        recognizer = QuietRecognizer,
                    )
                }
            }
            await { viewModel.uiState.value.ready }
            tap("Add countdown")

            tap("Save countdown")
            compose.onNodeWithText("Name the event.").assertExists()
            compose.onNodeWithText("Pick a date.").assertExists()
            compose.onNodeWithText("Save countdown").assertExists()
            assertEquals(0, taskCount(useCases))

            compose.onNode(hasSetTextAction()).performTextInput("Dana's birthday")
            compose.waitForIdle()
            compose.onNodeWithText("Name the event.").assertDoesNotExist()
            tap("Save countdown")
            compose.onNodeWithText("Name the event.").assertDoesNotExist()
            compose.onNodeWithText("Pick a date.").assertExists()
            assertEquals(0, taskCount(useCases))

            compose.onNode(hasSetTextAction()).performTextReplacement("")
            compose.waitForIdle()
            tap("Pick a date")
            tap("OK")
            compose.onNodeWithText("Pick a date.").assertDoesNotExist()
            tap("Save countdown")
            compose.onNodeWithText("Name the event.").assertExists()
            compose.onNodeWithText("Pick a date.").assertDoesNotExist()
            assertEquals(0, taskCount(useCases))

            compose.onNode(hasSetTextAction()).performTextInput("Dana's birthday")
            compose.waitForIdle()
            compose.onNodeWithText("Name the event.").assertDoesNotExist()
            tap("Save countdown")
            val eventDay = LocalDate.now().plusDays(7).toEpochDay()
            awaitSnapshot(useCases) { snapshot ->
                val task = snapshot.tasks.singleOrNull { it.title == "Dana's birthday" }
                task != null && task.bucket == TaskBucket.COUNTDOWN && task.scheduledOnEpochDay == eventDay
            }
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
        }
    }

    @Test
    fun eachActionAfterArrivalClosesTheEventAndLeavesTheParentWaiting() {
        withViewModel { viewModel, useCases, repository ->
            val today = LocalDate.now()
            runBlocking {
                useCases.capture("Write the report")
                val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
                useCases.promote(work.id)
                useCases.start(work.id)
                useCases.captureCountdown("Dentist", today.toEpochDay())
                repository.releaseDueCountdowns()
            }
            var screen by mutableStateOf("home")
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    when (screen) {
                        "home" ->
                            HomeScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenFocus = {},
                                onOpenInbox = {},
                                onOpenLater = {},
                                onOpenScheduled = {},
                                onOpenDone = {},
                            )
                        else -> FocusScreen(state = state, viewModel = viewModel)
                    }
                }
            }
            await { viewModel.uiState.value.now?.isEvent == true }
            compose.onNodeWithText("EVENT").assertExists()

            tap("Today")
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist" }
                event.bucket == TaskBucket.TODAY && event.status == TaskStatus.OPEN && parentWaits(snapshot)
            }
            await { viewModel.uiState.value.recovery?.title == "Write the report" }
            compose.onNodeWithText("Resume").assertExists()

            compose.runOnIdle { screen = "focus" }
            arriveAgain(useCases, repository, "Dentist again", today)
            await { viewModel.uiState.value.now?.title == "Dentist again" }
            compose.onNodeWithText("EVENT").assertExists()
            tap("Later")
            awaitSnapshot(useCases) { snapshot ->
                snapshot.tasks.single { it.title == "Dentist again" }.bucket == TaskBucket.LATER && parentWaits(snapshot)
            }

            arriveAgain(useCases, repository, "Anniversary", today)
            await { viewModel.uiState.value.now?.title == "Anniversary" }
            compose.onNodeWithText("EVENT").assertExists()
            tap("Count down again")
            tap("OK")
            val tomorrow = today.plusDays(1).toEpochDay()
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Anniversary" }
                event.bucket == TaskBucket.COUNTDOWN && event.scheduledOnEpochDay == tomorrow && parentWaits(snapshot)
            }
            await { viewModel.uiState.value.countdowns.any { it.title == "Anniversary" && it.dayLabel == "Tomorrow" } }

            arriveAgain(useCases, repository, "Checkup", today)
            await { viewModel.uiState.value.now?.title == "Checkup" }
            tap("Done")
            awaitSnapshot(useCases) { snapshot ->
                snapshot.tasks.single { it.title == "Checkup" }.status == TaskStatus.DONE && parentWaits(snapshot)
            }
            val parent =
                runBlocking {
                    useCases.observeSnapshot().first().let { snapshot ->
                        val id = snapshot.tasks.single { it.title == "Write the report" }.id
                        snapshot.frames.single { it.taskId == id }.state
                    }
                }
            assertEquals(FrameState.AWAITING_RESUME, parent)
        }
    }

    @Test
    fun todayOnAFullTodayOpensReplaceForTheEvent() {
        withViewModel { viewModel, useCases, repository ->
            runBlocking {
                repeat(3) { index ->
                    useCases.capture("Today ${index + 1}")
                    val id = useCases.observeSnapshot().first().tasks.single { it.title == "Today ${index + 1}" }.id
                    useCases.promote(id)
                }
                val first = useCases.observeSnapshot().first().tasks.single { it.title == "Today 1" }.id
                useCases.start(first)
                useCases.captureCountdown("Dentist", LocalDate.now().toEpochDay())
                repository.releaseDueCountdowns()
            }
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    FocusScreen(state = state, viewModel = viewModel)
                }
            }
            await { viewModel.uiState.value.now?.isEvent == true }
            tap("Today")
            await { viewModel.uiState.value.swapIncoming?.title == "Dentist" }
            awaitSnapshot(useCases) { snapshot ->
                snapshot.tasks.single { it.title == "Dentist" }.bucket == TaskBucket.INBOX && parentWaits(snapshot)
            }
            assertEquals(3, viewModel.uiState.value.swapChoices.size)
        }
    }

    private fun arriveAgain(
        useCases: FocusUseCases,
        repository: FocusRepositoryImpl,
        title: String,
        today: LocalDate,
    ) {
        runBlocking {
            useCases.captureCountdown(title, today.toEpochDay())
            repository.releaseDueCountdowns()
        }
    }

    private fun parentWaits(snapshot: FocusSnapshot): Boolean {
        val work = snapshot.tasks.firstOrNull { it.title == "Write the report" || it.title == "Today 1" } ?: return false
        val frame = snapshot.frames.firstOrNull { it.taskId == work.id } ?: return false
        return frame.state == FrameState.AWAITING_RESUME && snapshot.frames.none { it.state == FrameState.ACTIVE }
    }

    private fun withViewModel(block: (FocusStackViewModel, FocusUseCases, FocusRepositoryImpl) -> Unit) {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "countdown-tap-${UUID.randomUUID()}"),
                clock = AppClock { System.currentTimeMillis() },
            )
        val useCases = FocusUseCases(repository)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            block(viewModel, useCases, repository)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun taskCount(useCases: FocusUseCases): Int =
        runBlocking { useCases.observeSnapshot().first().tasks.size }

    private fun tap(label: String): SemanticsNodeInteraction {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        compose.waitForIdle()
        return node
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitSnapshot(
        useCases: FocusUseCases,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        await { runBlocking { predicate(useCases.observeSnapshot().first()) } }
    }
}

private object QuietRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
