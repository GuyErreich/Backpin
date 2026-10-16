package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.runOffTestThread
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.scheduleMomentLabel
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
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CountdownTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun enteringTwentyFiveDaysWithANameSavesOneCountdownAndCloses() {
        withViewModel { viewModel, useCases, _, _ ->
            showCountdown(viewModel)
            await { viewModel.uiState.value.ready }
            tap("Add countdown")
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            nameField().performTextInput("Dana's birthday")
            daysField().performTextInput("25")
            val eventDay = LocalDate.now().plusDays(25).toEpochDay()
            compose.onNodeWithText(scheduleMomentLabel(eventDay, null)).assertExists()
            daysField().performTouchInput { click() }
            daysField().performImeAction()
            val created = awaitCountdown(useCases, "Dana's birthday", eventDay)
            assertEquals(1, taskCount(useCases))
            compose.onNodeWithText("Count down to").assertDoesNotExist()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            await { viewModel.uiState.value.countdowns.singleOrNull()?.dayLabel == "25 days" }
            compose.onNodeWithText("25 days").assertExists()

            tap("Change date")
            compose.onNode(hasSetTextAction()).performTextInput("3")
            compose.onNode(hasSetTextAction()).performTouchInput { click() }
            compose.onNode(hasSetTextAction()).performImeAction()
            val moved = awaitCountdown(useCases, "Dana's birthday", LocalDate.now().plusDays(3).toEpochDay())
            assertEquals(created.id, moved.id)
            await { viewModel.uiState.value.countdowns.singleOrNull()?.dayLabel == "3 days" }
            compose.onNodeWithText("Change the day").assertDoesNotExist()
            compose.onNodeWithText("3 days", useUnmergedTree = true).assertExists()

            tap("Change date")
            tap("Pick a date")
            tapCalendarDay(LocalDate.now().plusDays(4))
            tap("OK")
            val picked = awaitCountdown(useCases, "Dana's birthday", LocalDate.now().plusDays(4).toEpochDay())
            assertEquals(created.id, picked.id)
            await { viewModel.uiState.value.countdowns.singleOrNull()?.dayLabel == "4 days" }
            compose.onNodeWithText("4 days", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
        }
    }

    @Test
    fun pickingACalendarDayWithANameSavesOneCountdownAndCloses() {
        withViewModel { viewModel, useCases, _, _ ->
            showCountdown(viewModel)
            await { viewModel.uiState.value.ready }
            tap("Add countdown")
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            nameField().performTextInput("Dana's birthday")
            tap("Pick a date")
            tap("OK")
            val eventDay = LocalDate.now().plusDays(7).toEpochDay()
            awaitCountdown(useCases, "Dana's birthday", eventDay)
            assertEquals(1, taskCount(useCases))
            compose.onNodeWithText("Count down to").assertDoesNotExist()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            compose.onNodeWithText("7 days").assertExists()
        }
    }

    @Test
    fun aNumberOrADateWithoutANameWaitsUntilTheNameIsFilled() {
        withViewModel { viewModel, useCases, _, _ ->
            showCountdown(viewModel)
            await { viewModel.uiState.value.ready }
            tap("Add countdown")
            daysField().performTextInput("25")
            val inTwentyFive = LocalDate.now().plusDays(25).toEpochDay()
            compose.onNodeWithText(scheduleMomentLabel(inTwentyFive, null)).assertExists()
            daysField().performTouchInput { click() }
            daysField().performImeAction()
            compose.onNodeWithText("Name the event.").assertExists()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            compose.onNodeWithText(scheduleMomentLabel(inTwentyFive, null)).assertExists()
            assertEquals(0, taskCount(useCases))

            nameField().performTextInput("Dana's birthday")
            awaitCountdown(useCases, "Dana's birthday", inTwentyFive)
            assertEquals(1, taskCount(useCases))
            compose.onNodeWithText("Count down to").assertDoesNotExist()

            tap("Add countdown")
            tap("Pick a date")
            tap("OK")
            compose.onNodeWithText("Name the event.").assertExists()
            compose.onNodeWithText("Save countdown").assertDoesNotExist()
            assertEquals(1, taskCount(useCases))

            val eventDay = LocalDate.now().plusDays(7).toEpochDay()
            nameField().performTextInput("Anniversary")
            awaitCountdown(useCases, "Anniversary", eventDay)
            assertEquals(2, taskCount(useCases))
            compose.onNodeWithText("Count down to").assertDoesNotExist()
        }
    }

    @Test
    fun eachActionAfterArrivalClosesTheEventAndLeavesTheParentWaiting() {
        withViewModel { viewModel, useCases, repository, _ ->
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
            tap("Pick a date")
            tap("OK")
            val tomorrow = today.plusDays(1).toEpochDay()
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Anniversary" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == tomorrow &&
                    event.countdownIntervalDays == 1 &&
                    parentWaits(snapshot)
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
        withViewModel { viewModel, useCases, repository, _ ->
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

    @Test
    fun resumeOneTapUsesTheLastCountFromHomeAndTypedDaysFromFocus() {
        withViewModel { viewModel, useCases, repository, _ ->
            val today = LocalDate.now()
            runBlocking {
                useCases.captureCountdown("Birthday", today.plusDays(12).toEpochDay())
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
            await { viewModel.uiState.value.now?.title == "Dentist" }
            tap("Count down again")
            compose.onNodeWithText("Again in 12 days").assertExists()
            tap("Again in 12 days")
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(12).toEpochDay() &&
                    event.countdownIntervalDays == 12 &&
                    parentWaits(snapshot)
            }
            compose.onNodeWithText("Again in 12 days").assertDoesNotExist()

            compose.runOnIdle { screen = "focus" }
            arriveAgain(useCases, repository, "Checkup", today)
            await { viewModel.uiState.value.now?.title == "Checkup" }
            tap("Count down again")
            compose.onNodeWithText("Again in 12 days").assertExists()
            compose.onNode(hasSetTextAction()).performTextInput("14")
            compose.onNode(hasSetTextAction()).performTouchInput { click() }
            compose.onNode(hasSetTextAction()).performImeAction()
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Checkup" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(14).toEpochDay() &&
                    event.countdownIntervalDays == 14 &&
                    parentWaits(snapshot)
            }
        }
    }

    @Test
    fun resumeBackClosesTheSheetWithoutDetaching() {
        withViewModel { viewModel, useCases, repository, _ ->
            val today = LocalDate.now()
            runBlocking {
                useCases.capture("Write the report")
                val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
                useCases.promote(work.id)
                useCases.start(work.id)
                useCases.captureCountdown("Dentist", today.toEpochDay())
                repository.releaseDueCountdowns()
            }
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    HomeScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenFocus = {},
                        onOpenInbox = {},
                        onOpenLater = {},
                        onOpenScheduled = {},
                        onOpenDone = {},
                    )
                }
            }
            await { viewModel.uiState.value.now?.isEvent == true }
            tap("Count down again")
            compose.onNodeWithText("Days from today").assertExists()
            compose.onNodeWithText("Again in", substring = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Close").performTouchInput { click() }
            compose.waitForIdle()
            compose.onNodeWithText("Days from today").assertDoesNotExist()
            await { viewModel.uiState.value.now?.title == "Dentist" && viewModel.uiState.value.now?.isEvent == true }
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    snapshot.frames.any { it.taskId == event.id && it.state == FrameState.ACTIVE }
            }
        }
    }

    @Test
    fun resumePrefillsTheEventIntervalOverTheLastCountOnThisPhone() {
        withViewModel { viewModel, useCases, repository, database ->
            val today = LocalDate.now()
            runBlocking {
                useCases.captureCountdown("Birthday", today.plusDays(12).toEpochDay())
                useCases.capture("Write the report")
                val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
                useCases.promote(work.id)
                useCases.start(work.id)
                useCases.captureCountdown("Dentist", today.toEpochDay())
                val dentist = useCases.observeSnapshot().first().tasks.single { it.title == "Dentist" }
                val row = database.taskDao().getById(dentist.id) ?: error("Missing dentist")
                database.taskDao().upsert(row.copy(countdownIntervalDays = 30))
                repository.releaseDueCountdowns()
            }
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    FocusScreen(state = state, viewModel = viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Dentist" }
            tap("Count down again")
            compose.onNodeWithText("Again in 30 days").assertExists()
            compose.onNodeWithText("Again in 12 days").assertDoesNotExist()
            tap("Again in 30 days")
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(30).toEpochDay() &&
                    event.countdownIntervalDays == 30 &&
                    parentWaits(snapshot)
            }
        }
    }

    private fun arriveAgain(
        useCases: FocusUseCases,
        repository: FocusRepositoryImpl,
        title: String,
        today: LocalDate,
    ) {
        runOffTestThread {
            useCases.captureCountdown(title, today.toEpochDay())
            repository.releaseDueCountdowns()
        }
    }

    private fun parentWaits(snapshot: FocusSnapshot): Boolean {
        val work = snapshot.tasks.firstOrNull { it.title == "Write the report" || it.title == "Today 1" } ?: return false
        val frame = snapshot.frames.firstOrNull { it.taskId == work.id } ?: return false
        return frame.state == FrameState.AWAITING_RESUME && snapshot.frames.none { it.state == FrameState.ACTIVE }
    }

    private fun withViewModel(
        block: (FocusStackViewModel, FocusUseCases, FocusRepositoryImpl, FocusStackDatabase) -> Unit,
    ) {
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
            block(viewModel, useCases, repository, database)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun showCountdown(viewModel: FocusStackViewModel) {
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
    }

    private fun nameField(): SemanticsNodeInteraction = compose.onAllNodes(hasSetTextAction())[0]

    private fun daysField(): SemanticsNodeInteraction = compose.onAllNodes(hasSetTextAction())[1]

    private fun taskCount(useCases: FocusUseCases): Int =
        runBlocking { useCases.observeSnapshot().first().tasks.size }

    private fun awaitCountdown(
        useCases: FocusUseCases,
        title: String,
        epochDay: Long,
    ): Task {
        lateinit var found: Task
        awaitSnapshot(useCases) { snapshot ->
            val task = snapshot.tasks.singleOrNull { it.title == title }
            if (
                task != null &&
                task.bucket == TaskBucket.COUNTDOWN &&
                task.scheduledOnEpochDay == epochDay &&
                snapshot.frames.isEmpty()
            ) {
                found = task
                true
            } else {
                false
            }
        }
        return found
    }

    private fun tapCalendarDay(date: LocalDate) {
        val before = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        compose
            .onNode(hasContentDescription("Switch to text input mode", substring = true))
            .performTouchInput { click() }
        compose.waitForIdle()
        val fields = compose.onAllNodes(hasSetTextAction())
        val count = fields.fetchSemanticsNodes().size
        check(count > before) { "The date field did not open" }
        fields[count - 1].performTextReplacement(dateInputDigits(date))
        compose.waitForIdle()
        val shown = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()).format(date)
        compose.onNode(hasContentDescription(shown, substring = true)).assertExists()
    }

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

/** Digits for the Material date field, in the same order as its localized pattern. */
private fun dateInputDigits(date: LocalDate): String {
    val localeFormat =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            Locale.getDefault(),
        )
    val pattern =
        localeFormat
            .replace(Regex("[^dMy/\\-.]"), "")
            .replace(Regex("d{1,2}"), "dd")
            .replace(Regex("M{1,2}"), "MM")
            .replace(Regex("y{1,4}"), "yyyy")
    val delimiter = Regex("[/\\-.]").find(pattern)?.value.orEmpty()
    val without = if (delimiter.isEmpty()) pattern else pattern.replace(delimiter, "")
    val digits = StringBuilder()
    var index = 0
    while (index < without.length) {
        val mark = without[index]
        var end = index + 1
        while (end < without.length && without[end] == mark) end += 1
        val width = end - index
        val piece =
            when (mark) {
                'M' -> date.monthValue.toString().padStart(width, '0')
                'd' -> date.dayOfMonth.toString().padStart(width, '0')
                'y' -> date.year.toString().padStart(width, '0').takeLast(width)
                else -> ""
            }
        digits.append(piece)
        index = end
    }
    return digits.toString()
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
