package app.backpin.focus.ui.capture

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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.scheduledMomentEpochMillis
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.home.ScheduledScreen
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class ScheduleCalendarTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tomorrowInSevenDaysOrAPickedDateSchedulesOnceAndCloses() {
        val wakeUps = mutableListOf<Long>()
        withViewModel(wakeUps) { viewModel, useCases ->
            var open by mutableStateOf(true)
            compose.setContent {
                if (!open) return@setContent
                FocusStackTheme {
                    CaptureSheet(
                        phrases = emptyList(),
                        frameActive = false,
                        onDismiss = { open = false },
                        onSaveToInbox = viewModel::saveToInbox,
                        onInterruptNow = viewModel::interruptNow,
                        onPlaceToday = viewModel::placeOnToday,
                        onPlaceStart = viewModel::placeAndStart,
                        onPlaceLater = viewModel::placeLater,
                        onSchedule = viewModel::placeScheduled,
                        recognizer = QuietRecognizer,
                    )
                }
            }

            fun reopen() {
                compose.runOnIdle { open = true }
                compose.waitForIdle()
            }

            compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
            tap("Schedule")
            compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Add a time").assertDoesNotExist()
            compose.onNodeWithText("Change date").assertDoesNotExist()
            tap("Tomorrow")
            val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
            awaitScheduled(useCases, "Call Dana", tomorrow, minute = null)
            assertEquals(1, taskCount(useCases))
            assertSheetClosed()

            reopen()
            compose.onNode(hasSetTextAction()).performTextInput("File the form")
            tap("Schedule")
            compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
            tap("In 7 days")
            awaitScheduled(useCases, "File the form", LocalDate.now().plusDays(7).toEpochDay(), minute = null)
            assertEquals(2, taskCount(useCases))
            assertSheetClosed()

            reopen()
            compose.onNode(hasSetTextAction()).performTextInput("Book the room")
            tap("Schedule")
            tap("Pick a date")
            tap("OK")
            awaitScheduled(useCases, "Book the room", tomorrow, minute = null)
            assertEquals(3, taskCount(useCases))
            assertSheetClosed()
            assertTrue(wakeUps.isEmpty())
        }
    }

    @Test
    fun cancellingTheCalendarLeavesTheQuickDaysInPlace() {
        val placed = mutableListOf<String>()
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = false,
                    onDismiss = {},
                    onSaveToInbox = { placed += "inbox:$it" },
                    onInterruptNow = { placed += "interrupt:$it" },
                    onPlaceToday = { placed += "today:$it" },
                    onPlaceStart = { placed += "start:$it" },
                    onPlaceLater = { placed += "later:$it" },
                    onSchedule = { title, day, minute ->
                        placed += if (minute == null) "schedule:$title:$day" else "schedule:$title:$day:$minute"
                    },
                    recognizer = QuietRecognizer,
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
        tap("Schedule")
        tap("Pick a date")
        tap("Cancel")
        compose.onNodeWithText("Add a time").assertDoesNotExist()
        compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Change date").assertDoesNotExist()
        tap("In 7 days")
        assertEquals(listOf("schedule:Call Dana:${LocalDate.now().plusDays(7).toEpochDay()}"), placed)
    }

    @Test
    fun changeDateAndAddATimeUpdateTheSameScheduledTask() {
        val wakeUps = mutableListOf<Long>()
        withViewModel(wakeUps) { viewModel, useCases ->
            var open by mutableStateOf(true)
            var showScheduled by mutableStateOf(false)
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    if (open) {
                        CaptureSheet(
                            phrases = emptyList(),
                            frameActive = false,
                            onDismiss = { open = false },
                            onSaveToInbox = viewModel::saveToInbox,
                            onInterruptNow = viewModel::interruptNow,
                            onPlaceToday = viewModel::placeOnToday,
                            onPlaceStart = viewModel::placeAndStart,
                            onPlaceLater = viewModel::placeLater,
                            onSchedule = viewModel::placeScheduled,
                            recognizer = QuietRecognizer,
                        )
                    }
                    if (showScheduled) {
                        ScheduledScreen(
                            tasks = state.scheduledTasks,
                            ready = state.ready,
                            canInterrupt = false,
                            onBack = {},
                            onToday = {},
                            onStart = {},
                            onInterrupt = {},
                            onInbox = {},
                            onLater = {},
                            onRemove = {},
                            onChangeDate = viewModel::changeScheduledDate,
                            onChangeTime = viewModel::changeScheduledTime,
                        )
                    }
                }
            }
            compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
            tap("Schedule")
            tap("Tomorrow")
            val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
            val created = awaitScheduled(useCases, "Call Dana", tomorrow, minute = null)
            assertSheetClosed()
            compose.runOnIdle { showScheduled = true }
            compose.waitForIdle()

            tap("Change date")
            val next = LocalDate.now().plusDays(2)
            tapCalendarDay(next)
            tap("OK")
            val dated = awaitScheduled(useCases, "Call Dana", next.toEpochDay(), minute = null)
            assertEquals(created.id, dated.id)
            assertEquals(1, taskCount(useCases))

            tap("Add a time")
            tap("OK")
            val timed = awaitScheduled(useCases, "Call Dana", next.toEpochDay(), minute = 9 * 60)
            assertEquals(created.id, timed.id)
            assertEquals(1, taskCount(useCases))
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.scheduledTasks.singleOrNull { it.title == "Call Dana" }?.minuteOfDay == 9 * 60
            }
            compose.onNodeWithText("Change time", useUnmergedTree = true).assertExists()
            compose.waitUntil(timeoutMillis = 5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                wakeUps.isNotEmpty()
            }
            assertEquals(scheduledMomentEpochMillis(next.toEpochDay(), 9 * 60), wakeUps.single())

            tap("Change time")
            tap("OK")
            val retimed = awaitScheduled(useCases, "Call Dana", next.toEpochDay(), minute = 9 * 60)
            assertEquals(created.id, retimed.id)
            assertEquals(1, taskCount(useCases))

            tap("Clear time")
            val cleared = awaitScheduled(useCases, "Call Dana", next.toEpochDay(), minute = null)
            assertEquals(created.id, cleared.id)
            assertNull(cleared.scheduledAtMinuteOfDay)
            assertEquals(1, taskCount(useCases))
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.scheduledTasks.singleOrNull { it.title == "Call Dana" }?.minuteOfDay == null
            }
            compose.onNodeWithText("Add a time", useUnmergedTree = true).assertExists()
        }
    }

    private fun assertSheetClosed() {
        compose.onNodeWithText("When should it come back?").assertDoesNotExist()
        compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
    }

    private fun withViewModel(
        wakeUps: MutableList<Long>,
        block: (FocusStackViewModel, FocusUseCases) -> Unit,
    ) {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "calendar-tap-${UUID.randomUUID()}"),
                    clock = AppClock { System.currentTimeMillis() },
                ),
                wakeUp = { wakeUps += it },
            )
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
            block(viewModel, useCases)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun taskCount(useCases: FocusUseCases): Int =
        runBlocking { useCases.observeSnapshot().first().tasks.size }

    private fun awaitScheduled(
        useCases: FocusUseCases,
        title: String,
        epochDay: Long,
        minute: Int?,
    ): Task {
        lateinit var found: Task
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking {
                val snapshot = useCases.observeSnapshot().first()
                val task = snapshot.tasks.singleOrNull { it.title == title }
                if (
                    task != null &&
                    task.bucket == TaskBucket.SCHEDULED &&
                    task.scheduledOnEpochDay == epochDay &&
                    task.scheduledAtMinuteOfDay == minute &&
                    snapshot.frames.none { it.taskId == task.id }
                ) {
                    found = task
                    true
                } else {
                    false
                }
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
        val merged = compose.onAllNodesWithText(label).fetchSemanticsNodes()
        val node =
            if (merged.isNotEmpty()) {
                compose.onNodeWithText(label).assertIsEnabled().assertHasClickAction()
            } else {
                compose.onNodeWithText(label, useUnmergedTree = true)
            }
        node.performTouchInput { click() }
        compose.waitForIdle()
        return node
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
