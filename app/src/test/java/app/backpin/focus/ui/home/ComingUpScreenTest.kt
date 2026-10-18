// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.backpin.focus.R
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.TaskBucket
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
class ComingUpScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun comingUpSortsByDateShowsCountdownTextAndOmitsLater() {
        useTestFocus { fixture ->
            val today = fixture.clock.localDate()
            runBlocking {
                fixture.useCases.captureLater("Parked later")
                fixture.useCases.captureScheduled("Book the room", today.plusDays(30).toEpochDay())
                fixture.useCases.captureCountdown("Dentist", today.plusDays(2).toEpochDay())
                fixture.useCases.captureScheduled("File the form", today.plusDays(4).toEpochDay())
                fixture.useCases.captureScheduled("Call Dana", today.plusDays(1).toEpochDay())
            }
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    FocusStackTheme {
                        ComingUpScreen(
                            scheduledTasks = state.scheduledTasks,
                            countdowns = state.countdowns,
                            ready = state.ready,
                            today = state.today,
                            countdownUnit = CountdownUnit.DAYS,
                            onEdit = {},
                            onOpenSheet = {},
                            onOpenMissed = {},
                            onAddCountdown = { _, _ -> },
                            onChangeCountdownDate = { _, _ -> },
                        )
                    }
                }
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.uiState.value.scheduledTasks.size == 3 &&
                        viewModel.uiState.value.countdowns.size == 1
                }
                awaitText("Call Dana")
                awaitText("Dentist")
                awaitText("File the form")
                awaitText("Book the room")

                val order = listOf("Call Dana", "Dentist", "File the form", "Book the room")
                order.zipWithNext().forEach { (earlier, later) ->
                    assertTrue(
                        "$earlier should sit above $later",
                        topOf(earlier) < topOf(later),
                    )
                }

                awaitText(inDays(1))
                awaitText(inDays(2))
                awaitText(inDays(4))
                awaitText(inDays(30))

                compose.onNodeWithText("Parked later", useUnmergedTree = true).assertDoesNotExist()
                val laterStillLater =
                    runBlocking {
                        fixture.repository
                            .observeSnapshot()
                            .first()
                            .tasks
                            .single { it.title == "Parked later" }
                            .bucket == TaskBucket.LATER
                    }
                assertTrue(laterStillLater)
            }
        }
    }

    private fun inDays(count: Int): String = compose.activity.resources.getQuantityString(R.plurals.coming_up_in_days, count, count)

    private fun topOf(title: String): Float = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
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
