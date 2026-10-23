// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusStackViewModel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SwipeRevealClipTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun startRevealStaysInsideTheCardInRtl() {
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            HomeScreen(
                                state = state,
                                viewModel = viewModel,
                                onOpenFocus = {},
                                onOpenInbox = {},
                                onOpenScheduled = {},
                                onOpenDone = {},
                                homeLayout = HomeLayout.BANDS,
                            )
                        }
                    }
                }
                val row = callDanaRow(viewModel)
                row.performTouchInput {
                    down(center)
                    moveBy(Offset(120.dp.toPx(), 0f))
                }
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()

                val card = row.fetchSemanticsNode().boundsInRoot
                val start =
                    compose
                        .onNodeWithText("Start", useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                assertTrue(
                    "Start should sit in the gap inside the card, start=$start card=$card",
                    start.center.x < card.left && start.top >= card.top - 1f && start.bottom <= card.bottom + 1f,
                )
                assertTrue(
                    "Start should stay on screen, start=$start",
                    start.left >= 0f && start.right <= card.right,
                )
            }
        }
    }

    @Test
    fun revealColorContinuesBehindTheRow() {
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
                            homeLayout = HomeLayout.BANDS,
                        )
                    }
                }
                val row = callDanaRow(viewModel)
                row.performTouchInput {
                    down(center)
                    moveBy(Offset(120.dp.toPx(), 0f))
                }
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()

                val card = row.fetchSemanticsNode().boundsInRoot
                val color =
                    compose
                        .onNodeWithTag(SWIPE_REVEAL_COLOR_TAG, useUnmergedTree = true)
                        .fetchSemanticsNode()
                        .boundsInRoot
                val reveal = card.left - color.left
                val under = color.right - card.left
                val corner = with(compose.density) { HomeMetrics.TaskRadius.toPx() }
                val slack = with(compose.density) { 12.dp.toPx() }
                assertTrue(
                    "The band should sit in the gap and cover the row height, color=$color card=$card",
                    reveal > 1f && color.top <= card.top + 1f && color.bottom >= card.bottom - 1f,
                )
                assertTrue(
                    "The band should cover the leading corners, under=$under corner=$corner",
                    under >= corner - 2f,
                )
                assertTrue(
                    "The band should stay on the opened side, under=$under corner=$corner",
                    under <= corner + slack,
                )
            }
        }
    }

    /**
     * The row description is on the card, under the swipe card's merged actions.
     * The merged tree can omit it while the unmerged node is already placed, and
     * the touch then never lands. Wait for that placed node.
     */
    private fun callDanaRow(viewModel: FocusStackViewModel): SemanticsNodeInteraction {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            viewModel.uiState.value.now == null &&
                viewModel.uiState.value.todaySlots.any { it.task?.title == "Call Dana" } &&
                callDanaPlaced()
        }
        return compose.onNode(hasContentDescription("Call Dana", substring = true), useUnmergedTree = true)
    }

    private fun callDanaPlaced(): Boolean = compose.onAllNodes(
        hasContentDescription("Call Dana", substring = true),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().any { node ->
        node.layoutInfo.isPlaced && node.boundsInRoot.width > 1f && node.boundsInRoot.height > 1f
    }
}
