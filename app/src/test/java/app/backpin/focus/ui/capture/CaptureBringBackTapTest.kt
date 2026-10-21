// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.R
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CaptureBringBackTapTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bringBackSavesTheTitleToInboxAndOpensTheWheel() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                compose.runOnIdle { viewModel.openCapture() }
                await("capture open") { viewModel.uiState.value.captureOpen }
                compose.onNode(hasSetTextAction()).performTextInput("Call Dana")
                tap(compose.activity.getString(R.string.action_bring_back))
                await("Bring back target") { viewModel.bringBack.value?.title == "Call Dana" }
                await("Bring back sheet") {
                    compose.onAllNodesWithText("Bring it back in…", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose.onNodeWithText("When should it come back?").assertDoesNotExist()
                compose.onNodeWithText("Schedule for", substring = true).assertDoesNotExist()
                compose.onNodeWithText("Tomorrow").assertDoesNotExist()
                val inbox =
                    kotlinx.coroutines.runBlocking {
                        fixture.useCases.observeSnapshot().first().tasks.single { it.title == "Call Dana" }
                    }
                assertEquals(TaskBucket.INBOX, inbox.bucket)
            }
        }
    }

    @Test
    fun emptyTitleDoesNotOpenBringBack() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                compose.runOnIdle { viewModel.openCapture() }
                await("capture open") { viewModel.uiState.value.captureOpen }
                tap(compose.activity.getString(R.string.action_bring_back))
                await("still capturing") { viewModel.uiState.value.captureOpen && viewModel.bringBack.value == null }
                compose.onNodeWithText("Bring it back in…").assertDoesNotExist()
                compose.onNodeWithText("Name it.").assertExists()
            }
        }
    }

    @Test
    fun pickADateOnTheBringBackCalendarSchedulesOnceAndCloses() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                compose.runOnIdle { viewModel.openCapture() }
                await("capture open") { viewModel.uiState.value.captureOpen }
                compose.onNode(hasSetTextAction()).performTextInput("Book the room")
                tap(compose.activity.getString(R.string.action_bring_back))
                await("Bring back sheet") {
                    compose.onAllNodesWithText("Bring it back in…", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                tap(compose.activity.getString(R.string.bring_back_pick_a_date))
                await("calendar page") {
                    compose
                        .onAllNodesWithText(
                            compose.activity.getString(R.string.bring_back_pick_a_date_title),
                            useUnmergedTree = true,
                        )
                        .fetchSemanticsNodes()
                        .isNotEmpty() &&
                        compose
                            .onAllNodesWithText("Bring it back in…", useUnmergedTree = true)
                            .fetchSemanticsNodes()
                            .isEmpty()
                }
                val picked = fixture.clock.localDate().plusDays(4)
                tap(picked.dayOfMonth.toString())
                val saveLabel =
                    compose.activity.getString(
                        R.string.bring_back_save_on_date,
                        scheduleMomentLabel(picked.toEpochDay(), null, Locale.getDefault()),
                    )
                tap(saveLabel)
                await("Book the room scheduled") {
                    kotlinx.coroutines.runBlocking {
                        val task =
                            fixture.useCases
                                .observeSnapshot()
                                .first()
                                .tasks
                                .singleOrNull { it.title == "Book the room" }
                        task != null &&
                            task.bucket == TaskBucket.SCHEDULED &&
                            task.scheduledOnEpochDay == picked.toEpochDay()
                    }
                }
                assertEquals(null, viewModel.bringBack.value)
                compose.onNodeWithText("Bring it back in…").assertDoesNotExist()
            }
        }
    }

    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click() }
        pump()
    }

    private fun await(
        step: String,
        predicate: () -> Boolean,
    ) {
        compose.waitUntil(conditionDescription = step, timeoutMillis = 5_000) {
            pump()
            predicate()
        }
    }

    private fun pump() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        mainDispatcherRule.scheduler.advanceUntilIdle()
    }
}
