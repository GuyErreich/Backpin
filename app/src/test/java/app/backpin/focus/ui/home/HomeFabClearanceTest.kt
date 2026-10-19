// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.testing.MainDispatcherRule
import app.backpin.focus.testing.defaultTodayTitles
import app.backpin.focus.testing.useTestFocus
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h640dp")
class HomeFabClearanceTest {
    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lastListItemAndSnackbarStayClearOfTheFab() {
        useTestFocus { fixture ->
            val lastRepeat = "Fill row 12"
            mainDispatcherRule.runTest {
                fixture.seedFullToday()
                val day = fixture.clock.localDate().toEpochDay()
                for (index in 1..12) {
                    fixture.repeats.create(
                        "Fill row $index",
                        RepeatBand.NIGHT,
                        1,
                        day,
                        reminderOn = false,
                    )
                }
            }
            fixture.withViewModel { viewModel ->
                compose.setContent {
                    FocusStackTheme {
                        FocusApp(viewModel)
                    }
                }
                await {
                    viewModel.uiState.value.ready &&
                        viewModel.repeatRows.value.rows.any { it.repeat.title == lastRepeat }
                }
                val doneTitle = defaultTodayTitles().last()
                invokeCustomAction("Done", doneTitle)
                val checkedOff = compose.activity.getString(R.string.undo_checked_off)
                await {
                    nodes(checkedOff, unmerged = false).isNotEmpty() ||
                        nodes(checkedOff, unmerged = true).isNotEmpty()
                }
                compose.onNodeWithText(lastRepeat, useUnmergedTree = true).performScrollTo()
                compose.waitForIdle()
                val last = compose.onNodeWithText(lastRepeat, useUnmergedTree = true).getBoundsInRoot()
                val fab = compose.onNodeWithText(compose.activity.getString(R.string.action_add)).getBoundsInRoot()
                val snackbarUnmerged = nodes(checkedOff, unmerged = true).isNotEmpty() &&
                    nodes(checkedOff, unmerged = false).isEmpty()
                val snackbar =
                    compose.onNodeWithText(checkedOff, useUnmergedTree = snackbarUnmerged).getBoundsInRoot()
                val snackbarHeight = snackbar.bottom - snackbar.top
                assertTrue(
                    "snackbar height $snackbarHeight vs ${HomeMetrics.SnackbarHeight}",
                    snackbarHeight >= HomeMetrics.SnackbarHeight - 0.5.dp,
                )
                assertTrue("last list item is covered by the FAB", !intersects(last, fab))
                assertTrue("snackbar is covered by the FAB", !intersects(snackbar, fab))
                assertTrue("last list item is covered by the snackbar", !intersects(last, snackbar))
            }
        }
    }

    private fun intersects(
        a: DpRect,
        b: DpRect,
    ): Boolean {
        val slop = 0.5.dp
        return a.left < b.right - slop &&
            a.right > b.left + slop &&
            a.top < b.bottom - slop &&
            a.bottom > b.top + slop
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
        val action = found[index].config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnIdle { check(action.action()) }
        pump()
    }

    private fun nodes(
        text: String,
        unmerged: Boolean,
    ) = compose.onAllNodesWithText(text, useUnmergedTree = unmerged).fetchSemanticsNodes()

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
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
