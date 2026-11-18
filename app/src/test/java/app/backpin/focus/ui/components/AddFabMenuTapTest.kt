// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.R
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AddFabMenuTapTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longPressShowsMenuOnlyWhileATaskIsActiveAndEachItemRuns() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                    @Suppress("UNUSED_EXPRESSION")
                    state.now
                }
                await("ready") { viewModel.uiState.value.ready }
                val add = compose.activity.getString(R.string.action_add)
                compose.onNodeWithText(add).performTouchInput { longClick() }
                pump()
                assertFalse(
                    compose
                        .onAllNodesWithText(compose.activity.getString(R.string.add_fab_menu_interrupt))
                        .fetchSemanticsNodes()
                        .isNotEmpty(),
                )

                mainDispatcherRule.runTest {
                    val today = fixture.seedFullToday().first()
                    fixture.useCases.start(today.id)
                }
                await("now active") { viewModel.uiState.value.now != null }

                compose.onNodeWithText(add).performTouchInput { longClick() }
                await("menu open") {
                    compose
                        .onAllNodesWithText(compose.activity.getString(R.string.add_fab_menu_interrupt))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                tap(compose.activity.getString(R.string.add_fab_menu_interrupt))
                await("interrupt capture") {
                    viewModel.uiState.value.captureOpen && viewModel.uiState.value.captureInterrupts
                }

                compose.runOnIdle { viewModel.dismissCapture() }
                await("capture closed") { !viewModel.uiState.value.captureOpen }

                compose.onNodeWithText(add).performTouchInput { longClick() }
                val addSubtitle = compose.activity.getString(R.string.add_fab_menu_add_subtitle)
                await("menu open again") {
                    compose.onAllNodesWithText(addSubtitle).fetchSemanticsNodes().isNotEmpty()
                }
                tap(addSubtitle)
                await("plain capture") {
                    viewModel.uiState.value.captureOpen && !viewModel.uiState.value.captureInterrupts
                }
                assertTrue(viewModel.uiState.value.now != null)
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
