// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.TodaysRepeats
import app.backpin.focus.domain.repeatRowsState
import app.backpin.focus.runOffTestThread
import app.backpin.focus.themeqa.awaitUntil
import app.backpin.focus.themeqa.themeQaEnv
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The REPEAT header must not say "All done" while a missed repeat is still
 * unchecked. On #29 this still fails; the fix landed in #30.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatsMissedHeaderQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun headerDoesNotSayAllDoneWhileMissedRepeatsRemainUnchecked() {
        val todays = TodaysRepeats(
            noon = listOf(
                occ("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED),
            ),
            night = listOf(
                occ("Brush teeth", RepeatBand.NIGHT, RepeatDayStatus.DONE),
            ),
        )
        val state = repeatRowsState(todays)
        assertTrue(state.rows.any { it.status == RepeatDayStatus.MISSED })
        assertNotEquals(
            "All done",
            repeatsLeftLabel(state, compose.activity.resources),
        )

        val env = themeQaEnv(hour = 20)
        try {
            val today = java.time.LocalDate.of(2026, 10, 16).toEpochDay()
            runOffTestThread {
                env.repeats.create("Water plants", RepeatBand.NOON, 1, today)
            }
            compose.setContent {
                val ui by env.viewModel.uiState.collectAsState()
                FocusStackTheme {
                    HomeScreen(
                        state = ui,
                        viewModel = env.viewModel,
                        onOpenFocus = {},
                        onOpenInbox = {},
                        onOpenLater = {},
                        onOpenScheduled = {},
                        onOpenDone = {},
                    )
                }
            }
            compose.awaitUntil {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.any { it.status == RepeatDayStatus.MISSED }
            }
            compose.onNodeWithText("Water plants", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("All done").assertDoesNotExist()
            assertNotEquals(
                "All done",
                repeatsLeftLabel(env.viewModel.repeatRows.value, compose.activity.resources),
            )
        } finally {
            env.close()
        }
    }

    private fun occ(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
    ): RepeatOccurrence = RepeatOccurrence(
        repeat = Repeat(
            id = title,
            title = title,
            band = band,
            everyNDays = 1,
            anchorEpochDay = 0,
            reminderOn = true,
            sortOrder = 0,
            createdAtEpochMillis = 0,
            archivedAtEpochMillis = null,
        ),
        epochDay = 0,
        status = status,
    )
}
