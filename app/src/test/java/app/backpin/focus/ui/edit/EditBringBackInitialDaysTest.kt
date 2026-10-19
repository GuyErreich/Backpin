// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.edit

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import app.backpin.focus.R
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.capture.BRING_BACK_DEFAULT_DAYS
import app.backpin.focus.ui.capture.BRING_BACK_MAX_DAYS
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditBringBackInitialDaysTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bringBackFromEditStartsOnCurrentDaysAwayOrFive() {
        useTestFocus { fixture ->
            val today = fixture.clock.localDate()
            lateinit var ids: Map<String, String>
            mainDispatcherRule.runTest {
                ids =
                    CASES.associate { case ->
                        when {
                            case.offsetDays == null -> fixture.useCases.capture(case.title)
                            case.countdown ->
                                fixture.useCases.captureCountdown(
                                    case.title,
                                    today.plusDays(case.offsetDays.toLong()).toEpochDay(),
                                )
                            else ->
                                fixture.useCases.captureScheduled(
                                    case.title,
                                    today.plusDays(case.offsetDays.toLong()).toEpochDay(),
                                )
                        }
                        val id =
                            fixture.useCases
                                .observeSnapshot()
                                .first()
                                .tasks
                                .single { it.title == case.title }
                                .id
                        case.title to id
                    }
            }
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await("FocusApp ready") { viewModel.uiState.value.ready }
                CASES.forEach { case ->
                    compose.runOnIdle { viewModel.openEdit(ids.getValue(case.title)) }
                    await("editing ${case.title}") { viewModel.uiState.value.editing?.title == case.title }
                    tap(compose.activity.getString(R.string.action_bring_back))
                    await("Bring back ${case.title}") { viewModel.bringBack.value?.title == case.title }
                    val saveLabel = saveInDays(case.initialDays)
                    await("Save label $saveLabel") {
                        compose.onAllNodesWithText(saveLabel).fetchSemanticsNodes().isNotEmpty() ||
                            compose.onAllNodesWithText(saveLabel, useUnmergedTree = true)
                                .fetchSemanticsNodes()
                                .isNotEmpty()
                    }
                    compose.onNodeWithText(saveLabel).assertExists()
                    compose.runOnIdle { viewModel.dismissBringBack() }
                    await("Bring back dismissed after ${case.title}") { viewModel.bringBack.value == null }
                }
            }
        }
    }

    private fun saveInDays(days: Int): String = compose.activity.resources.getQuantityString(R.plurals.bring_back_save_in_days, days, days)

    private fun tap(label: String) {
        compose.onNode(hasText(label)).assertIsEnabled()
        compose.onNode(hasText(label)).performTouchInput { click() }
        pump()
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

    private data class Case(
        val title: String,
        val offsetDays: Int?,
        val initialDays: Int,
        val countdown: Boolean = false,
    )

    private companion object {
        val CASES: List<Case> =
            listOf(
                Case("No date", null, BRING_BACK_DEFAULT_DAYS),
                Case("Due today", 0, BRING_BACK_DEFAULT_DAYS),
                Case("Due yesterday", -1, BRING_BACK_DEFAULT_DAYS),
                Case("Due tomorrow", 1, 1),
                Case("Due in 12 days", 12, 12),
                Case("Due in a year", BRING_BACK_MAX_DAYS, BRING_BACK_MAX_DAYS),
                Case("Due in 400 days", 400, BRING_BACK_MAX_DAYS),
                Case("Countdown in 400 days", 400, BRING_BACK_MAX_DAYS, countdown = true),
            )
    }
}
