// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.testing.useTestFocus
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
                compose.waitUntil(timeoutMillis = 5_000) {
                    compose.waitForIdle()
                    shadowOf(Looper.getMainLooper()).idle()
                    viewModel.uiState.value.now == null &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Call Dana" }
                }
                val row = compose.onNode(hasContentDescription("Call Dana", substring = true))
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
}
