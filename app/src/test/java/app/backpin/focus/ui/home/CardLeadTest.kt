// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.ui.FocusStackViewModel
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CardLeadTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pileCardTitleIsTitleLargeAndActionsStayShort() {
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
        val today = boundsOf("Today")
        val interrupt = boundsOf("Interrupt")
        listOf("Today", "Start", "Interrupt", "Edit").forEach { label ->
            val height = boundsOf(label).let { it.bottom - it.top }
            assertTrue("$label is $height tall", height <= 48.dp + 0.5.dp && height >= 48.dp - 0.5.dp)
        }
        assertTrue(
            "Interrupt is ${interrupt.right - interrupt.left}, Today is ${today.right - today.left}",
            interrupt.right - interrupt.left > today.right - today.left,
        )
    }

    @Test
    fun captureDestinationStaysAtLeast56dp() {
        compose.setContent {
            FocusStackTheme {
                CaptureSheet(
                    phrases = emptyList(),
                    frameActive = true,
                    onDismiss = {},
                    onSaveToInbox = {},
                    onInterruptNow = {},
                    onPlaceToday = {},
                    onPlaceStart = {},
                    onPlaceLater = {},
                    onSchedule = { _, _, _ -> },
                )
            }
        }
        val bounds = compose.onNodeWithText("Save to Inbox").getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Save to Inbox is $height tall", height >= 56.dp - 0.5.dp)
    }

    @Test
    fun touchesOnTodayStartAndInterruptReachThePileAction() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "card-lead-${UUID.randomUUID()}"),
                    clock = AppClock { System.currentTimeMillis() },
                ),
            )
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            runBlocking {
                useCases.capture("Hold this")
                useCases.start(idOf(useCases, "Hold this"))
                useCases.capture("To today")
                useCases.capture("To start")
                useCases.capture("To interrupt")
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

            tap("Today", beside = "To today")
            awaitSnapshot(useCases, "Today") { snapshot ->
                snapshot.tasks.single { it.title == "To today" }.bucket == TaskBucket.TODAY
            }
            await { compose.onAllNodesWithText("To today").fetchSemanticsNodes().isEmpty() }

            tap("Start", beside = "To start")
            awaitSnapshot(useCases, "Start") { snapshot -> activeTitle(snapshot) == "To start" }
            await { compose.onAllNodesWithText("To start").fetchSemanticsNodes().isEmpty() }

            tap("Interrupt", beside = "To interrupt")
            awaitSnapshot(useCases, "Interrupt") { snapshot ->
                activeTitle(snapshot) == "To interrupt" &&
                    snapshot.tasks.single { it.title == "To interrupt" }.bucket == TaskBucket.INTERRUPTION
            }
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun boundsOf(label: String) = compose.onNodeWithText(label).getBoundsInRoot()

    private fun fontSizeOf(text: String) = textLayout(text).layoutInput.style.fontSize

    private fun textLayout(text: String): TextLayoutResult {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun tap(
        label: String,
        beside: String,
    ) {
        compose.waitForIdle()
        val node = nearest(compose.onAllNodesWithText(label), beside)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun nearest(
        nodes: SemanticsNodeInteractionCollection,
        beside: String,
    ): SemanticsNodeInteraction {
        val anchor = compose.onNodeWithText(beside).fetchSemanticsNode().boundsInRoot.top
        val found = nodes.fetchSemanticsNodes()
        val index = found.indices.minBy { kotlin.math.abs(found[it].boundsInRoot.top - anchor) }
        return nodes[index]
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
