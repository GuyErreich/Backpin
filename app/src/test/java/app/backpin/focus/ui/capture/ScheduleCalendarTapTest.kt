package app.backpin.focus.ui.capture

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.scheduleMomentLabel
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
class ScheduleCalendarTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pickADateThenAddATimeSchedulesThatMoment() {
        val placed = mutableListOf<String>()
        show(placed)
        compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
        tap("Schedule")
        tap("Pick a date")
        tap("OK")
        val tomorrow = LocalDate.now().plusDays(1)
        compose.onNodeWithText("Comes back ${scheduleMomentLabel(tomorrow.toEpochDay(), null)}.").assertExists()
        assertTrue(placed.isEmpty())

        tap("Add a time")
        tap("OK")
        val label = scheduleMomentLabel(tomorrow.toEpochDay(), 9 * 60)
        compose.onNodeWithText("Comes back $label.").assertExists()
        compose.onNodeWithText("Change time").assertExists()
        assertTrue(placed.isEmpty())

        tap("Schedule for $label")
        assertEquals(listOf("schedule:Call Dana:${tomorrow.toEpochDay()}:540"), placed)
    }

    @Test
    fun cancellingTheCalendarLeavesTheQuickDaysInPlace() {
        val placed = mutableListOf<String>()
        show(placed)
        compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
        tap("Schedule")
        tap("Pick a date")
        tap("Cancel")
        compose.onNodeWithText("Add a time").assertDoesNotExist()
        tap("In 7 days")
        assertEquals(listOf("schedule:Call Dana:${LocalDate.now().plusDays(7).toEpochDay()}"), placed)
    }

    @Test
    fun aPickedMomentReachesTheRepositoryThroughTheViewModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val wakeUps = mutableListOf<Long>()
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
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    CaptureSheet(
                        phrases = emptyList(),
                        frameActive = state.now != null,
                        onDismiss = {},
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
            compose.onNode(hasSetTextAction()).performTextInput("Talk to Dana")
            tap("Schedule")
            tap("Pick a date")
            tap("OK")
            tap("Add a time")
            tap("OK")
            val tomorrow = LocalDate.now().plusDays(1).toEpochDay()
            tap("Schedule for ${scheduleMomentLabel(tomorrow, 9 * 60)}")
            awaitSnapshot(useCases) { snapshot ->
                val task = snapshot.tasks.singleOrNull { it.title == "Talk to Dana" }
                task != null &&
                    task.bucket == TaskBucket.SCHEDULED &&
                    task.scheduledOnEpochDay == tomorrow &&
                    task.scheduledAtMinuteOfDay == 9 * 60 &&
                    snapshot.frames.isEmpty()
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                shadowOf(Looper.getMainLooper()).idle()
                wakeUps.isNotEmpty()
            }
            assertEquals(
                app.backpin.focus.domain.scheduledMomentEpochMillis(tomorrow, 9 * 60),
                wakeUps.single(),
            )
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun show(placed: MutableList<String>) {
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
    }

    private fun tap(label: String): SemanticsNodeInteraction {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        compose.waitForIdle()
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
