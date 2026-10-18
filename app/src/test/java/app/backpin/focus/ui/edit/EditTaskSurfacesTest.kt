// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.edit

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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.capture.CaptureEdit
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.SpeechProblem
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.countdown.CountdownScreen
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.home.ComingUpScreen
import app.backpin.focus.ui.home.DoneScreen
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.home.InboxScreen
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditTaskSurfacesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun editOpensFromEachOpenSurfaceAndSaveRenames() {
        withHarness { viewModel, useCases, clock ->
            seedOpenTasks(useCases, clock)
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
                        "coming" ->
                            ComingUpScreen(
                                scheduledTasks = state.scheduledTasks,
                                countdowns = state.countdowns,
                                ready = state.ready,
                                today = clock.localDate(),
                                countdownUnit = CountdownUnit.DAYS,
                                onEdit = viewModel::openEdit,
                                onOpenSheet = {},
                                onOpenMissed = {},
                                onAddCountdown = { _, _ -> },
                                onChangeCountdownDate = { _, _ -> },
                            )
                        "countdown" ->
                            CountdownScreen(
                                countdowns = state.countdowns,
                                ready = state.ready,
                                onAdd = { _, _ -> },
                                onChangeDate = { _, _ -> },
                                onRemove = {},
                                onEdit = viewModel::openEdit,
                                today = clock.localDate(),
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

            invokeCustomAction("Edit", "Hold this")
            saveTitleAs("Now renamed")
            await { viewModel.uiState.value.now?.title == "Now renamed" }
            await { viewModel.uiState.value.todaySlots.any { it.task?.title == "Now renamed" } }
            assertNull(viewModel.uiState.value.editing)

            invokeCustomAction("Edit", "Park me")
            saveTitleAs("Today renamed")
            awaitSnapshot(useCases) { hasTask(it, "Today renamed", TaskBucket.TODAY) }

            compose.runOnIdle { screen = "focus" }
            compose.waitForIdle()
            tap("Edit")
            saveTitleAs("Focus renamed")
            await { viewModel.uiState.value.now?.title == "Focus renamed" }

            compose.runOnIdle { screen = "inbox" }
            compose.waitForIdle()
            tapEditNear("From inbox")
            saveTitleAs("Inbox renamed")
            awaitSnapshot(useCases) { hasTask(it, "Inbox renamed", TaskBucket.INBOX) }

            compose.runOnIdle { screen = "coming" }
            compose.waitForIdle()
            await { compose.onAllNodesWithText("From scheduled").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("From scheduled")).performTouchInput { longClick() }
            await { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            saveTitleAs("Scheduled renamed")
            awaitSnapshot(useCases) { hasTask(it, "Scheduled renamed", TaskBucket.SCHEDULED) }

            compose.runOnIdle { screen = "countdown" }
            compose.waitForIdle()
            tapEditNear("From countdown")
            saveTitleAs("Countdown renamed")
            awaitSnapshot(useCases) { hasTask(it, "Countdown renamed", TaskBucket.COUNTDOWN) }

            compose.runOnIdle { screen = "done" }
            compose.waitForIdle()
            compose.onNode(hasText("From done")).assertExists()
            compose.onNode(hasText("Edit")).assertDoesNotExist()
        }
    }

    @Test
    fun longPressDoesNotEditPilesAndTodayStillReorders() {
        withHarness { viewModel, useCases, clock ->
            seedOpenTasks(useCases, clock)
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
                        else ->
                            HomeScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenFocus = {},
                                onOpenInbox = {},
                                onOpenScheduled = {},
                                onOpenDone = {},
                            )
                    }
                    EditHost(viewModel)
                }
            }
            await { viewModel.uiState.value.inboxTasks.any { it.title == "From inbox" } }

            compose.onNode(hasText("From inbox")).performTouchInput { longClick() }
            compose.waitForIdle()
            assertNull(viewModel.uiState.value.editing)
            compose.onNode(hasText("Done")).assertDoesNotExist()

            invokeCustomAction("Edit", "From inbox")
            await { viewModel.uiState.value.editing?.title == "From inbox" }
            tapDone()
            await { viewModel.uiState.value.editing == null }

            compose.onNode(hasContentDescription("Inbox. From inbox.", substring = true)).performTouchInput {
                swipeRight()
            }
            awaitSnapshot(useCases) { hasTask(it, "From inbox", TaskBucket.TODAY) }

            compose.runOnIdle { screen = "home" }
            compose.waitForIdle()
            await { viewModel.uiState.value.todaySlots.count { it.role == SlotRole.OPEN && it.task != null } >= 1 }
            val before = viewModel.uiState.value.todaySlots.mapNotNull { it.task?.title }
            val openTitle =
                viewModel.uiState.value.todaySlots
                    .first { it.role == SlotRole.OPEN && it.task != null }
                    .task!!
                    .title
            todayRow(openTitle).performTouchInput { longClick() }
            compose.waitForIdle()
            assertNull(viewModel.uiState.value.editing)
            invokeCustomAction("Move down", openTitle)
            await {
                val after = viewModel.uiState.value.todaySlots.mapNotNull { it.task?.title }
                after != before && after.toSet() == before.toSet()
            }
            assertNull(viewModel.uiState.value.editing)
            todayRow(openTitle).performTouchInput {
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
        withHarness { viewModel, useCases, clock ->
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
            compose.onNode(hasText("Save title")).assertDoesNotExist()
            tap("Edit")
            await { viewModel.uiState.value.editing?.title == UNTITLED_INTERRUPTION }
            saveTitleAs("Name this")
            await { viewModel.uiState.value.now?.title == "Name this" }
        }
    }

    @Test
    fun backSavesABlankKeepsTheTitleAndPlaceChangesMoveTheTask() {
        withHarness { viewModel, useCases, clock ->
            seedOpenTasks(useCases, clock)
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
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
                    EditHost(viewModel)
                }
            }
            await { viewModel.uiState.value.inboxTasks.any { it.title == "From inbox" } }

            tapEditNear("From inbox")
            titleField().performTextReplacement("Inbox kept by back")
            compose.onNode(hasContentDescription("Close")).performTouchInput { click() }
            compose.waitForIdle()
            awaitSnapshot(useCases) { hasTask(it, "Inbox kept by back", TaskBucket.INBOX) }
            await { compose.onAllNodesWithText("Inbox kept by back").fetchSemanticsNodes().isNotEmpty() }

            tapEditNear("Inbox kept by back")
            titleField().performTextReplacement("   ")
            tapDone()
            awaitSnapshot(useCases) { it.tasks.any { task -> task.title == "Inbox kept by back" } }
            awaitSnapshot(useCases) { it.tasks.none { task -> task.title.isBlank() } }

            tapEditNear("Inbox kept by back")
            tap("Later")
            tapDone()
            awaitSnapshot(useCases) { hasTask(it, "Inbox kept by back", TaskBucket.LATER) }

            compose.runOnIdle { viewModel.openEdit(runBlocking { idOf(useCases, "From later") }) }
            await { viewModel.uiState.value.editing?.title == "From later" }
            tap("Schedule")
            tap("Tomorrow")
            val tomorrow = clock.localDate().plusDays(1).toEpochDay()
            awaitSnapshot(useCases) {
                it.tasks.any { task ->
                    task.title == "From later" &&
                        task.bucket == TaskBucket.SCHEDULED &&
                        task.scheduledOnEpochDay == tomorrow
                }
            }
        }
    }

    @Composable
    private fun EditHost(viewModel: FocusStackViewModel) {
        val state by viewModel.uiState.collectAsState()
        state.editing?.let { editing ->
            CaptureSheet(
                phrases = emptyList(),
                frameActive = state.now != null,
                onDismiss = viewModel::dismissEdit,
                onSaveToInbox = {},
                onInterruptNow = {},
                onPlaceToday = {},
                onPlaceStart = {},
                onPlaceLater = {},
                onSchedule = { _, _, _ -> },
                edit =
                CaptureEdit(
                    title = editing.title,
                    place = editing.place,
                    scheduledEpochDay = editing.scheduledOnEpochDay,
                ),
                onCommitEdit = viewModel::saveEdit,
                recognizer = SilentRecognizer,
            )
        }
    }

    private fun withHarness(block: (FocusStackViewModel, FocusUseCases, FakeAppClock) -> Unit) {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                block(viewModel, fixture.useCases, fixture.clock)
            }
        }
    }

    private fun seedOpenTasks(
        useCases: FocusUseCases,
        clock: FakeAppClock,
    ) {
        runBlocking {
            useCases.capture("Hold this")
            useCases.start(idOf(useCases, "Hold this"))
            useCases.capture("Park me")
            useCases.promote(idOf(useCases, "Park me"))
            useCases.capture("From inbox")
            useCases.captureLater("From later")
            useCases.captureScheduled("From scheduled", clock.localDate().plusDays(3).toEpochDay())
            useCases.captureCountdown("From countdown", clock.localDate().plusDays(12).toEpochDay())
            useCases.capture("From done")
            useCases.completeTask(idOf(useCases, "From done"))
        }
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun hasTask(
        snapshot: FocusSnapshot,
        title: String,
        bucket: TaskBucket,
    ): Boolean = snapshot.tasks.any { it.title == title && it.bucket == bucket }

    private fun tapEditNear(title: String) {
        await { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        nearest(compose.onAllNodesWithText("Edit"), title).assertIsEnabled().assertHasClickAction()
        nearest(compose.onAllNodesWithText("Edit"), title).performTouchInput { click() }
        compose.waitForIdle()
        await { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveTitleAs(title: String) {
        titleField().performTextReplacement(title)
        tapDone()
    }

    private fun titleField() = compose.onNode(hasSetTextAction() and hasText("Task"))

    private fun tapDone() {
        val nodes = compose.onAllNodesWithText("Done")
        val found = nodes.fetchSemanticsNodes()
        check(found.isNotEmpty()) { "No Done button" }
        val index = found.indices.maxBy { found[it].boundsInRoot.height }
        nodes[index].assertIsEnabled().assertHasClickAction().performTouchInput { click() }
        compose.waitForIdle()
        await { compose.onAllNodes(hasSetTextAction() and hasText("Task")).fetchSemanticsNodes().isEmpty() }
    }

    private fun todayRow(title: String) = compose.onNode(
        hasContentDescription("Today position", substring = true) and hasContentDescription(title, substring = true),
    )

    private fun tap(label: String) {
        compose.onNode(hasText(label)).assertIsEnabled().assertHasClickAction()
        compose.onNode(hasText(label)).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun invokeCustomAction(
        label: String,
        nearTitle: String,
    ) {
        val titles = compose.onAllNodesWithText(nearTitle).fetchSemanticsNodes()
        check(titles.isNotEmpty()) { "No title $nearTitle" }
        val titleTop = titles.minBy { it.boundsInRoot.top }.boundsInRoot.top
        val matcher =
            SemanticsMatcher("has $label") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
            }
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
        val found = nodes.fetchSemanticsNodes()
        check(found.isNotEmpty()) { "No $label action near $nearTitle" }
        val index = found.indices.minBy { kotlin.math.abs(found[it].boundsInRoot.top - titleTop) }
        val action =
            found[index].config[SemanticsActions.CustomActions].single { it.label == label }
        compose.runOnIdle { check(action.action()) }
        compose.waitForIdle()
    }

    private fun nearest(
        nodes: SemanticsNodeInteractionCollection,
        beside: String,
    ): SemanticsNodeInteraction {
        val titles = compose.onAllNodesWithText(beside).fetchSemanticsNodes()
        check(titles.isNotEmpty()) { "No title $beside" }
        val anchor = titles.minBy { it.boundsInRoot.top }.boundsInRoot.top
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
