// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
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
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.TaskSheetRole
import app.backpin.focus.ui.TaskSheetUi
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.home.InboxScreen
import app.backpin.focus.ui.home.LaterScreen
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TaskSheetTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tappingTodayOpenWithoutFocusShowsStartDoneLaterEditAndStartCloses() {
        withHarness { viewModel, useCases, _ ->
            runBlocking { todayTask(useCases, "Park me") }
            showHome(viewModel)
            await { viewModel.uiState.value.todaySlots.any { it.task?.title == "Park me" } }
            tapRow("open. Park me. Opens the task sheet")
            await { viewModel.taskSheet.value?.role == TaskSheetRole.TODAY_OPEN }
            assertEquals(listOf("Start", "Done", "Later", "Edit"), sheetActions())
            tapSheet("Start")
            await { viewModel.taskSheet.value == null && viewModel.uiState.value.now?.title == "Park me" }
            compose.onNodeWithText("This task.").assertDoesNotExist()
        }
    }

    @Test
    fun tappingTodayOpenInFocusAddsInterruptAndInterruptNests() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                todayTask(useCases, "Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                todayTask(useCases, "Park me")
            }
            showHome(viewModel)
            await { viewModel.uiState.value.now?.title == "Hold this" }
            tapRow("open. Park me. Opens the task sheet")
            await { viewModel.taskSheet.value?.canInterrupt == true }
            assertEquals(listOf("Start", "Done", "Later", "Interrupt", "Edit"), sheetActions())
            tapSheet("Interrupt")
            await {
                viewModel.taskSheet.value == null && viewModel.uiState.value.now?.title == "Park me"
            }
            awaitSnapshot(useCases) { snapshot ->
                activeTitle(snapshot) == "Park me" &&
                    snapshot.frames.any { it.taskId == idOfBlocking(useCases, "Hold this") && it.state == FrameState.PAUSED }
            }
        }
    }

    @Test
    fun todayOpenDoneLaterAndEditEachRunAndCloseTheSheet() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                todayTask(useCases, "Finish me")
                todayTask(useCases, "Park me")
                todayTask(useCases, "Rename me")
            }
            showHome(viewModel)
            await { viewModel.uiState.value.todaySlots.count { it.task != null } == 3 }

            tapRow("open. Finish me. Opens the task sheet")
            await { viewModel.taskSheet.value?.title == "Finish me" }
            tapSheet("Done")
            awaitSnapshot(useCases) { task(it, "Finish me").status == TaskStatus.DONE }
            assertNull(viewModel.taskSheet.value)

            tapRow("open. Park me. Opens the task sheet")
            await { viewModel.taskSheet.value?.title == "Park me" }
            tapSheet("Later")
            awaitSnapshot(useCases) { task(it, "Park me").bucket == TaskBucket.LATER }
            assertNull(viewModel.taskSheet.value)

            tapRow("open. Rename me. Opens the task sheet")
            await { viewModel.taskSheet.value?.title == "Rename me" }
            tapSheet("Edit")
            await { viewModel.taskSheet.value == null && viewModel.uiState.value.editing?.title == "Rename me" }
            compose.onNodeWithText("This task.").assertDoesNotExist()
        }
    }

    @Test
    fun waitingRowShowsResumeLaterEditAndNeverInterrupt() {
        withHarness { viewModel, useCases, _ ->
            runBlocking { seedWaitingToday(useCases) }
            showHome(viewModel)
            awaitWaiting(viewModel)
            tapRow("waiting. Hold this. Opens the task sheet")
            await { viewModel.taskSheet.value?.role == TaskSheetRole.TODAY_WAITING }
            assertEquals(listOf("Resume", "Later", "Edit"), sheetActions())
            tapSheet("Later")
            awaitSnapshot(useCases) { task(it, "Hold this").bucket == TaskBucket.LATER }
            assertNull(viewModel.taskSheet.value)
        }
    }

    @Test
    fun waitingResumeResumesTheParentAndCloses() {
        withHarness { viewModel, useCases, _ ->
            runBlocking { seedWaitingToday(useCases) }
            showHome(viewModel)
            awaitWaiting(viewModel)
            tapRow("waiting. Hold this. Opens the task sheet")
            await { viewModel.taskSheet.value?.role == TaskSheetRole.TODAY_WAITING }
            tapSheet("Resume")
            await { viewModel.uiState.value.now?.title == "Hold this" }
            assertNull(viewModel.taskSheet.value)
        }
    }

    @Test
    fun inboxWithoutFocusOmitsInterruptLikeTodayOpen() {
        withHarness { viewModel, useCases, _ ->
            runBlocking { useCases.capture("From inbox") }
            showInbox(viewModel)
            await { viewModel.uiState.value.inboxTasks.any { it.title == "From inbox" } }
            tapRow("Inbox. From inbox. Opens the task sheet")
            await { viewModel.taskSheet.value?.role == TaskSheetRole.PILE && viewModel.taskSheet.value?.canInterrupt == false }
            assertEquals(listOf("Today", "Start", "Edit"), sheetActions())
        }
    }

    @Test
    fun inboxInFocusShowsTodayStartInterruptEditAndTodayCloses() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                todayTask(useCases, "Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                useCases.capture("From inbox")
            }
            showInbox(viewModel)
            await { viewModel.uiState.value.now?.title == "Hold this" }
            tapRow("Inbox. From inbox. Opens the task sheet")
            await { viewModel.taskSheet.value?.canInterrupt == true }
            assertEquals(listOf("Today", "Start", "Interrupt", "Edit"), sheetActions())
            tapSheet("Today")
            awaitSnapshot(useCases) { task(it, "From inbox").bucket == TaskBucket.TODAY }
            assertNull(viewModel.taskSheet.value)
        }
    }

    @Test
    fun laterInFocusInterruptNestsFromThePileAndCloses() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                todayTask(useCases, "Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                useCases.captureLater("From later")
            }
            showLater(viewModel)
            await { viewModel.uiState.value.laterTasks.any { it.title == "From later" } }
            tapRow("Later. From later. Opens the task sheet")
            await { viewModel.taskSheet.value?.role == TaskSheetRole.PILE }
            assertEquals(listOf("Today", "Start", "Interrupt", "Edit"), sheetActions())
            tapSheet("Interrupt")
            awaitSnapshot(useCases) { snapshot ->
                activeTitle(snapshot) == "From later" &&
                    snapshot.frames.any { it.taskId == idOfBlocking(useCases, "Hold this") && it.state == FrameState.PAUSED }
            }
            assertNull(viewModel.taskSheet.value)
        }
    }

    @Test
    fun laterStartAndEditEachRunAndCloseTheSheet() {
        withHarness { viewModel, useCases, _ ->
            runBlocking {
                useCases.captureLater("Start later")
                useCases.captureLater("Edit later")
            }
            showLater(viewModel)
            await { viewModel.uiState.value.laterTasks.size == 2 }

            tapRow("Later. Start later. Opens the task sheet")
            await { viewModel.taskSheet.value?.title == "Start later" }
            tapSheet("Start")
            awaitSnapshot(useCases) { activeTitle(it) == "Start later" }
            assertNull(viewModel.taskSheet.value)

            tapRow("Later. Edit later. Opens the task sheet")
            await { viewModel.taskSheet.value?.title == "Edit later" }
            tapSheet("Edit")
            await { viewModel.uiState.value.editing?.title == "Edit later" }
            assertNull(viewModel.taskSheet.value)
        }
    }

    private fun showHome(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            val sheet by viewModel.taskSheet.collectAsState()
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
                sheet?.let { pending -> hostedTaskSheet(pending, viewModel) }
            }
        }
    }

    private fun showInbox(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            val sheet by viewModel.taskSheet.collectAsState()
            FocusStackTheme {
                InboxScreen(
                    tasks = state.inboxTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = {},
                    onToday = viewModel::promote,
                    onStart = viewModel::start,
                    onInterrupt = viewModel::interruptFromPile,
                    onEdit = viewModel::openEdit,
                    onOpenSheet = viewModel::openTaskSheet,
                )
                sheet?.let { pending -> hostedTaskSheet(pending, viewModel) }
            }
        }
    }

    private fun showLater(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            val sheet by viewModel.taskSheet.collectAsState()
            FocusStackTheme {
                LaterScreen(
                    tasks = state.laterTasks,
                    ready = state.ready,
                    canInterrupt = state.canInterrupt,
                    onBack = {},
                    onToday = viewModel::promote,
                    onStart = viewModel::start,
                    onInterrupt = viewModel::interruptFromPile,
                    onEdit = viewModel::openEdit,
                    onOpenSheet = viewModel::openTaskSheet,
                )
                sheet?.let { pending -> hostedTaskSheet(pending, viewModel) }
            }
        }
    }

    @Composable
    private fun hostedTaskSheet(
        pending: TaskSheetUi,
        viewModel: FocusStackViewModel,
    ) {
        TaskSheet(
            state = pending,
            onDismiss = viewModel::dismissTaskSheet,
            onStart = viewModel::startFromTaskSheet,
            onDone = viewModel::doneFromTaskSheet,
            onEdit = viewModel::editFromTaskSheet,
            onInterrupt = viewModel::interruptFromTaskSheet,
            onToday = viewModel::todayFromTaskSheet,
            onResume = viewModel::resumeFromTaskSheet,
            onBringBack = viewModel::bringBackFromTaskSheet,
        )
    }

    private fun withHarness(block: (FocusStackViewModel, FocusUseCases, FocusStackDatabase) -> Unit) {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "task-sheet-${UUID.randomUUID()}"),
                    clock = AppClock { System.currentTimeMillis() },
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
            block(viewModel, useCases, database)
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
        CHIP_LABELS.forEach { label ->
            val nodes = compose.onAllNodesWithText(label)
            val found = nodes.fetchSemanticsNodes()
            found.indices.forEach { index ->
                val bounds = nodes[index].getBoundsInRoot()
                if (bounds.bottom - bounds.top >= SHEET_CHIP_MIN) {
                    hits += Hit(label, bounds.left, bounds.top)
                }
            }
        }
        return hits.sortedWith(compareBy({ it.top }, { it.left })).map { it.label }
    }

    private fun tapSheet(label: String) {
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithText(label)
        val found = nodes.fetchSemanticsNodes()
        val sheet =
            found.indices.filter { index ->
                val bounds = nodes[index].getBoundsInRoot()
                bounds.bottom - bounds.top >= SHEET_CHIP_MIN
            }
        check(sheet.isNotEmpty()) { "No sheet chip $label" }
        val node = nodes[sheet.maxBy { found[it].boundsInRoot.top }]
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

    private suspend fun todayTask(
        useCases: FocusUseCases,
        title: String,
    ) {
        useCases.capture(title)
        useCases.promote(idOf(useCases, title))
    }

    /** Nested interrupt inside Today, then Done on the child. Parent stays a Today waiting row. */
    private suspend fun seedWaitingToday(useCases: FocusUseCases) {
        todayTask(useCases, "Hold this")
        todayTask(useCases, "Ping")
        useCases.start(idOf(useCases, "Hold this"))
        useCases.interruptWith(idOf(useCases, "Ping"))
        useCases.completeActive()
    }

    private fun awaitWaiting(viewModel: FocusStackViewModel) {
        await {
            viewModel.uiState.value.now == null &&
                viewModel.uiState.value.recovery?.title == "Hold this" &&
                viewModel.uiState.value.todaySlots.any { slot ->
                    slot.role == SlotRole.WAITING && slot.task?.title == "Hold this"
                }
        }
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun idOfBlocking(
        useCases: FocusUseCases,
        title: String,
    ): String = runBlocking { idOf(useCases, title) }

    private fun task(
        snapshot: FocusSnapshot,
        title: String,
    ) = snapshot.tasks.single { it.title == title }

    private fun activeTitle(snapshot: FocusSnapshot): String? {
        val active = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE } ?: return null
        return snapshot.tasks.singleOrNull { it.id == active.taskId }?.title
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

    private companion object {
        val SHEET_CHIP_MIN = 50.dp
        val CHIP_LABELS =
            listOf(
                "Start",
                "Done",
                "Later",
                "Edit",
                "Interrupt",
                "Resume",
                "Today",
                "Done now",
                "Skip today",
                "Tomorrow",
                "Pick a day",
                "Pick a date",
            )
    }
}
