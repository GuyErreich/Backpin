// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.capture.CaptureSheet
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CardLeadTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pileCardTitleIsTitleLargeAndChipRowIsGone() {
        compose.setContent {
            FocusStackTheme {
                InboxScreen(
                    tasks =
                    listOf(
                        TaskUi(
                            id = "1",
                            title = "Call Dana",
                            returnCue = null,
                        ),
                    ),
                    ready = true,
                    canInterrupt = true,
                    onBack = {},
                    onToday = {},
                    onStart = {},
                    onInterrupt = {},
                    onEdit = {},
                )
            }
        }
        assertEquals(22.sp, fontSizeOf("Call Dana"))
        listOf("Today", "Start", "Interrupt", "Edit").forEach { label ->
            compose.onAllNodesWithText(label).fetchSemanticsNodes().let { nodes ->
                assertTrue("$label chip must not appear on the resting card", nodes.isEmpty())
            }
        }
        val matcher =
            SemanticsMatcher("has Today") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Today" }
            }
        val custom =
            compose
                .onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single()
                .config[SemanticsActions.CustomActions]
                .map { it.label }
                .toSet()
        assertTrue(custom.containsAll(listOf("Today", "Start", "Interrupt", "Edit")))
    }

    @Test
    fun captureDestinationStaysAtLeast56dp() {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    onDismiss = {},
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                )
            }
        }
        val bounds = compose.onNodeWithText("Save to Inbox").getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Save to Inbox is $height tall", height >= 56.dp - 0.5.dp)
    }

    @Test
    fun customActionsOnInboxReachTodayStartAndInterrupt() {
        useTestFocus { fixture ->
            fixture.withViewModel { viewModel ->
                runBlocking {
                    fixture.useCases.capture("Hold this")
                    fixture.useCases.start(idOf(fixture.useCases, "Hold this"))
                    fixture.useCases.capture("To today")
                    fixture.useCases.capture("To start")
                    fixture.useCases.capture("To interrupt")
                }
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    FocusStackTheme {
                        InboxScreen(
                            tasks = state.inboxTasks,
                            ready = state.ready,
                            canInterrupt = state.canInterrupt,
                            onBack = {},
                            onToday = viewModel::promote,
                            onStart = viewModel::start,
                            onInterrupt = viewModel::interruptFromPile,
                            onEdit = {},
                        )
                    }
                }
                await { viewModel.uiState.value.ready && viewModel.uiState.value.canInterrupt }
                compose.onNodeWithText("To today").assertExists()

                invokeCustomAction("Today", "To today")
                awaitSnapshot(fixture.useCases, "Today") { snapshot ->
                    snapshot.tasks.single { it.title == "To today" }.bucket == TaskBucket.TODAY
                }
                await { compose.onAllNodesWithText("To today").fetchSemanticsNodes().isEmpty() }

                invokeCustomAction("Start", "To start")
                awaitSnapshot(fixture.useCases, "Start") { snapshot -> activeTitle(snapshot) == "To start" }
                await { compose.onAllNodesWithText("To start").fetchSemanticsNodes().isEmpty() }

                invokeCustomAction("Interrupt", "To interrupt")
                awaitSnapshot(fixture.useCases, "Interrupt") { snapshot ->
                    activeTitle(snapshot) == "To interrupt" &&
                        snapshot.tasks.single { it.title == "To interrupt" }.bucket == TaskBucket.INTERRUPTION
                }
            }
        }
    }

    private fun fontSizeOf(text: String) = textLayout(text).layoutInput.style.fontSize

    private fun textLayout(text: String): TextLayoutResult {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun invokeCustomAction(
        label: String,
        nearTitle: String,
    ) {
        val titles = compose.onAllNodesWithText(nearTitle).fetchSemanticsNodes()
        check(titles.isNotEmpty()) { "No title $nearTitle" }
        val titleTop = titles.minBy { it.boundsInRoot.top }.boundsInRoot.top
        val matcher =
            SemanticsMatcher("has $label") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == label }
            }
        val nodes = compose.onAllNodes(matcher, useUnmergedTree = true)
        val found = nodes.fetchSemanticsNodes()
        check(found.isNotEmpty()) { "No $label action near $nearTitle" }
        val index = found.indices.minBy { kotlin.math.abs(found[it].boundsInRoot.top - titleTop) }
        val action =
            found[index].config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { check(action.action()) }
        compose.waitForIdle()
    }

    private suspend fun idOf(
        useCases: FocusUseCases,
        title: String,
    ): String = useCases.observeSnapshot().first().tasks.single { it.title == title }.id

    private fun activeTitle(snapshot: FocusSnapshot): String? {
        val active = snapshot.frames.singleOrNull { it.state == FrameState.ACTIVE } ?: return null
        return snapshot.tasks.singleOrNull { it.id == active.taskId }?.title
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
        what: String,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        var detail = ""
        try {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idle()
                val snapshot = runBlocking { useCases.observeSnapshot().first() }
                detail =
                    snapshot.tasks.joinToString { "${it.title}:${it.bucket}" } +
                    " active=${activeTitle(snapshot)}"
                predicate(snapshot)
            }
        } catch (error: Throwable) {
            throw AssertionError("$what — $detail", error)
        }
    }
}
