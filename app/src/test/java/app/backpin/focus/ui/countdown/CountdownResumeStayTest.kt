package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.backInDaysLabel
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class CountdownResumeStayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resumeFromHomeStaysOnHomeShowsBackInAndUndoRestoresTheEvent() {
        withViewModel { viewModel, useCases, repository, database ->
            val today = LocalDate.now()
            seedArrivedEvent(useCases, repository, database, intervalDays = 180)
            compose.setContent {
                FocusStackTheme {
                    FocusApp(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Dentist checkup" }
            compose.onNodeWithText("Keep your place.").assertExists()
            compose.onNodeWithText("Home").assertIsSelected()

            tap("Count down again")
            resumeDaysField().performTextInput("45")
            tap("Start in 45 days")

            val expected = backInDaysLabel(45, today)
            await { viewModel.uiState.value.undo?.message == expected }
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist checkup" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(45).toEpochDay() &&
                    event.countdownIntervalDays == 45 &&
                    parentWaits(snapshot)
            }
            compose.onNodeWithText("Keep your place.").assertExists()
            compose.onNodeWithText("Home").assertIsSelected()
            compose.onNodeWithText("Countdown").assertIsNotSelected()
            compose.onNodeWithText("Add countdown").assertDoesNotExist()
            compose.onNodeWithText("EVENT").assertDoesNotExist()
            compose.onNodeWithText(expected).assertExists()
            compose.onNodeWithText("Undo").assertExists()

            tap("Undo")
            await { viewModel.uiState.value.now?.title == "Dentist checkup" && viewModel.uiState.value.now?.isEvent == true }
            await { viewModel.uiState.value.undo == null }
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist checkup" }
                val work = snapshot.tasks.single { it.title == "Write the report" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.countdownIntervalDays == 180 &&
                    snapshot.frames.any { it.taskId == event.id && it.state == FrameState.ACTIVE } &&
                    snapshot.frames.any { it.taskId == work.id && it.state == FrameState.PAUSED }
            }
            compose.onNodeWithText("Keep your place.").assertExists()
            compose.onNodeWithText("Home").assertIsSelected()
            compose.onNodeWithText("EVENT").assertExists()
            compose.onNodeWithText(expected).assertDoesNotExist()
            val restored = runBlocking { useCases.observeSnapshot().first() }
            assertEquals(180, restored.tasks.single { it.title == "Dentist checkup" }.countdownIntervalDays)
            assertNull(viewModel.uiState.value.undo)
        }
    }

    @Test
    fun resumeFromFocusStaysOnFocusAndNeverOpensCountdown() {
        withViewModel { viewModel, useCases, repository, database ->
            val today = LocalDate.now()
            seedArrivedEvent(useCases, repository, database, intervalDays = 180)
            compose.setContent {
                FocusStackTheme {
                    FocusApp(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Dentist checkup" }
            tap("Focus")
            compose.onNodeWithText("Focus").assertIsSelected()
            compose.onNodeWithText("EVENT").assertExists()

            tap("Count down again")
            resumeDaysField().performTextInput("45")
            tap("Start in 45 days")

            val expected = backInDaysLabel(45, today)
            await { viewModel.uiState.value.undo?.message == expected }
            await { viewModel.uiState.value.now == null && viewModel.uiState.value.recovery?.title == "Write the report" }
            compose.onNodeWithText("Focus").assertIsSelected()
            compose.onNodeWithText("Home").assertIsNotSelected()
            compose.onNodeWithText("Countdown").assertIsNotSelected()
            compose.onNodeWithText("Add countdown").assertDoesNotExist()
            compose.onNodeWithText("Resume").assertExists()
            compose.onNodeWithText(expected).assertExists()
            compose.onNodeWithText("Undo").assertExists()
        }
    }

    private fun seedArrivedEvent(
        useCases: FocusUseCases,
        repository: FocusRepositoryImpl,
        database: FocusStackDatabase,
        intervalDays: Int,
    ) {
        runBlocking {
            useCases.capture("Write the report")
            val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
            useCases.promote(work.id)
            useCases.start(work.id)
            useCases.captureCountdown("Dentist checkup", LocalDate.now().toEpochDay())
            val dentist = useCases.observeSnapshot().first().tasks.single { it.title == "Dentist checkup" }
            val row = database.taskDao().getById(dentist.id) ?: error("Missing dentist")
            database.taskDao().upsert(row.copy(countdownIntervalDays = intervalDays))
            repository.releaseDueCountdowns()
        }
    }

    private fun parentWaits(snapshot: FocusSnapshot): Boolean {
        val work = snapshot.tasks.firstOrNull { it.title == "Write the report" } ?: return false
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
                preferencesStore = PreferencesDataStore(context, "countdown-stay-${UUID.randomUUID()}"),
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

    private fun resumeDaysField() = compose.onNode(hasSetTextAction() and hasText("Days from today"))

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        compose.waitForIdle()
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
