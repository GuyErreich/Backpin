package app.backpin.focus.ui.edit

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.countdown.CountdownScreen
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.DoneScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.home.InboxScreen
import app.backpin.focus.ui.home.LaterScreen
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditTaskSurfacesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editOpensFromEachOpenSurfaceAndSaveRenames() {
        withHarness { viewModel, useCases ->
            seedOpenTasks(useCases)
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
                        "focus" -> FocusScreen(state = state, viewModel = viewModel)
                        "inbox" ->
                            InboxScreen(
                                tasks = state.inboxTasks,
                                ready = state.ready,
                                canInterrupt = state.canInterrupt,
                                onBack = {},
                                onToday = {},
                                onStart = {},
                                onInterrupt = {},
                                onEdit = viewModel::openEdit,
                            )
                        "later" ->
                            LaterScreen(
                                tasks = state.laterTasks,
                                ready = state.ready,
                                canInterrupt = state.canInterrupt,
                                onBack = {},
                                onToday = {},
                                onStart = {},
                                onInterrupt = {},
                                onEdit = viewModel::openEdit,
                            )
                        "scheduled" ->
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
                                onEdit = viewModel::openEdit,
                            )
                        "countdown" ->
                            CountdownScreen(
                                countdowns = state.countdowns,
                                ready = state.ready,
                                onAdd = { _, _ -> },
                                onChangeDate = { _, _ -> },
                                onRemove = {},
                                onEdit = viewModel::openEdit,
                                recognizer = SilentRecognizer,
                            )
                        else ->
                            DoneScreen(
                                tasks = state.archiveTasks,
                                ready = state.ready,
                                onBack = {},
                                onRestore = {},
                            )
                    }
                    EditHost(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Hold this" }

            tapEditNear("Hold this")
            saveAs("Now renamed")
            await { viewModel.uiState.value.now?.title == "Now renamed" }
            await { viewModel.uiState.value.todaySlots.any { it.task?.title == "Now renamed" } }
            assertNull(viewModel.uiState.value.editing)

            tapEditNear("Park me")
            saveAs("Today renamed")
            awaitSnapshot(useCases) { task(it, "Today renamed").bucket == TaskBucket.TODAY }

            compose.runOnIdle { screen = "focus" }
            compose.waitForIdle()
            tap("Edit")
            saveAs("Focus renamed")
            await { viewModel.uiState.value.now?.title == "Focus renamed" }

            compose.runOnIdle { screen = "inbox" }
            compose.waitForIdle()
            tapEditNear("From inbox")
            saveAs("Inbox renamed")
            awaitSnapshot(useCases) { task(it, "Inbox renamed").bucket == TaskBucket.INBOX }

            compose.runOnIdle { screen = "later" }
            compose.waitForIdle()
            tapEditNear("From later")
            saveAs("Later renamed")
            awaitSnapshot(useCases) { task(it, "Later renamed").bucket == TaskBucket.LATER }

            compose.runOnIdle { screen = "scheduled" }
            compose.waitForIdle()
            tapEditNear("From scheduled")
            saveAs("Scheduled renamed")
            awaitSnapshot(useCases) { task(it, "Scheduled renamed").bucket == TaskBucket.SCHEDULED }

            compose.runOnIdle { screen = "countdown" }
            compose.waitForIdle()
            tapEditNear("From countdown")
            saveAs("Countdown renamed")
            awaitSnapshot(useCases) { task(it, "Countdown renamed").bucket == TaskBucket.COUNTDOWN }

            compose.runOnIdle { screen = "done" }
            compose.waitForIdle()
            compose.onNodeWithText("From done").assertExists()
            compose.onNodeWithText("Edit").assertDoesNotExist()
        }
    }

    @Test
    fun longPressEditsPilesAndTodayStillReorders() {
        withHarness { viewModel, useCases ->
            seedOpenTasks(useCases)
            var screen by mutableStateOf("inbox")
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    when (screen) {
                        "inbox" ->
                            InboxScreen(
                                tasks = state.inboxTasks,
                                ready = state.ready,
                                canInterrupt = state.canInterrupt,
                                onBack = {},
                                onToday = viewModel::promote,
                                onStart = {},
                                onInterrupt = {},
                                onEdit = viewModel::openEdit,
                            )
                        "later" ->
                            LaterScreen(
                                tasks = state.laterTasks,
                                ready = state.ready,
                                canInterrupt = state.canInterrupt,
                                onBack = {},
                                onToday = {},
                                onStart = {},
                                onInterrupt = {},
                                onEdit = viewModel::openEdit,
                            )
                        "scheduled" ->
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
                                onEdit = viewModel::openEdit,
                            )
                        else ->
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
                    EditHost(viewModel)
                }
            }
            await { viewModel.uiState.value.inboxTasks.any { it.title == "From inbox" } }

            compose.onNodeWithText("From inbox").performTouchInput { longClick() }
            await { viewModel.uiState.value.editing?.title == "From inbox" }
            tapClose()
            await { viewModel.uiState.value.editing == null }

            invokeCustomAction("Edit", "From inbox")
            await { viewModel.uiState.value.editing?.title == "From inbox" }
            tapClose()

            compose.onNode(hasContentDescription("Inbox. From inbox.", substring = true)).performTouchInput {
                swipeRight()
            }
            awaitSnapshot(useCases) { task(it, "From inbox").bucket == TaskBucket.TODAY }

            compose.runOnIdle { screen = "later" }
            compose.waitForIdle()
            compose.onNodeWithText("From later").performTouchInput { longClick() }
            await { viewModel.uiState.value.editing?.title == "From later" }
            tapClose()

            compose.runOnIdle { screen = "scheduled" }
            compose.waitForIdle()
            compose.onNodeWithText("From scheduled").performTouchInput { longClick() }
            await { viewModel.uiState.value.editing?.title == "From scheduled" }
            tapClose()

            compose.runOnIdle { screen = "home" }
            compose.waitForIdle()
            await { viewModel.uiState.value.todaySlots.count { it.task != null } >= 2 }
            val before = viewModel.uiState.value.todaySlots.mapNotNull { it.task?.title }
            val first = before.first()
            compose.onNodeWithText(first).performTouchInput { longClick() }
            compose.waitForIdle()
            assertNull(viewModel.uiState.value.editing)
            invokeCustomAction("Move down", first)
            await {
                val after = viewModel.uiState.value.todaySlots.mapNotNull { it.task?.title }
                after != before && after.toSet() == before.toSet()
            }
            assertNull(viewModel.uiState.value.editing)
            compose.onNodeWithText(first).performTouchInput {
                down(center)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 200)
                moveBy(Offset(0f, 180f))
                up()
            }
            compose.waitForIdle()
            assertNull(viewModel.uiState.value.editing)
        }
    }

    @Test
    fun untitledInterruptionUsesTheEditSheet() {
        withHarness { viewModel, useCases ->
            runBlocking {
                useCases.capture("Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                useCases.interrupt("")
            }
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    FocusScreen(state = state, viewModel = viewModel)
                    EditHost(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == UNTITLED_INTERRUPTION }
            compose.onNodeWithText("Save title").assertDoesNotExist()
            tap("Edit")
            await { viewModel.uiState.value.editing?.title == UNTITLED_INTERRUPTION }
            saveAs("Name this")
            await { viewModel.uiState.value.now?.title == "Name this" }
        }
    }

    @Composable
    private fun EditHost(viewModel: FocusStackViewModel) {
        val state by viewModel.uiState.collectAsState()
        state.editing?.let { editing ->
            EditTaskSheet(
                initialTitle = editing.title,
                onDismiss = viewModel::dismissEdit,
                onSave = viewModel::saveEdit,
                recognizer = SilentRecognizer,
            )
        }
    }

    private fun withHarness(block: (FocusStackViewModel, FocusUseCases) -> Unit) {
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
                    preferencesStore = PreferencesDataStore(context, "edit-surfaces-${UUID.randomUUID()}"),
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
            block(viewModel, useCases)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun seedOpenTasks(useCases: FocusUseCases) {
        runBlocking {
            useCases.capture("Hold this")
            useCases.start(idOf(useCases, "Hold this"))
            useCases.capture("Park me")
            useCases.promote(idOf(useCases, "Park me"))
            useCases.capture("From inbox")
            useCases.captureLater("From later")
            useCases.captureScheduled("From scheduled", LocalDate.now().plusDays(3).toEpochDay())
            useCases.captureCountdown("From countdown", LocalDate.now().plusDays(12).toEpochDay())
            useCases.capture("From done")
            useCases.completeTask(idOf(useCases, "From done"))
        }
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun task(
        snapshot: FocusSnapshot,
        title: String,
    ) = snapshot.tasks.single { it.title == title }

    private fun tapEditNear(title: String) {
        nearest(compose.onAllNodesWithText("Edit"), title).assertIsEnabled().assertHasClickAction()
        nearest(compose.onAllNodesWithText("Edit"), title).performTouchInput { click() }
        compose.waitForIdle()
        await { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveAs(title: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(title)
        tap("Save")
        await { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
    }

    private fun tapClose() {
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText(label).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun invokeCustomAction(
        label: String,
        nearTitle: String,
    ) {
        val titleTop = compose.onNodeWithText(nearTitle).fetchSemanticsNode().boundsInRoot.top
        val matcher =
            SemanticsMatcher("has $label") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
            }
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
        val found = nodes.fetchSemanticsNodes()
        check(found.isNotEmpty()) { "No $label action near $nearTitle" }
        val index = found.indices.minBy { kotlin.math.abs(found[it].boundsInRoot.top - titleTop) }
        nodes[index].performSemanticsAction(SemanticsActions.CustomActions) { actions ->
            actions.first { it.label == label }.action()
        }
        compose.waitForIdle()
    }

    private fun nearest(
        nodes: SemanticsNodeInteractionCollection,
        beside: String,
    ): SemanticsNodeInteraction {
        val anchor = compose.onNodeWithText(beside).fetchSemanticsNode().boundsInRoot.top
        val found = nodes.fetchSemanticsNodes()
        val index = found.indices.minBy { kotlin.math.abs(found[it].boundsInRoot.top - anchor) }
        return nodes[index]
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

    private object SilentRecognizer : UtteranceRecognizer {
        override fun available(): Boolean = false

        override fun listen(
            onPartial: (String) -> Unit,
            onFinished: (String) -> Unit,
            onProblem: (SpeechProblem) -> Unit,
        ): Boolean = false

        override fun cancel() = Unit
    }
}
