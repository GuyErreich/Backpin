// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.R
import app.backpin.focus.domain.FrameState
import app.backpin.focus.runOffTestThread
import app.backpin.focus.testing.expectedStartRedirectInterruptionTitle
import app.backpin.focus.testing.expectedStartRedirectParentTitle
import app.backpin.focus.testing.nestInterruptChain
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class StartRedirectSnackbarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun twoLevelSnackbarNamesTheChildAndItsDirectParent() {
        assertSnackbarNamesDirectParent(
            titles = listOf("Write the brief", "Call the school"),
        )
    }

    @Test
    fun threeLevelSnackbarNamesTheDeepestChildAndItsDirectParent() {
        assertSnackbarNamesDirectParent(
            titles = listOf("Write the brief", "Call the school", "Ping the vendor"),
        )
    }

    @Test
    fun dismissingTheSnackbarClearsStartRedirect() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                val titles = listOf("Write the brief", "Call the school", "Ping the vendor")
                val chain = runOffTestThread { fixture.nestInterruptChain(titles) }
                showApp(viewModel)
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.now?.taskId == chain.last().id
                }
                openFocusThenStart(chain.first().title)
                val message = redirectMessage(titles)
                awaitText(message)
                awaitUi(viewModel) { it.startRedirect != null }
                dismissSnackbar(message)
                awaitUi(viewModel) { it.startRedirect == null }
                awaitGone(message)
                assertNull(viewModel.uiState.value.startRedirect)
                assertEquals(chain.last().id, viewModel.uiState.value.now?.taskId)
            }
        }
    }

    @Test
    fun snackbarTimeoutClearsStartRedirectOnTheTestClock() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                val titles = listOf("Write the brief", "Call the school", "Ping the vendor")
                val chain = runOffTestThread { fixture.nestInterruptChain(titles) }
                showApp(viewModel)
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.now?.taskId == chain.last().id
                }
                openFocusFromNowCard()
                openSwitchFromFocus()
                awaitText(chain.first().title)
                compose.mainClock.autoAdvance = false
                tap(chain.first().title)
                val message = redirectMessage(titles)
                awaitOnClock { nodes(message, unmerged = true).isNotEmpty() || nodes(message, unmerged = false).isNotEmpty() }
                awaitUiOnClock(viewModel) { it.startRedirect != null }
                compose.mainClock.advanceTimeBy(SNACKBAR_SHORT_MS)
                compose.mainClock.advanceTimeByFrame()
                shadowOf(Looper.getMainLooper()).idle()
                compose.mainClock.autoAdvance = true
                awaitUi(viewModel) { it.startRedirect == null }
                awaitGone(message)
                assertNull(viewModel.uiState.value.startRedirect)
                assertEquals(chain.last().id, viewModel.uiState.value.now?.taskId)
            }
        }
    }

    private fun assertSnackbarNamesDirectParent(titles: List<String>) {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                val chain = runOffTestThread { fixture.nestInterruptChain(titles) }
                showApp(viewModel)
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.now?.taskId == chain.last().id
                }
                openFocusThenStart(chain.first().title)
                val message = redirectMessage(titles)
                awaitText(message)
                awaitUi(viewModel) { state ->
                    val now = state.now
                    state.startRedirect != null &&
                        now != null &&
                        now.taskId == chain.last().id &&
                        now.title == expectedStartRedirectInterruptionTitle(titles)
                }
                val redirect = viewModel.uiState.value.startRedirect!!
                assertEquals(chain.last().id, redirect.interruptionId)
                assertEquals(expectedStartRedirectParentTitle(titles), redirect.parentTitle)
                assertEquals(chain[chain.lastIndex - 1].id, redirect.parentId)
                val snapshot = runOffTestThread {
                    fixture.repository.observeSnapshot().first()
                }
                assertEquals(
                    chain.last().id,
                    snapshot.frames.single { it.state == FrameState.ACTIVE }.taskId,
                )
            }
        }
    }

    private fun redirectMessage(titles: List<String>): String {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return context.getString(
            R.string.start_redirect_message,
            expectedStartRedirectInterruptionTitle(titles),
            expectedStartRedirectParentTitle(titles),
        )
    }

    private fun showApp(viewModel: FocusStackViewModel) {
        compose.setContent {
            FocusStackTheme {
                FocusApp(viewModel)
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun openFocusThenStart(title: String) {
        openFocusFromNowCard()
        tapStartOnParent(title)
    }

    private fun openFocusFromNowCard() {
        compose.waitForIdle()
        val now = compose.onNode(hasContentDescription("Opens focus.", substring = true))
        now.assertHasClickAction()
        now.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        await {
            compose.onAllNodes(hasText("Focus") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Focus").assertIsSelected()
    }

    private fun tapStartOnParent(title: String) {
        openSwitchFromFocus()
        awaitText(title)
        tap(title)
    }

    private fun openSwitchFromFocus() {
        compose.onNode(isHeading()).performTouchInput { longClick() }
        awaitText("Switch")
        tap("Switch")
    }

    private fun tap(label: String) {
        awaitText(label)
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dismissSnackbar(message: String) {
        val unmerged = nodes(message, unmerged = true).isNotEmpty()
        val node = compose.onNodeWithText(message, useUnmergedTree = unmerged)
        val semantics = node.fetchSemanticsNode()
        if (semantics.config.contains(SemanticsActions.Dismiss)) {
            node.performSemanticsAction(SemanticsActions.Dismiss)
        } else {
            node.performTouchInput { swipeLeft() }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun awaitText(text: String) {
        await {
            nodes(text, unmerged = false).isNotEmpty() || nodes(text, unmerged = true).isNotEmpty()
        }
        val unmerged = nodes(text, unmerged = true).isNotEmpty() && nodes(text, unmerged = false).isEmpty()
        compose.onNodeWithText(text, useUnmergedTree = unmerged).assertExists()
    }

    private fun awaitGone(text: String) {
        await {
            nodes(text, unmerged = false).isEmpty() && nodes(text, unmerged = true).isEmpty()
        }
    }

    private fun nodes(
        text: String,
        unmerged: Boolean,
    ) = compose.onAllNodesWithText(text, useUnmergedTree = unmerged).fetchSemanticsNodes()

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitOnClock(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitUi(
        viewModel: FocusStackViewModel,
        predicate: (FocusUiState) -> Boolean,
    ): FocusUiState {
        await { predicate(viewModel.uiState.value) }
        return viewModel.uiState.value
    }

    private fun awaitUiOnClock(
        viewModel: FocusStackViewModel,
        predicate: (FocusUiState) -> Boolean,
    ): FocusUiState {
        awaitOnClock { predicate(viewModel.uiState.value) }
        return viewModel.uiState.value
    }

    private companion object {
        const val SNACKBAR_SHORT_MS = 4_000L
    }
}
