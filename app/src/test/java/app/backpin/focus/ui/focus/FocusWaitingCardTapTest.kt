// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.focus

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
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
class FocusWaitingCardTapTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun waitingCardShowsBringBackAndOpensTheSheet() {
        useTestFocus { fixture ->
            runBlocking {
                fixture.useCases.capture("Hold this")
                val hold = fixture.useCases.observeSnapshot().first().tasks.single { it.title == "Hold this" }
                fixture.useCases.promote(hold.id)
                fixture.useCases.start(hold.id)
                fixture.useCases.capture("Ping")
                val ping = fixture.useCases.observeSnapshot().first().tasks.single { it.title == "Ping" }
                fixture.useCases.promote(ping.id)
                fixture.useCases.interruptWith(ping.id)
                fixture.useCases.completeActive()
            }
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await { viewModel.uiState.value.recovery?.title == "Hold this" }
                tap("Focus")
                await { compose.onAllNodesWithText("BEFORE THIS").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Bring back…").assertIsEnabled().assertHasClickAction()
                compose.onNodeWithText("Later").assertDoesNotExist()
                tap("Bring back…")
                await { viewModel.bringBack.value?.title == "Hold this" }
                await {
                    compose.onAllNodesWithText("Bring it back in…", useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                compose.onNodeWithText("Bring it back in…", useUnmergedTree = true).assertExists()
                awaitSnapshot(fixture.useCases) { snapshot ->
                    snapshot.tasks.single { it.title == "Hold this" }.bucket != TaskBucket.LATER
                }
            }
        }
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
}
