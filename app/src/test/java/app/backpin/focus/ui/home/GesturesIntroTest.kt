// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.settings.SettingsScreen
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
class GesturesIntroTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeShowsIntroUntilGotItThenSettingsReplayClearsTheFlag() {
        useTestFocus { fixture ->
            runBlocking {
                fixture.preferences.setGesturesIntroSeen(false)
                fixture.preferences.setGestureHintRightSeen(false)
                fixture.preferences.setGestureHintLeftSeen(false)
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
                await { viewModel.uiState.value.ready }
                compose.onNodeWithText("Slide right finishes it.").assertExists()
                tap("Next")
                compose.onNodeWithText("Slide left puts it back.").assertExists()
                tap("Next")
                compose.onNodeWithText("Tap opens it.").assertExists()
                tap("Next")
                compose.onNodeWithText("Hold moves it.").assertExists()
                tap("Got it")
                await {
                    runBlocking { fixture.preferences.preferences.first().gesturesIntroSeen }
                }
                compose.onNodeWithText("Slide right finishes it.").assertDoesNotExist()

                viewModel.replayGesturesIntro()
                await {
                    !runBlocking { fixture.preferences.preferences.first().gesturesIntroSeen }
                }
                await { compose.onAllNodesWithText("Slide right finishes it.").fetchSemanticsNodes().isNotEmpty() }
            }
        }
    }

    @Test
    fun introBackOnFirstCardClosesAndMarksSeen() {
        useTestFocus { fixture ->
            runBlocking { fixture.preferences.setGesturesIntroSeen(false) }
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
                await { viewModel.uiState.value.ready }
                compose.onNodeWithText("Slide right finishes it.").assertExists()
                compose.onNodeWithContentDescription("Back").performTouchInput { click(center) }
                await {
                    runBlocking { fixture.preferences.preferences.first().gesturesIntroSeen }
                }
                compose.onNodeWithText("Slide right finishes it.").assertDoesNotExist()
            }
        }
    }

    @Test
    fun settingsShowGesturesCallsReplay() {
        var replayed = false
        compose.setContent {
            FocusStackTheme {
                SettingsScreen(
                    preferences = UserPreferences(gesturesIntroSeen = true),
                    onNudgeSelected = { _, _ -> },
                    onQuietHours = { _, _ -> },
                    onCurrentNotification = {},
                    onTodayLimit = {},
                    onAddPhrase = {},
                    onRenamePhrase = { _, _ -> },
                    onRemovePhrase = {},
                    bandTimes = BandTimes.DEFAULT,
                    onShowGestures = { replayed = true },
                )
            }
        }
        compose.onNodeWithText("Show gestures").assertExists()
        tap("Show gestures")
        assertTrue(replayed)
    }

    @Test
    fun homeHintRetiresAfterBothSlidesAreMarked() {
        useTestFocus { fixture ->
            runBlocking {
                fixture.preferences.setGesturesIntroSeen(true)
                fixture.preferences.setGestureHintRightSeen(false)
                fixture.preferences.setGestureHintLeftSeen(false)
                fixture.useCases.capture("Park me")
                fixture.useCases.promote(
                    fixture.useCases.observeSnapshot().first().tasks.single { it.title == "Park me" }.id,
                )
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
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.todaySlots.any { it.task?.title == "Park me" }
                }
                compose.onNodeWithText("Slide right to finish. Slide left to put it back.").assertExists()

                viewModel.markGestureHintRightSeen()
                await {
                    runBlocking { fixture.preferences.preferences.first().gestureHintRightSeen } &&
                        !runBlocking { fixture.preferences.preferences.first().gestureHintLeftSeen }
                }
                compose.onNodeWithText("Slide right to finish. Slide left to put it back.").assertExists()

                viewModel.markGestureHintLeftSeen()
                await {
                    runBlocking {
                        val prefs = fixture.preferences.preferences.first()
                        prefs.gestureHintRightSeen && prefs.gestureHintLeftSeen
                    }
                }
                compose.onNodeWithText("Slide right to finish. Slide left to put it back.").assertDoesNotExist()
            }
        }
    }

    @Test
    fun doneRestoreAndInboxHaveNoChipLabels() {
        var restored: String? = null
        compose.setContent {
            FocusStackTheme {
                DoneScreen(
                    tasks = listOf(TaskUi(id = "d1", title = "Archived call", returnCue = null)),
                    ready = true,
                    onBack = {},
                    onRestore = { restored = it },
                )
                InboxScreen(
                    tasks = listOf(TaskUi(id = "i1", title = "Inbox call", returnCue = null)),
                    ready = true,
                    canInterrupt = false,
                    onBack = {},
                    onToday = {},
                    onStart = {},
                    onInterrupt = {},
                    onEdit = {},
                    showBack = false,
                )
            }
        }
        listOf("Today", "Start", "Restore", "Edit").forEach { label ->
            assertTrue(compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty())
        }
        val matcher =
            SemanticsMatcher("has Restore") { node ->
                node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Restore" }
            }
        val action =
            compose
                .onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .first()
                .config[SemanticsActions.CustomActions]
                .first { it.label == "Restore" }
        compose.runOnIdle { check(action.action()) }
        assertTrue(restored == "d1")
    }

    private fun tap(label: String) {
        compose.onNodeWithText(label).assertIsEnabled().assertHasClickAction()
        compose.onNodeWithText(label).performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }
}
