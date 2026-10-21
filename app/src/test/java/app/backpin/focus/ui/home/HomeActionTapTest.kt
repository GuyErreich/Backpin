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
import androidx.compose.ui.test.swipeRight
import app.backpin.focus.domain.HomeLayout
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
    fun swipeLeftOnTheHeadOpensInterrupt() {
        showHomeWithNowAndToday { viewModel ->
            compose.onNodeWithText("Later").assertDoesNotExist()
            swipeLeftOn(hasContentDescription("NOW. Hold this", substring = true))
            await { viewModel.uiState.value.captureOpen && viewModel.uiState.value.captureInterrupts }
            compose.onNodeWithText("Bring it back in…").assertDoesNotExist()
        }
    }

    @Test
    fun focusedTaskLeavesTheListAndReturnsWhenAnotherRowInterrupts() {
        showHomeWithNowAndToday { viewModel ->
            await { textCount("Hold this") == 1 }
            compose.onNodeWithText("Park me").assertExists()
            swipeLeftOn(hasContentDescription("Park me", substring = true))
            await {
                viewModel.uiState.value.now?.title == "Park me" &&
                    textCount("Park me") == 1 &&
                    textCount("Hold this") == 1
            }
        }
    }

    @Test
    fun focusedTaskLeavesTheBandAndReturnsWhenAnotherRowInterrupts() {
        showHomeWithNowAndToday(HomeLayout.BANDS) { viewModel ->
            await { textCount("Hold this") == 1 }
            compose.onNodeWithText("Park me").assertExists()
            swipeLeftOn(hasContentDescription("Park me", substring = true))
            await {
                viewModel.uiState.value.now?.title == "Park me" &&
                    textCount("Park me") == 1 &&
                    textCount("Hold this") == 1
            }
        }
    }

    @Test
    fun swipeRightStartsWhenNothingIsInFocus() {
        useTestFocus { fixture ->
            runBlocking {
                fixture.useCases.capture("Call Dana")
                val id =
                    fixture.repository
                        .observeSnapshot()
                        .first()
                        .tasks
                        .single { it.title == "Call Dana" }
                        .id
                fixture.useCases.promote(id)
            }
            fixture.withViewModel { viewModel ->
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
                await {
                    viewModel.uiState.value.now == null &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Call Dana" }
                }
                swipeRightOn(hasContentDescription("Call Dana", substring = true))
                await { viewModel.uiState.value.now?.title == "Call Dana" && textCount("Call Dana") == 1 }
            }
        }
    }

    @Test
    fun swipeRightInABandRemovesTheRowUntilItIsReplaced() {
        useTestFocus { fixture ->
            runBlocking {
                fixture.useCases.capture("Call Dana")
                val id =
                    fixture.repository
                        .observeSnapshot()
                        .first()
                        .tasks
                        .single { it.title == "Call Dana" }
                        .id
                fixture.useCases.promote(id)
            }
            fixture.withViewModel { viewModel ->
                showHome(viewModel, HomeLayout.BANDS)
                await {
                    viewModel.uiState.value.now == null &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Call Dana" } &&
                        textCount("Call Dana") == 1
                }
                swipeRightOn(hasContentDescription("Call Dana", substring = true))
                await { viewModel.uiState.value.now?.title == "Call Dana" && textCount("Call Dana") == 1 }
            }
        }
    }

    private fun showHomeWithNowAndToday(
        layout: HomeLayout = HomeLayout.FLAT,
        block: (FocusStackViewModel) -> Unit,
    ) {
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
                showHome(viewModel, layout)
                await {
                    viewModel.uiState.value.now?.title == "Hold this" &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Park me" }
                }
                block(viewModel)
            }
        }
    }

    private fun showHome(
        viewModel: FocusStackViewModel,
        layout: HomeLayout,
    ) {
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
                    homeLayout = layout,
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
    }

    private fun swipeLeftOn(matcher: SemanticsMatcher) {
        swipe(matcher) { swipeLeft() }
    }

    private fun swipeRightOn(matcher: SemanticsMatcher) {
        swipe(matcher) { swipeRight() }
    }

    private fun swipe(
        matcher: SemanticsMatcher,
        gesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit,
    ) {
        compose.waitForIdle()
        compose.onNode(matcher).performTouchInput(gesture)
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun textCount(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }
}
