// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.home.ScheduledScreen
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MissedTaskSheetQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tappingADueScheduledCardOpensDoneNowTomorrowAndPickADay() {
        withHarness { viewModel, useCases, _ ->
            val today = LocalDate.now()
            runBlocking {
                useCases.captureScheduled("Call Dana", today.toEpochDay())
            }
            showScheduled(viewModel)
            await { viewModel.uiState.value.scheduledTasks.singleOrNull { it.title == "Call Dana" }?.due == true }
            tapRow("Scheduled. Call Dana")
            await { viewModel.missedTaskSheet.value?.title == "Call Dana" }
            compose.onNodeWithText("This one was missed.").assertExists()
            compose.onNodeWithText("It stayed off Today. Done now, or pick another day. Never an alarm.").assertExists()
            assertEquals(listOf("Done now", "Tomorrow", "Pick a day"), sheetActions())
            assertEquals(
                TaskBucket.SCHEDULED,
                runBlocking { task(useCases, "Call Dana").bucket },
            )
        }
    }

    @Test
    fun doneNowCompletesTheDueTaskAndClosesTheSheet() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                useCases.captureScheduled("Call Dana", LocalDate.now().toEpochDay())
            }
            showScheduled(viewModel)
            await { viewModel.uiState.value.scheduledTasks.any { it.title == "Call Dana" && it.due } }
            tapRow("Scheduled. Call Dana")
            await { viewModel.missedTaskSheet.value != null }
            tapSheet("Done now")
            awaitSnapshot(useCases) { task(it, "Call Dana").status == TaskStatus.DONE }
            assertNull(viewModel.missedTaskSheet.value)
            compose.onNodeWithText("This one was missed.").assertDoesNotExist()
        }
    }

    @Test
    fun tomorrowSchedulesTheNextLocalDayOnTheInjectedClock() {
        withHarness { viewModel, useCases, clock ->
            val today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowEpochMillis()), ZoneId.systemDefault())
            runBlocking {
                useCases.captureScheduled("Call Dana", today.toEpochDay())
            }
            showScheduled(viewModel)
            await { viewModel.uiState.value.scheduledTasks.any { it.title == "Call Dana" && it.due } }
            tapRow("Scheduled. Call Dana")
            await { viewModel.missedTaskSheet.value != null }
            tapSheet("Tomorrow")
            val tomorrow = today.plusDays(1)
            awaitSnapshot(useCases) { snapshot ->
                val task = task(snapshot, "Call Dana")
                task.bucket == TaskBucket.SCHEDULED &&
                    task.status == TaskStatus.OPEN &&
                    task.scheduledOnEpochDay == tomorrow.toEpochDay()
            }
            assertNull(viewModel.missedTaskSheet.value)
            await { viewModel.uiState.value.scheduledTasks.single { it.title == "Call Dana" }.due == false }
        }
    }

    @Test
    fun pickADayOpensACalendarWhoseEarliestDayIsTomorrow() {
        var picked: LocalDate? = null
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        compose.setContent {
            FocusStackTheme {
                PickDateDialog(
                    initial = tomorrow,
                    earliest = tomorrow,
                    onPick = { picked = it },
                    onDismiss = {},
                )
            }
        }
        assertTodayCellDisabled(today)
        tap("OK")
        assertEquals(tomorrow, picked)
    }

    @Test
    fun pickADayFromTheSheetSchedulesTomorrowAndRejectsToday() {
        withHarness { viewModel, useCases, clock ->
            val today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.nowEpochMillis()), ZoneId.systemDefault())
            val tomorrow = today.plusDays(1)
            runBlocking {
                useCases.captureScheduled("Call Dana", today.toEpochDay())
            }
            showScheduled(viewModel)
            await { viewModel.uiState.value.scheduledTasks.any { it.title == "Call Dana" && it.due } }
            tapRow("Scheduled. Call Dana")
            await { viewModel.missedTaskSheet.value != null }
            tapSheet("Pick a day")
            assertTodayCellDisabled(today)
            tap("OK")
            awaitSnapshot(useCases) { snapshot ->
                val task = task(snapshot, "Call Dana")
                task.bucket == TaskBucket.SCHEDULED && task.scheduledOnEpochDay == tomorrow.toEpochDay()
            }
            assertNull(viewModel.missedTaskSheet.value)
        }
    }

    private fun showScheduled(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            val missed by viewModel.missedTaskSheet.collectAsState()
            FocusStackTheme {
                ScheduledScreen(
                    tasks = state.scheduledTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = {},
                    onToday = {},
                    onStart = {},
                    onInterrupt = {},
                    onInbox = {},
                    onLater = {},
                    onRemove = {},
                    onChangeDate = { _, _ -> },
                    onChangeTime = { _, _ -> },
                    onEdit = {},
                    onOpenMissed = viewModel::openMissedTask,
                )
                missed?.let { pending ->
                    MissedTaskSheet(
                        state = pending,
                        onDismiss = viewModel::dismissMissedTask,
                        onDoneNow = viewModel::doneMissedTask,
                        onTomorrow = viewModel::tomorrowMissedTask,
                        onPickDay = viewModel::pickDayMissedTask,
                    )
                }
            }
        }
    }

    private fun withHarness(block: (FocusStackViewModel, FocusUseCases, AppClock) -> Unit) {
        val clock = AppClock { System.currentTimeMillis() }
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "missed-task-${UUID.randomUUID()}"),
                    clock = clock,
                ),
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
            block(viewModel, useCases, clock)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun sheetActions(): List<String> {
        data class Hit(
            val label: String,
            val left: androidx.compose.ui.unit.Dp,
            val top: androidx.compose.ui.unit.Dp,
        )

        val hits = mutableListOf<Hit>()
        listOf("Done now", "Tomorrow", "Pick a day", "Start", "Done", "Later", "Edit").forEach { label ->
            val nodes = compose.onAllNodesWithText(label)
            val found = nodes.fetchSemanticsNodes()
            found.indices.forEach { index ->
                val bounds = nodes[index].getBoundsInRoot()
                if (bounds.bottom - bounds.top >= 50.dp) {
                    hits += Hit(label, bounds.left, bounds.top)
                }
            }
        }
        return hits.sortedWith(compareBy({ it.top }, { it.left })).map { it.label }
    }

    private fun tapSheet(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.performScrollTo()
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun tapRow(description: String) {
        compose.waitForIdle()
        val node = compose.onNode(hasContentDescription(description, substring = true))
        node.performScrollTo()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun assertTodayCellDisabled(today: LocalDate) {
        val spoken = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()).format(today)
        val short = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()).format(today)
        val matchers =
            listOf(
                hasContentDescription(spoken, substring = true),
                hasContentDescription(short, substring = true),
            )
        matchers.forEach { matcher ->
            val count = compose.onAllNodes(matcher).fetchSemanticsNodes().size
            if (count > 0) {
                compose.onNode(matcher).assertIsNotEnabled()
            }
        }
        compose.onNodeWithText("OK").assertIsEnabled()
    }

    private fun task(
        useCases: FocusUseCases,
        title: String,
    ) = runBlocking { useCases.observeSnapshot().first().tasks.single { it.title == title } }

    private fun task(
        snapshot: FocusSnapshot,
        title: String,
    ) = snapshot.tasks.single { it.title == title }

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
