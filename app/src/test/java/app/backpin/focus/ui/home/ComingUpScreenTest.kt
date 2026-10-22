// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.R
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.DEFAULT_TEST_ZONE
import app.backpin.focus.testing.FakeAppClock
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                val parked = fixture.useCases.capture("Parked later")!!
                fixture.useCases.demote(parked)
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
                            today = today,
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

    @Test
    fun earliestPickableDateMovesAfterMidnightOnTheInjectedClock() {
        val clock =
            FakeAppClock(
                Instant.parse("2026-10-17T03:30:00Z"),
                DEFAULT_TEST_ZONE,
            )
        useTestFocus(clock = clock) { fixture ->
            check(fixture.clock === clock)
            val beforeMidnight = clock.localDate()
            fixture.withViewModel { viewModel ->
                var today by mutableStateOf(clock.localDate())
                compose.setContent {
                    val state by viewModel.uiState.collectAsState()
                    FocusStackTheme {
                        ComingUpScreen(
                            scheduledTasks = state.scheduledTasks,
                            countdowns = state.countdowns,
                            ready = state.ready,
                            today = today,
                            countdownUnit = CountdownUnit.DAYS,
                            onEdit = {},
                            onOpenSheet = {},
                            onOpenMissed = {},
                            onAddCountdown = { _, _ -> },
                            onChangeCountdownDate = { _, _ -> },
                        )
                    }
                }
                await { viewModel.uiState.value.ready }
                compose.onNodeWithText(compose.activity.getString(R.string.coming_up_empty)).assertExists()
                tap("Add countdown")
                tap("Pick a date")
                assertTodayCellDisabled(beforeMidnight)
                tap("Cancel")
                clock.advanceBy(Duration.ofHours(1))
                val afterMidnight = clock.localDate()
                assertTrue(
                    "clock should roll to the next local day",
                    afterMidnight == beforeMidnight.plusDays(1),
                )
                today = afterMidnight
                compose.waitForIdle()
                tap("Pick a date")
                assertTodayCellDisabled(afterMidnight)
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

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertTodayCellDisabled(today: LocalDate) {
        val spoken = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()).format(today)
        val short = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()).format(today)
        val matchers =
            listOf(
                hasContentDescription(spoken, substring = true),
                hasContentDescription(short, substring = true),
            )
        matchers.forEach { matcher ->
            val count = compose.onAllNodes(matcher).fetchSemanticsNodes().size
            if (count > 0) {
                compose.onNode(matcher).assertIsNotEnabled()
            }
        }
        compose.onNodeWithText("OK").assertIsEnabled()
    }
}
