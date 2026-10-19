// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.components.AppTab
import app.backpin.focus.ui.components.AppTabBar
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class AppTabBarQaTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun inboxFocusAndSettingsTabsNameThemselvesAndHideAZeroBadge() {
        var inboxCount by mutableIntStateOf(0)
        compose.setContent {
            FocusStackTheme {
                AppTabBar(
                    selected = AppTab.HOME,
                    inboxCount = inboxCount,
                    comingUpCount = 0,
                    onSelect = {},
                )
            }
        }
        compose.waitForIdle()
        listOf("Inbox", "Focus", "Settings").forEach { name ->
            val node = compose.onNode(hasText(name) and isTab()).fetchSemanticsNode()
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
            val descriptions = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue(
                "$name should name the tab (text=$texts desc=$descriptions)",
                texts.any { it == name } || descriptions.any { it == name },
            )
        }
        assertTrue("no Inbox badge at 0", !inboxBadge("0"))
        for (count in listOf(1, 2, 3, 0)) {
            compose.runOnIdle { inboxCount = count }
            compose.waitForIdle()
            if (count == 0) {
                assertTrue("no Inbox badge at 0", !inboxBadge("0"))
                assertTrue("stale Inbox badge 1", !inboxBadge("1"))
                assertTrue("stale Inbox badge 2", !inboxBadge("2"))
                assertTrue("stale Inbox badge 3", !inboxBadge("3"))
            } else {
                assertTrue("Inbox badge $count", inboxBadge(count.toString()))
            }
        }
    }

    @Test
    fun inboxBadgeTracksCaptureAndPromote() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                assertTrue("no Inbox badge at 0", !inboxBadge("0"))
                mainDispatcherRule.runTest { fixture.useCases.capture("Note one") }
                await("inbox count 1") { viewModel.uiState.value.inboxCount == 1 }
                assertTrue("Inbox badge 1 after capture", inboxBadge("1"))
                mainDispatcherRule.runTest { fixture.useCases.capture("Note two") }
                await("inbox count 2") { viewModel.uiState.value.inboxCount == 2 }
                assertTrue("Inbox badge 2 after second capture", inboxBadge("2"))
                mainDispatcherRule.runTest {
                    val id =
                        fixture.useCases
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Note two" }
                            .id
                    fixture.useCases.promote(id)
                }
                await("inbox count 1 after promote") { viewModel.uiState.value.inboxCount == 1 }
                assertTrue("Inbox badge 1 after promote", inboxBadge("1"))
                assertTrue("stale Inbox badge 2 after promote", !inboxBadge("2"))
                mainDispatcherRule.runTest {
                    val id =
                        fixture.useCases
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Note one" }
                            .id
                    fixture.useCases.promote(id)
                }
                await("inbox count 0 after emptying") { viewModel.uiState.value.inboxCount == 0 }
                assertTrue("no Inbox badge at 0 after emptying", !inboxBadge("0"))
                assertTrue("stale Inbox badge 1 after emptying", !inboxBadge("1"))
            }
        }
    }

    private fun isTab(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun inboxBadge(count: String): Boolean {
        val inbox = compose.onNode(hasText("Inbox") and isTab()).fetchSemanticsNode().boundsInRoot
        val coming = compose.onNode(hasText("Coming up") and isTab()).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodesWithText(count, useUnmergedTree = true).fetchSemanticsNodes().any { node ->
            val bounds = node.boundsInRoot
            val mid = (bounds.left + bounds.right) / 2f
            val inboxMid = (inbox.left + inbox.right) / 2f
            val comingMid = (coming.left + coming.right) / 2f
            kotlin.math.abs(mid - inboxMid) < kotlin.math.abs(mid - comingMid)
        }
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
