// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

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
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithText
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
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.theme.FocusStackTheme

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InterruptButtonTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun interruptTapsReachTheViewModel() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = FakeAppClock()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "interrupt-tap-${UUID.randomUUID()}"),
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
                        return FocusStackViewModel(useCases, clock = clock) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            runBlocking {
                useCases.capture("Hold this")
                val hold = useCases.observeSnapshot().first().tasks.single { it.title == "Hold this" }
                useCases.start(hold.id)
                useCases.capture("From inbox")
            }

            var screen by mutableStateOf("capture")
            compose.setContent {
                val state by viewModel.uiState.collectAsState()
                FocusStackTheme {
                    when (screen) {
                        "focus" -> FocusScreen(state = state, viewModel = viewModel)
                        "inbox" ->
                            InboxScreen(
                                tasks = state.inboxTasks,
                                ready = state.ready,
                                canInterrupt = state.canInterrupt,
                                onBack = {},
                                onToday = {},
                                onStart = {},
                                onInterrupt = viewModel::interruptFromPile,
                                onEdit = {},
                            )
                        else ->
                            CaptureSheet(
                                phrases = emptyList(),
                                frameActive = state.now != null,
                                today = clock.localDate(),
                                onDismiss = {},
                                onSaveToInbox = viewModel::saveToInbox,
                                onInterruptNow = viewModel::interruptNow,
                                onPlaceToday = viewModel::placeOnToday,
                                onPlaceStart = viewModel::placeAndStart,
                                onPlaceLater = viewModel::placeLater,
                                onSchedule = viewModel::placeScheduled,
                                recognizer = SilentRecognizer,
                            )
                    }
                }
            }
            await { viewModel.uiState.value.now != null }
            compose.onNode(hasSetTextAction()).performTextInput("Quick note")
            tap("Interrupt now")
            awaitSnapshot(useCases) { snapshot -> activeTitle(snapshot) == "Quick note" }

            show("Switch") { screen = "focus" }
            tap("Interrupt")
            await { viewModel.uiState.value.captureOpen && viewModel.uiState.value.captureInterrupts }
            viewModel.dismissCapture()
            await { !viewModel.uiState.value.captureOpen }

            show("From inbox") { screen = "inbox" }
            tap("Interrupt", beside = "From inbox")
            awaitSnapshot(useCases) { snapshot ->
                activeTitle(snapshot) == "From inbox" &&
                    snapshot.tasks.single { it.title == "From inbox" }.bucket == TaskBucket.INTERRUPTION
            }
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun show(
        label: String,
        select: () -> Unit,
    ) {
        compose.runOnIdle(select)
        compose.waitForIdle()
        compose.onNodeWithText(label, substring = true).assertExists()
    }

    private fun tap(
        label: String,
        beside: String? = null,
    ) {
        val node =
            if (beside == null) {
                compose.onNodeWithText(label)
            } else {
                nearest(compose.onAllNodesWithText(label), beside)
            }
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
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
        await {
            runBlocking { predicate(useCases.observeSnapshot().first()) }
        }
    }

    private fun activeTitle(snapshot: FocusSnapshot): String? {
        val active = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE } ?: return null
        return snapshot.tasks.singleOrNull { it.id == active.taskId }?.title
    }
}

private object SilentRecognizer : UtteranceRecognizer {
    override fun available(): Boolean = false

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (app.backpin.focus.ui.capture.SpeechProblem) -> Unit,
    ): Boolean = false

    override fun cancel() = Unit
}
