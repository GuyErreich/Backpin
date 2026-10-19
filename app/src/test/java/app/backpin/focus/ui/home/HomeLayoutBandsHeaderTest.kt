// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
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
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.repeats.RepeatsService
import app.backpin.focus.runOffTestThread
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.theme.paletteFor
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class HomeLayoutBandsHeaderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<CloseableEnv>()

    @After
    fun tearDown() {
        closeOpened()
    }

    @Test
    fun bandsLayoutShowsMorningNoonNightHeadersAndFlatDoesNot() {
        val env = openEnv()
        var layout by mutableStateOf(HomeLayout.BANDS)
        showHome(env) { layout }
        await { env.viewModel.uiState.value.ready }
        compose.onNodeWithText("NOON · NOW").assertIsDisplayed()
        compose.onNodeWithText("MORNING").assertDoesNotExist()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()
        compose.onNodeWithText("ANYTIME").assertDoesNotExist()
        compose.onNodeWithText("TODAY").assertDoesNotExist()
        compose.onNodeWithText("REPEAT").assertDoesNotExist()

        compose.runOnIdle { layout = HomeLayout.FLAT }
        await { env.viewModel.uiState.value.ready }
        compose.onNodeWithText("MORNING").assertDoesNotExist()
        compose.onNodeWithText("NOON", substring = true).assertDoesNotExist()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()
        compose.onNodeWithText("ANYTIME").assertDoesNotExist()
        compose.onNodeWithText("TODAY").assertIsDisplayed()
        compose.onNodeWithText("REPEAT").assertIsDisplayed()
        compose.onNodeWithText("Nothing due today.").assertIsDisplayed()
    }

    @Test
    fun flatLayoutKeepsTheOldTodayAndRepeatList() {
        val env = openEnv()
        runOffTestThread {
            env.repeats.create("Take meds", RepeatBand.MORNING, 1, DAY.toEpochDay())
            env.useCases.capture("Buy milk")
            val taskId =
                env.useCases
                    .observeSnapshot()
                    .first()
                    .tasks
                    .single { it.title == "Buy milk" }
                    .id
            env.useCases.promote(taskId)
        }
        var layout by mutableStateOf(HomeLayout.FLAT)
        showHome(env) { layout }
        await {
            env.viewModel.uiState.value.ready &&
                env.viewModel.repeatRows.value.rows.any { it.repeat.title == "Take meds" } &&
                env.viewModel.uiState.value.todaySlots.any { it.task?.title == "Buy milk" }
        }
        compose.onNodeWithText("TODAY").assertIsDisplayed()
        compose.onNodeWithText("REPEAT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buy milk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Take meds", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("MORNING").assertDoesNotExist()
        compose.onNodeWithText("NOON", substring = true).assertDoesNotExist()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()

        compose.runOnIdle { layout = HomeLayout.BANDS }
        await { env.viewModel.uiState.value.ready }
        compose.onNodeWithText("TODAY").assertDoesNotExist()
        compose.onNodeWithText("REPEAT").assertDoesNotExist()
        compose.onNodeWithText("NOON · NOW").assertIsDisplayed()
        compose.onNodeWithText("MORNING").assertIsDisplayed()
        compose.onNodeWithText("NIGHT").assertDoesNotExist()
        compose.onNodeWithText("ANYTIME").assertDoesNotExist()
        compose.onNodeWithText("Buy milk", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Take meds", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun bandHeaderColorsFollowSelectedThemeViaLivePalette() {
        assertEquals(FocusColors.Text, paletteFor(ThemeId.SIGNAL).textPrimary)
        assertEquals(FocusColors.Muted, paletteFor(ThemeId.SIGNAL).textSecondary)
        assertEquals(FocusColors.Amber, paletteFor(ThemeId.SIGNAL).missed)
        assertNotEquals(FocusColors.Text, paletteFor(ThemeId.EMBER).textPrimary)
        assertNotEquals(FocusColors.Muted, paletteFor(ThemeId.EMBER).textSecondary)
        assertNotEquals(FocusColors.Amber, paletteFor(ThemeId.EMBER).missed)
        assertNotEquals(
            paletteFor(ThemeId.STILL_WATER).textPrimary,
            paletteFor(ThemeId.CLEAR_LINE).textPrimary,
        )
        var themeId by mutableStateOf(ThemeId.SIGNAL)
        val bands = noonBands()
        compose.setContent {
            FocusStackTheme(themeId = themeId) {
                HomeBandsSection(
                    bands = bands,
                    filled = filledFrom(bands),
                    hasRoom = false,
                    todayLimit = 3,
                    reducedMotion = true,
                    somethingCurrent = false,
                    onOpenFocus = {},
                    onStart = {},
                    onDone = {},
                    onBringBack = { _, _ -> },
                    onInterrupt = {},
                    onReorder = {},
                    onEditTask = {},
                    onCheckOffRepeat = {},
                    onEditRepeat = {},
                )
            }
        }
        compose.waitForIdle()
        for (id in THEMES) {
            compose.runOnIdle { themeId = id }
            compose.waitForIdle()
            val palette = paletteFor(id)
            assertEquals(id.name, palette.textSecondary, labelColor("MORNING"))
            assertEquals(id.name, palette.now, labelColor("NOON · NOW"))
            assertNotEquals(id.name, palette.textSecondary, labelColor("NOON · NOW"))
            assertEquals(id.name, palette.textSecondary, labelColor("NIGHT"))
            assertEquals(id.name, palette.textSecondary, labelColor("ANYTIME"))
            assertEquals(id.name, palette.textSecondary, labelColor(doneOf(1, 1)))
        }
    }

    private fun showHome(
        env: CloseableEnv,
        layout: () -> HomeLayout,
    ) {
        compose.setContent {
            val state by env.viewModel.uiState.collectAsState()
            FocusStackTheme {
                HomeScreen(
                    state = state,
                    viewModel = env.viewModel,
                    onOpenFocus = {},
                    onOpenInbox = {},
                    onOpenScheduled = {},
                    onOpenDone = {},
                    homeLayout = layout(),
                )
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun openEnv(): CloseableEnv {
        closeOpened()
        val context: Context = ApplicationProvider.getApplicationContext()
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val preferences = PreferencesDataStore(context, "home-layout-${UUID.randomUUID()}")
        val clock = AppClock { atNoon() }
        val repository = FocusRepositoryImpl(database, preferences, clock)
        val repeats = RepeatsService(repository, clock = clock)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(
                            FocusUseCases(repository),
                            repeats,
                            clock,
                        ) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        return CloseableEnv(store, database, viewModel, FocusUseCases(repository), repeats).also {
            opened.add(it)
        }
    }

    private fun labelColor(text: String): Color {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    private fun doneOf(
        done: Int,
        total: Int,
    ): String = compose.activity.getString(R.string.home_band_count_of_done, done, total)

    private fun noonBands(): HomeBands = HomeBands(
        morning =
        group(
            HomeBand.MORNING,
            BandPhase.PAST,
            repeats = listOf(repeatRow("Morning pills", RepeatBand.MORNING, RepeatDayStatus.DONE)),
            counts = HomeBandCounts(done = 1, missed = 1, left = 0),
        ),
        noon =
        group(
            HomeBand.NOON,
            BandPhase.OPEN,
            tasks = listOf(task("noon", "Noon walk")),
            counts = HomeBandCounts(left = 1),
        ),
        night =
        group(
            HomeBand.NIGHT,
            BandPhase.FUTURE,
            repeats = listOf(repeatRow("Night stretch", RepeatBand.NIGHT, RepeatDayStatus.OPEN)),
            counts = HomeBandCounts(left = 2),
        ),
        anytime =
        group(
            HomeBand.ANYTIME,
            BandPhase.ALWAYS_OPEN,
            tasks = listOf(task("any", "Loose thread")),
        ),
        openBand = HomeBand.NOON,
    )

    private fun group(
        band: HomeBand,
        phase: BandPhase,
        tasks: List<Task> = emptyList(),
        repeats: List<RepeatRow> = emptyList(),
        counts: HomeBandCounts = HomeBandCounts(),
    ): HomeBandGroup = HomeBandGroup(
        band = band,
        tasks = tasks,
        repeats = repeats,
        counts = counts,
        phase = phase,
    )

    private fun filledFrom(bands: HomeBands): List<Pair<SlotUi, TaskUi>> = bands.inOrder().flatMap { group ->
        group.tasks.map { task ->
            val ui = TaskUi(id = task.id, title = task.title, returnCue = task.returnCue)
            SlotUi(SlotRole.OPEN, ui) to ui
        }
    }

    private fun task(
        id: String,
        title: String,
    ): Task = Task(
        id = id,
        title = title,
        bucket = TaskBucket.TODAY,
        status = TaskStatus.OPEN,
        returnCue = null,
        sortOrder = 0,
        createdAtEpochMillis = 0,
        completedAtEpochMillis = null,
    )

    private fun repeatRow(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
    ): RepeatRow = RepeatRow(
        occurrence =
        RepeatOccurrence(
            repeat =
            Repeat(
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
        ),
        everyNDays = 1,
    )

    private fun closeOpened() {
        opened.forEach { env ->
            env.store.clear()
            env.database.close()
        }
        opened.clear()
    }

    private class CloseableEnv(
        val store: ViewModelStore,
        val database: FocusStackDatabase,
        val viewModel: FocusStackViewModel,
        val useCases: FocusUseCases,
        val repeats: RepeatsService,
    )

    private companion object {
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneId = ZoneId.systemDefault()
        val THEMES: List<ThemeId> =
            listOf(ThemeId.SIGNAL, ThemeId.STILL_WATER, ThemeId.EMBER, ThemeId.CLEAR_LINE)

        fun atNoon(): Long = DAY.atTime(LocalTime.of(13, 0)).atZone(ZONE).toInstant().toEpochMilli()
    }
}
