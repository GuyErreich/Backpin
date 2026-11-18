// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.focus.FocusScreen
import app.backpin.focus.ui.task.TaskSheet
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The action on screen matches the job: finish what is in focus, resume a pause,
 * and name a countdown from an empty Coming up list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SurfacePrimaryActionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun activeNowCardShowsInterruptAndDone() = checkNowCard(nowCardCases().first { it.name == "active" })

    @Test
    fun emptyNowCardNamesAddAndTheSlot() = checkNowCard(nowCardCases().first { it.name == "empty" })

    @Test
    fun pausedNowCardShowsResume() = checkNowCard(nowCardCases().first { it.name == "paused" })

    private fun checkNowCard(case: NowCardCase) {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking { case.seed(fixture.useCases) }
                showHome(viewModel)
                await { viewModel.uiState.value.ready && case.ready(viewModel.uiState.value) }
                case.present.forEach { label ->
                    compose.onNodeWithText(label).assertExists()
                }
                case.absent.forEach { label ->
                    compose.onNodeWithText(label).assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun doneOnTheNowCardFinishesTheTask() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking {
                    fixture.useCases.capture("Call the school")
                    fixture.useCases.start(idOf(fixture.useCases, "Call the school"))
                }
                showHome(viewModel)
                await { viewModel.uiState.value.now?.title == "Call the school" }
                tap("Done")
                await { viewModel.uiState.value.now == null }
            }
        }
    }

    @Test
    fun interruptOnTheNowCardOpensCapture() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking {
                    fixture.useCases.capture("Call the school")
                    fixture.useCases.start(idOf(fixture.useCases, "Call the school"))
                }
                showHome(viewModel)
                await { viewModel.uiState.value.now?.title == "Call the school" }
                tap("Interrupt")
                await { viewModel.uiState.value.captureOpen && viewModel.uiState.value.captureInterrupts }
            }
        }
    }

    @Test
    fun resumeOnAPausedTaskStartsItAgain() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking {
                    fixture.useCases.capture("Call the school")
                    fixture.useCases.start(idOf(fixture.useCases, "Call the school"))
                    fixture.useCases.pauseActive()
                }
                showHome(viewModel)
                await { viewModel.uiState.value.paused?.title == "Call the school" }
                tap("Resume")
                await { viewModel.uiState.value.now?.title == "Call the school" }
            }
        }
    }

    @Test
    fun focusKeepsDoneAndMovesPauseSwitchAndEditToTheSheet() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking {
                    fixture.useCases.capture("Call the school")
                    fixture.useCases.start(idOf(fixture.useCases, "Call the school"))
                    fixture.useCases.capture("Buy milk")
                    fixture.useCases.promote(idOf(fixture.useCases, "Buy milk"))
                }
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    val sheet by viewModel.taskSheet.collectAsState()
                    FocusStackTheme {
                        FocusScreen(state = state, viewModel = viewModel)
                        sheet?.let { open ->
                            TaskSheet(
                                state = open,
                                onDismiss = viewModel::dismissTaskSheet,
                                onStart = viewModel::startFromTaskSheet,
                                onDone = viewModel::doneFromTaskSheet,
                                onEdit = viewModel::editFromTaskSheet,
                                onInterrupt = viewModel::interruptFromTaskSheet,
                                onToday = viewModel::todayFromTaskSheet,
                                onResume = viewModel::resumeFromTaskSheet,
                                onBringBack = viewModel::bringBackFromTaskSheet,
                                onPause = viewModel::pauseFromTaskSheet,
                                onSwitch = viewModel::switchFromTaskSheet,
                            )
                        }
                    }
                }
                await { viewModel.uiState.value.now?.title == "Call the school" }
                compose.onNodeWithText("Done").assertExists()
                compose.onNodeWithText("Interrupt").assertExists()
                compose.onNodeWithText("Pause").assertDoesNotExist()
                compose.onNodeWithText("Switch").assertDoesNotExist()
                compose.onNodeWithText("Edit").assertDoesNotExist()
                compose.onNode(isHeading()).performTouchInput { longClick() }
                await { viewModel.taskSheet.value?.title == "Call the school" }
                compose.onNodeWithText("Pause").assertExists()
                tap("Pause")
                await { viewModel.uiState.value.now == null && viewModel.taskSheet.value == null }
            }
        }
    }

    @Test
    fun phraseChipsMeetTheCardTouchHeight() {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = listOf("Talk to my wife"),
                    onDismiss = {},
                )
            }
        }
        val bounds = compose.onNodeWithText("Talk to my wife").getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("phrase chip is $height tall", height >= 48.dp - 0.5.dp)
    }

    private fun showHome(viewModel: FocusStackViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            FocusStackTheme {
                HomeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenFocus = {},
                    onOpenInbox = {},
                    onOpenScheduled = {},
                    onOpenDone = {},
                )
            }
        }
    }

    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }
}

private data class NowCardCase(
    val name: String,
    val seed: suspend (FocusUseCases) -> Unit,
    val ready: (FocusUiState) -> Boolean,
    val present: List<String>,
    val absent: List<String>,
)

private fun nowCardCases(): List<NowCardCase> = listOf(
    NowCardCase(
        name = "active",
        seed = { useCases ->
            useCases.capture("Call the school")
            useCases.start(idOf(useCases, "Call the school"))
        },
        ready = { it.now?.title == "Call the school" },
        present = listOf("Interrupt", "Done"),
        absent = listOf("Resume"),
    ),
    NowCardCase(
        name = "empty",
        seed = {},
        ready = { it.now == null && it.paused == null && it.recovery == null },
        present = listOf("Use + Add, or fill an empty Today slot."),
        absent = listOf("Done", "Interrupt", "Resume"),
    ),
    NowCardCase(
        name = "paused",
        seed = { useCases ->
            useCases.capture("Call the school")
            useCases.start(idOf(useCases, "Call the school"))
            useCases.pauseActive()
        },
        ready = { it.now == null && it.paused?.title == "Call the school" },
        present = listOf("Resume"),
        absent = listOf("Done", "Interrupt", "Start"),
    ),
)

private suspend fun idOf(
    useCases: FocusUseCases,
    title: String,
): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id
