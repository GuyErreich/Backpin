// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.capture.BringBackSheet
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeActionTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeLeftOnNowOpensBringBack() {
        showHomeWithNowAndToday { viewModel ->
            compose.onNodeWithText("Later").assertDoesNotExist()
            swipeLeftOn(hasContentDescription("NOW. Hold this", substring = true))
            await { viewModel.bringBack.value?.title == "Hold this" }
            awaitSheet("Bring it back in…")
        }
    }

    @Test
    fun swipeLeftOnTodayOpensBringBack() {
        showHomeWithNowAndToday { viewModel ->
            compose.onNodeWithText("Later").assertDoesNotExist()
            swipeLeftOn(hasContentDescription("Park me", substring = true))
            await { viewModel.bringBack.value?.title == "Park me" }
            awaitSheet("Bring it back in…")
        }
    }

    private fun showHomeWithNowAndToday(block: (FocusStackViewModel) -> Unit) {
        useTestFocus { fixture ->
            runBlocking {
                fixture.seedFullToday(listOf("Hold this", "Park me"))
                val hold =
                    fixture.repository
                        .observeSnapshot()
                        .first()
                        .tasks
                        .single { it.title == "Hold this" }
                fixture.useCases.start(hold.id)
            }
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    val bringBack by viewModel.bringBack.collectAsState()
                    FocusStackTheme {
                        HomeScreen(
                            state = state,
                            viewModel = viewModel,
                            onOpenFocus = {},
                            onOpenInbox = {},
                            onOpenScheduled = {},
                            onOpenDone = {},
                        )
                        bringBack?.let { target ->
                            val today = state.today
                            val isEvent =
                                state.now?.let { it.taskId == target.taskId && it.isEvent } == true
                            BringBackSheet(
                                onDismiss = viewModel::dismissBringBack,
                                onDate = {},
                                onKeepInInbox = {},
                                today = today,
                                earliest = if (isEvent) today.plusDays(1) else null,
                            )
                        }
                    }
                }
                await {
                    viewModel.uiState.value.now?.title == "Hold this" &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Park me" }
                }
                block(viewModel)
            }
        }
    }

    private fun swipeLeftOn(matcher: SemanticsMatcher) {
        compose.waitForIdle()
        compose.onNode(matcher).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitSheet(title: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }
}
