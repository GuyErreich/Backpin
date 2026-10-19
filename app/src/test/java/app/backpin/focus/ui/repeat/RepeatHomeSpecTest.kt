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
import app.backpin.focus.R
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.UndoTarget
import app.backpin.focus.ui.home.HomeScreen
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.undoSnackbarMessage
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RepeatHomeSpecTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTheTitleChecksTheRowOffAndUndoRestoresWithCheckedOffCopy() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
            }
            showApp(env.viewModel)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.status == RepeatDayStatus.OPEN
            }
            compose.onNodeWithText("Take meds", useUnmergedTree = true).performScrollTo()
            compose.onNodeWithText("Take meds", useUnmergedTree = true).performTouchInput { click(center) }
            await {
                env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.target is UndoTarget.RepeatCheckOff
            }
            val undo = env.viewModel.uiState.value.undo
            assertNotNull(undo)
            val checkedOff = compose.activity.getString(R.string.undo_checked_off)
            assertEquals("Checked off", checkedOff)
            assertEquals(checkedOff, undoSnackbarMessage(undo!!, checkedOff))
            assertTrue(undoSnackbarMessage(undo, checkedOff).isNotBlank())
            assertEquals("", undo.message)
            compose.onNodeWithText("Checked off").assertExists()
            compose.onNodeWithText("Undo").assertExists()
            compose.onNodeWithText("Undo").performTouchInput { click(center) }
            await {
                env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.OPEN &&
                    env.viewModel.uiState.value.undo == null
            }
            compose.onNodeWithContentDescription("Take meds. Morning. daily. Check off.").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun missedRowStaysMutedAndCheckableUntilMidnightAndUndoRestoresMissed() {
        val env = env(hour = 23, minute = 59)
        try {
            val today = DAY.toEpochDay()
            val id =
                runOffTestThread {
                    env.repeats.create("Water plants", RepeatBand.NOON, 1, today)!!
                }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.status == RepeatDayStatus.MISSED
            }
            compose.onNodeWithText("Missed at noon").assertExists()
            val missedColor = textLayout("Water plants").layoutInput.style.color
            assertEquals(FocusColors.Text, missedColor)
            tapDescription("Water plants. Missed at noon. Check off.")
            await {
                env.viewModel.missedRepeatSheet.value?.title == "Water plants" &&
                    env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.MISSED
            }
            compose.onNodeWithText("This one was missed.").assertExists()
            compose.onNodeWithText("Done now").performTouchInput { click(center) }
            await {
                env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.DONE &&
                    env.viewModel.uiState.value.undo?.target is UndoTarget.RepeatCheckOff
            }
            val log = runOffTestThread { env.database.repeatLogDao().get(id, today) }
            assertEquals(RepeatOutcome.DONE.name, log?.outcome)
            val undo = env.viewModel.uiState.value.undo!!
            assertEquals(RepeatDayStatus.MISSED, (undo.target as UndoTarget.RepeatCheckOff).restore)
            env.viewModel.undo(undo.id)
            await {
                env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.MISSED &&
                    env.viewModel.uiState.value.undo == null
            }
            assertEquals(
                RepeatOutcome.MISSED.name,
                runOffTestThread { env.database.repeatLogDao().get(id, today)?.outcome },
            )
            compose
                .onNodeWithContentDescription("Water plants. Missed at noon. Check off.")
                .assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun missedRepeatIsStillCheckableAt2359AndGoneAfterLocalMidnight() {
        val env = env(hour = 23, minute = 59)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.repeats.create("Water plants", RepeatBand.NOON, 2, today)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.map { it.repeat.title }.toSet() ==
                    setOf("Take meds", "Water plants") &&
                    env.viewModel.repeatRows.value.rows.all { it.status == RepeatDayStatus.MISSED }
            }
            compose.onNodeWithContentDescription("Take meds. Missed this morning. Check off.").assertExists()
            compose.onNodeWithContentDescription("Water plants. Missed at noon. Check off.").assertExists()

            env.clock.now = at(hour = 0, minute = 1, day = DAY.plusDays(1))
            env.reopenHome()
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.map { it.repeat.title } == listOf("Take meds") &&
                    env.viewModel.repeatRows.value.rows.single().status == RepeatDayStatus.OPEN
            }
            val rows = env.viewModel.repeatRows.value.rows
            assertEquals(listOf("Take meds"), rows.map { it.repeat.title })
            assertEquals(RepeatDayStatus.OPEN, rows.single().status)
            compose.onNodeWithText("Water plants").assertDoesNotExist()
            compose.onNodeWithContentDescription("Take meds. Morning. daily. Check off.").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun phoneOffOverMidnightDropsYesterdaysMissedRepeatWhenHomeReopens() {
        val env = env(hour = 23, minute = 59)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Water plants", RepeatBand.NOON, 2, today)
            }
            showHome(env)
            await {
                env.viewModel.repeatRows.value.rows.singleOrNull()?.status == RepeatDayStatus.MISSED
            }
            env.clock.now = at(hour = 8, minute = 0, day = DAY.plusDays(1))
            env.reopenHome()
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.none { it.repeat.title == "Water plants" }
            }
            awaitText("Nothing due today.")
            assertTrue(
                "Yesterday's off-cadence miss must not survive a reopen after midnight",
                env.viewModel.repeatRows.value.rows.none { it.repeat.title == "Water plants" },
            )
            compose.onNodeWithText("Water plants", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithText("Nothing due today.", useUnmergedTree = true).assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun repeatsNeverAppearInTheTodaySection() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.useCases.capture("Buy milk")
                val taskId = env.useCases.observeSnapshot().first().tasks.single { it.title == "Buy milk" }.id
                env.useCases.promote(taskId)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.any { it.repeat.title == "Take meds" } &&
                    env.viewModel.uiState.value.todaySlots.any { it.task?.title == "Buy milk" }
            }
            compose.onNodeWithText("TODAY").assertExists()
            compose.onNodeWithText("REPEAT").assertExists()
            compose.onNodeWithText("Buy milk").assertExists()
            compose.onNodeWithContentDescription("Take meds. Morning. daily. Check off.").assertExists()
            assertTrue(
                env.viewModel.uiState.value.todaySlots.none { it.task?.title == "Take meds" },
            )
            val snapshot = runOffTestThread { env.useCases.observeSnapshot().first() }
            assertTrue(
                snapshot.tasks.none { it.title == "Take meds" && it.bucket == TaskBucket.TODAY },
            )
            assertEquals(
                TaskBucket.TODAY,
                snapshot.tasks.single { it.title == "Buy milk" }.bucket,
            )
            assertEquals(TaskStatus.OPEN, snapshot.tasks.single { it.title == "Buy milk" }.status)
        } finally {
            env.close()
        }
    }

    @Test
    fun nonDailyRowKeepsCadenceOnOneLineAndEllipsizesTheTitleFirst() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            val title = "Water the plants on the balcony every morning before coffee"
            runOffTestThread {
                env.repeats.create(title, RepeatBand.NOON, 2, today)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.singleOrNull()?.cadenceDays == 2
            }
            val cadence = compose.activity.getString(R.string.repeat_cadence, 2)
            val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).getBoundsInRoot()
            val cadenceBounds = compose.onNodeWithText(cadence, substring = true, useUnmergedTree = true).getBoundsInRoot()
            assertTrue(
                "Title and cadence must share a row: title=$titleBounds cadence=$cadenceBounds",
                abs((titleBounds.top - cadenceBounds.top).value) < 8f,
            )
            assertTrue(
                "The title must give way before the cadence is cut: title.right=${titleBounds.right} cadence.left=${cadenceBounds.left}",
                titleBounds.right <= cadenceBounds.left + 0.5.dp,
            )
            val titleLayout = textLayout(title)
            assertEquals(1, titleLayout.lineCount)
            assertTrue(
                "A long title must ellipsize instead of wrapping onto the cadence",
                titleLayout.isLineEllipsized(0) || titleLayout.hasVisualOverflow,
            )
            val cadenceLayout = textLayout(cadence, substring = true)
            assertEquals(1, cadenceLayout.lineCount)
            assertFalse(
                "Cadence must stay intact",
                cadenceLayout.isLineEllipsized(0),
            )
            assertTrue(cadenceLayout.layoutInput.text.text.contains("every 2 days"))
        } finally {
            env.close()
        }
    }

    @Test
    fun emptyRepeatSectionDoesNotShowAllDone() {
        val env = env(hour = 8)
        try {
            showHome(env)
            await { env.viewModel.uiState.value.ready }
            compose.onNodeWithText("All done").assertDoesNotExist()
            compose.onNodeWithText("Nothing due today.").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun headerIsNotAllDoneWhileAMissedRowRemains() {
        val env = env(hour = 13)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.repeats.create("Water plants", RepeatBand.NOON, 1, today)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.map { it.status }.toSet() ==
                    setOf(RepeatDayStatus.MISSED, RepeatDayStatus.OPEN)
            }
            assertEquals(2, env.viewModel.repeatRows.value.leftCount)
            compose.onNodeWithText("2 of 2 left").assertExists()
            compose.onNodeWithText("All done").assertDoesNotExist()
            tapDescription("Water plants. Noon. daily. Check off.")
            await {
                env.viewModel.repeatRows.value.leftCount == 1 &&
                    env.viewModel.repeatRows.value.rows.any { it.status == RepeatDayStatus.MISSED }
            }
            compose.onNodeWithText("1 of 2 left").assertExists()
            compose.onNodeWithText("All done").assertDoesNotExist()
            tapDescription("Take meds. Missed this morning. Check off.")
            await {
                env.viewModel.missedRepeatSheet.value?.title == "Take meds" &&
                    env.viewModel.repeatRows.value.leftCount == 1 &&
                    env.viewModel.repeatRows.value.rows.any { it.status == RepeatDayStatus.MISSED }
            }
            compose.onNodeWithText("This one was missed.").assertExists()
            compose.onNodeWithText("1 of 2 left").assertExists()
            compose.onNodeWithText("All done").assertDoesNotExist()
            compose.onNodeWithText("Done now").performTouchInput { click(center) }
            await {
                env.viewModel.repeatRows.value.leftCount == 0 &&
                    env.viewModel.repeatRows.value.rows.all { it.status == RepeatDayStatus.DONE }
            }
            compose.onNodeWithText("All done").assertExists()
        } finally {
            env.close()
        }
    }

    @Test
    fun headerShowsAllDoneWhenEveryDueRepeatIsDone() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                val morning = env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)!!
                val second = env.repeats.create("Make bed", RepeatBand.MORNING, 1, today)!!
                env.repeats.markDone(morning, today)
                env.repeats.markDone(second, today)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.total == 2 &&
                    env.viewModel.repeatRows.value.leftCount == 0 &&
                    env.viewModel.repeatRows.value.rows.all { it.status == RepeatDayStatus.DONE }
            }
            compose.onNodeWithText("All done").assertExists()
            compose.onNodeWithText("0 of 2 left").assertDoesNotExist()
        } finally {
            env.close()
        }
    }

    @Test
    fun headerShowsLeftCountAndAllDoneWhenTheLastOpenRowIsCheckedOff() {
        val env = env(hour = 8)
        try {
            val today = DAY.toEpochDay()
            runOffTestThread {
                env.repeats.create("Take meds", RepeatBand.MORNING, 1, today)
                env.repeats.create("Make bed", RepeatBand.MORNING, 1, today)
            }
            showHome(env)
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.rows.size == 2 &&
                    env.viewModel.repeatRows.value.leftCount == 2 &&
                    env.viewModel.repeatRows.value.total == 2
            }
            awaitText("2 of 2 left")
            assertHeader("2 of 2 left")
            compose.onNodeWithText("All done", useUnmergedTree = true).assertDoesNotExist()
            tapDescription("Take meds. Morning. daily. Check off.")
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.leftCount == 1 &&
                    env.viewModel.repeatRows.value.total == 2
            }
            awaitText("1 of 2 left")
            assertHeader("1 of 2 left")
            tapDescription("Make bed. Morning. daily. Check off.")
            await {
                env.viewModel.uiState.value.ready &&
                    env.viewModel.repeatRows.value.leftCount == 0 &&
                    env.viewModel.repeatRows.value.total == 2
            }
            awaitText("All done")
            assertHeader("All done")
            awaitNoText("0 of 2 left")
            compose.onNodeWithText("0 of 2 left", useUnmergedTree = true).assertDoesNotExist()
            assertEquals("All done", repeatsLeftLabel(env.viewModel.repeatRows.value, compose.activity.resources))
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

    private fun showApp(viewModel: FocusStackViewModel) {
        compose.setContent {
            FocusStackTheme {
                FocusApp(viewModel)
            }
        }
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

    private fun textLayout(
        text: String,
        substring: Boolean = false,
    ): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, substring = substring, useUnmergedTree = true)
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

    private fun headerNodes(
        text: String,
        unmerged: Boolean,
    ) = compose.onAllNodesWithText(text, useUnmergedTree = unmerged).fetchSemanticsNodes()

    private fun awaitText(text: String) {
        await {
            headerNodes(text, unmerged = false).isNotEmpty() ||
                headerNodes(text, unmerged = true).isNotEmpty()
        }
    }

    private fun awaitNoText(text: String) {
        await {
            headerNodes(text, unmerged = false).isEmpty() &&
                headerNodes(text, unmerged = true).isEmpty()
        }
    }

    private fun assertHeader(text: String) {
        val unmerged = headerNodes(text, unmerged = false).isEmpty()
        compose.onNodeWithText(text, useUnmergedTree = unmerged).assertExists()
    }

    private fun env(
        hour: Int,
        minute: Int = 0,
        day: LocalDate = DAY,
    ): Env {
        val context: Context = ApplicationProvider.getApplicationContext()
        val clock = SettableClock(at(hour, minute, day))
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "repeat-home-spec-${UUID.randomUUID()}"),
                clock = clock,
            )
        val useCases = FocusUseCases(repository)
        val repeats =
            RepeatsService(
                repository = repository,
                clock = clock,
                zone = { ZONE },
            )
        val store = ViewModelStore()
        return Env(
            clock = clock,
            repeats = repeats,
            database = database,
            useCases = useCases,
            store = store,
            viewModel = createViewModel(store, useCases, repeats, clock),
        )
    }

    private data class Env(
        val clock: SettableClock,
        val repeats: RepeatsService,
        val database: FocusStackDatabase,
        val useCases: FocusUseCases,
        val store: ViewModelStore,
        var viewModel: FocusStackViewModel,
    ) {
        val shown = mutableStateOf(viewModel)

        fun reopenHome() {
            // Production reopens through MainActivity.onStart → FocusStackApp.onForeground
            // → refreshToday. Recreating the ViewModel alone does not move the day signal.
            repeats.refreshToday()
            store.clear()
            viewModel = createViewModel(store, useCases, repeats, clock)
            shown.value = viewModel
        }

        fun close() {
            store.clear()
            database.close()
        }
    }

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.of("UTC")

        fun at(
            hour: Int,
            minute: Int,
            day: LocalDate = DAY,
        ): Long = day.atTime(LocalTime.of(hour, minute)).atZone(ZONE).toInstant().toEpochMilli()

        fun createViewModel(
            store: ViewModelStore,
            useCases: FocusUseCases,
            repeats: RepeatsService,
            clock: AppClock,
        ): FocusStackViewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return FocusStackViewModel(useCases, repeats, clock) as T
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
