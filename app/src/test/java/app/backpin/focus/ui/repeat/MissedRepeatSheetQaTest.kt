// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.UndoTarget
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.theme.paletteFor
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MissedRepeatSheetQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingAMissedRowOpensTheSheetAndDoesNotMarkDone() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)!!
                }
            showHome(env)
            awaitMissed(env, "Take meds")
            val before = runOffTestThread { env.database.repeatLogDao().forDay(today) }
            tapDescription("Take meds. Missed this morning. Check off.")
            await { env.viewModel.missedRepeatSheet.value?.title == "Take meds" }
            compose.onNodeWithText("This one was missed.").assertExists()
            compose.onNodeWithText("Muted until the end of the day. You can still check it off.").assertExists()
            assertEquals(listOf("Done now", "Skip today"), sheetActions())
            assertEquals(
                RepeatDayStatus.MISSED,
                env.viewModel.repeatRows.value.rows.single { it.repeat.title == "Take meds" }.status,
            )
            assertEquals(before, runOffTestThread { env.database.repeatLogDao().forDay(today) })
            val log = runOffTestThread { env.database.repeatLogDao().get(id, today) }
            assertTrue(log == null || log.outcome == RepeatOutcome.MISSED.name)
            assertNull(env.viewModel.uiState.value.undo)
        } finally {
            env.close()
        }
    }

    @Test
    fun doneNowMarksTheRowDoneAndClosesTheSheet() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)!!
                }
            showHome(env)
            awaitMissed(env, "Take meds")
            tapDescription("Take meds. Missed this morning. Check off.")
            await { env.viewModel.missedRepeatSheet.value != null }
            tapSheet("Done now")
            await {
                env.viewModel.missedRepeatSheet.value == null &&
                    env.viewModel.repeatRows.value.rows.single { it.repeat.title == "Take meds" }.status ==
                    RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.target is UndoTarget.RepeatCheckOff
            }
            compose.onNodeWithText("This one was missed.").assertDoesNotExist()
            compose.onNodeWithContentDescription("Take meds. Morning. daily. Done.").assertExists()
            assertEquals(
                RepeatOutcome.DONE.name,
                runOffTestThread { env.database.repeatLogDao().get(id, today)?.outcome },
            )
        } finally {
            env.close()
        }
    }

    @Test
    fun skipTodayWritesNothingClosesTheSheetAndLeavesTheMissedRow() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    val created = env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)!!
                    env.repository.markOpenRepeatsMissed(RepeatBand.MORNING, today)
                    created
                }
            showHome(env)
            awaitMissed(env, "Take meds")
            val missed = "Take meds. Missed this morning. Check off."
            val before = runOffTestThread { env.database.repeatLogDao().get(id, today) }
            val logsBefore = runOffTestThread { env.database.repeatLogDao().forDay(today) }
            val allBefore = runOffTestThread { env.database.repeatLogDao().observeAll().first() }
            assertEquals(RepeatOutcome.MISSED.name, before?.outcome)
            assertEquals(1, allBefore.size)
            tapDescription(missed)
            await { env.viewModel.missedRepeatSheet.value != null }
            tapSheet("Skip today")
            await { env.viewModel.missedRepeatSheet.value == null }
            compose.onNodeWithText("This one was missed.").assertDoesNotExist()
            val row = env.viewModel.repeatRows.value.rows.single { it.repeat.title == "Take meds" }
            assertEquals(RepeatDayStatus.MISSED, row.status)
            val after = runOffTestThread { env.database.repeatLogDao().get(id, today) }
            val allAfter = runOffTestThread { env.database.repeatLogDao().observeAll().first() }
            assertEquals(before, after)
            assertEquals(RepeatOutcome.MISSED.name, after?.outcome)
            assertNotEquals(RepeatOutcome.DONE.name, after?.outcome)
            assertEquals(logsBefore, runOffTestThread { env.database.repeatLogDao().forDay(today) })
            assertEquals(allBefore, allAfter)
            assertTrue(allAfter.none { it.outcome == RepeatOutcome.DONE.name })
            assertNull(env.viewModel.uiState.value.undo)
            assertNull(runOffTestThread { env.repeats.observePendingUndo().first() })
            compose.onNodeWithText("Missed this morning").assertExists()
            assertEquals(FocusColors.Text, textColor("Take meds"))
            assertEquals(FocusColors.Amber, textColor("Missed this morning"))
            compose.onNodeWithContentDescription(missed).assertHasClickAction()
            tapDescription(missed)
            await { env.viewModel.missedRepeatSheet.value?.title == "Take meds" }
            compose.onNodeWithText("Done now").assertExists()
            assertEquals(RepeatDayStatus.MISSED, env.viewModel.repeatRows.value.rows.single().status)
            assertEquals(allBefore, runOffTestThread { env.database.repeatLogDao().observeAll().first() })
        } finally {
            env.close()
        }
    }

    @Test
    fun afterSkipTodayDoneNowStillChecksTheRowOff() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    val created = env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)!!
                    env.repository.markOpenRepeatsMissed(RepeatBand.MORNING, today)
                    created
                }
            showHome(env)
            awaitMissed(env, "Take meds")
            val missed = "Take meds. Missed this morning. Check off."
            tapDescription(missed)
            await { env.viewModel.missedRepeatSheet.value != null }
            tapSheet("Skip today")
            await { env.viewModel.missedRepeatSheet.value == null }
            tapDescription(missed)
            await { env.viewModel.missedRepeatSheet.value != null }
            tapSheet("Done now")
            await {
                env.viewModel.missedRepeatSheet.value == null &&
                    env.viewModel.repeatRows.value.rows.single { it.repeat.title == "Take meds" }.status ==
                    RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.target is UndoTarget.RepeatCheckOff
            }
            compose.onNodeWithContentDescription("Take meds. Morning. daily. Done.").assertExists()
            assertEquals(
                RepeatOutcome.DONE.name,
                runOffTestThread { env.database.repeatLogDao().get(id, today)?.outcome },
            )
        } finally {
            env.close()
        }
    }

    @Test
    fun afterMidnightASkippedMissLeavesHomeAndReturnsOnTheNextDueDay() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Water plants", RepeatBand.NOON, 2, today)
                env.repository.markOpenRepeatsMissed(RepeatBand.NOON, today)
            }
            showHome(env)
            awaitMissed(env, "Water plants")
            tapDescription("Water plants. Missed at noon. Check off.")
            await { env.viewModel.missedRepeatSheet.value != null }
            tapSheet("Skip today")
            await {
                env.viewModel.missedRepeatSheet.value == null &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.status == RepeatDayStatus.MISSED
            }

            env.advanceTo(hour = 23, minute = 59, day = DAY)
            awaitMissed(env, "Water plants")
            compose.onNodeWithText("Missed at noon").assertExists()
            assertEquals(FocusColors.Text, textColor("Water plants"))
            compose
                .onNodeWithContentDescription("Water plants. Missed at noon. Check off.")
                .assertHasClickAction()

            env.advanceTo(hour = 0, minute = 0, day = DAY.plusDays(1))
            await { env.viewModel.uiState.value.ready }
            assertTrue(env.viewModel.repeatRows.value.rows.none { it.repeat.title == "Water plants" })
            compose.onNodeWithText("Water plants").assertDoesNotExist()
            compose.onNodeWithText("Nothing due today.").assertExists()

            env.advanceTo(hour = 8, minute = 0, day = DAY.plusDays(2))
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.repeat?.title == "Water plants" &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.status == RepeatDayStatus.OPEN
            }
            compose.onNodeWithContentDescription("Water plants. Noon. every 2 days. Check off.").assertExists()
            compose.onNodeWithText("Missed at noon").assertDoesNotExist()
        } finally {
            env.close()
        }
    }

    @Test
    fun missedRowStaysMutedAndNeverUsesTheErrorColor() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
            }
            showHome(env)
            awaitMissed(env, "Take meds")
            assertEquals(FocusColors.Text, textColor("Take meds"))
            assertEquals(FocusColors.Amber, textColor("Missed this morning"))
            val danger = paletteFor(ThemeId.SIGNAL).danger
            assertNotEquals(danger, textColor("Take meds"))
            assertNotEquals(danger, textColor("Missed this morning"))
            compose
                .onNodeWithContentDescription("Take meds. Missed this morning. Check off.")
                .assertHasClickAction()
        } finally {
            env.close()
        }
    }

    private fun showHome(env: Env) {
        compose.setContent {
            val viewModel = env.shown.value
            val state by viewModel.uiState.collectAsState()
            val missed by viewModel.missedRepeatSheet.collectAsState()
            FocusStackTheme {
                HomeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenFocus = {},
                    onOpenInbox = {},
                    onOpenScheduled = {},
                    onOpenDone = {},
                )
                missed?.let { pending ->
                    MissedRepeatSheet(
                        state = pending,
                        onDismiss = viewModel::dismissMissedRepeat,
                        onDoneNow = viewModel::doneMissedRepeat,
                        onSkipToday = viewModel::skipMissedRepeat,
                    )
                }
            }
        }
    }

    private fun awaitMissed(
        env: Env,
        title: String,
    ) {
        await {
            env.viewModel.uiState.value.ready &&
                env.viewModel.repeatRows.value.rows.singleOrNull { it.repeat.title == title }?.status ==
                RepeatDayStatus.MISSED
        }
    }

    private fun sheetActions(): List<String> {
        data class Hit(
            val label: String,
            val left: androidx.compose.ui.unit.Dp,
            val top: androidx.compose.ui.unit.Dp,
        )

        val hits = mutableListOf<Hit>()
        listOf("Done now", "Skip today", "Start", "Done", "Later", "Edit").forEach { label ->
            val nodes = compose.onAllNodesWithText(label)
            val found = nodes.fetchSemanticsNodes()
            found.indices.forEach { index ->
                val bounds = nodes[index].getBoundsInRoot()
                if (bounds.bottom - bounds.top >= 50.dp) {
                    hits += Hit(label, bounds.left, bounds.top)
                }
            }
        }
        return hits.sortedWith(compareBy({ it.top }, { it.left })).map { it.label }
    }

    private fun tapSheet(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun tapDescription(description: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithContentDescription(description)
        node.performScrollTo()
        node.assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun textColor(text: String) = textLayout(text).layoutInput.style.color

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun env(hour: Int): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val clock = SettableClock(at(hour, 0, DAY))
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "missed-repeat-${UUID.randomUUID()}"),
                clock = clock,
            )
        val useCases = FocusUseCases(repository)
        val repeats = RepeatsService(repository, clock = clock)
        val store = ViewModelStore()
        return Env(
            clock = clock,
            repeats = repeats,
            repository = repository,
            database = database,
            useCases = useCases,
            store = store,
            viewModel = createViewModel(store, useCases, repeats),
        )
    }

    private data class Env(
        val clock: SettableClock,
        val repeats: RepeatsService,
        val repository: FocusRepositoryImpl,
        val database: FocusStackDatabase,
        val useCases: FocusUseCases,
        val store: ViewModelStore,
        var viewModel: FocusStackViewModel,
    ) {
        val shown = mutableStateOf(viewModel)

        fun advanceTo(
            hour: Int,
            minute: Int,
            day: LocalDate,
        ) {
            clock.now = at(hour, minute, day)
            repeats.refreshToday()
            reopenHome()
        }

        fun reopenHome() {
            store.clear()
            viewModel = createViewModel(store, useCases, repeats)
            shown.value = viewModel
        }

        fun close() {
            store.clear()
            database.close()
        }
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.systemDefault()

        fun at(
            hour: Int,
            minute: Int,
            day: LocalDate = DAY,
        ): Long = day.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toEpochMilli()

        fun createViewModel(
            store: ViewModelStore,
            useCases: FocusUseCases,
            repeats: RepeatsService,
        ): FocusStackViewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return FocusStackViewModel(useCases, repeats) as T
                }
            },
        )[FocusStackViewModel::class.java]
    }

    private class SettableClock(
        var now: Long,
    ) : AppClock {
        override fun nowEpochMillis(): Long = now
    }
}
