// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
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
import app.backpin.focus.ui.FocusApp
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
        showHome(HomeLayout.BANDS)
        compose.onNodeWithText("Morning").assertIsDisplayed()
        compose.onNodeWithText("Noon").assertIsDisplayed()
        compose.onNodeWithText("Night").assertIsDisplayed()
        compose.onNodeWithText("Anytime").assertIsDisplayed()
        compose.onNodeWithText("TODAY").assertDoesNotExist()
        compose.onNodeWithText("REPEAT").assertDoesNotExist()

        showHome(HomeLayout.FLAT)
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithText("Noon").assertDoesNotExist()
        compose.onNodeWithText("Night").assertDoesNotExist()
        compose.onNodeWithText("Anytime").assertDoesNotExist()
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
        showHome(HomeLayout.FLAT, env = env)
        compose.waitUntil(timeoutMillis = 5_000) {
            env.viewModel.uiState.value.ready &&
                env.viewModel.repeatRows.value.rows.any { it.repeat.title == "Take meds" } &&
                env.viewModel.uiState.value.todaySlots.any { it.task?.title == "Buy milk" }
        }
        compose.onNodeWithText("TODAY").assertIsDisplayed()
        compose.onNodeWithText("REPEAT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Buy milk").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Take meds", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithText("Noon").assertDoesNotExist()
        compose.onNodeWithText("Night").assertDoesNotExist()

        showHome(HomeLayout.BANDS, env = env)
        compose.waitUntil(timeoutMillis = 5_000) { env.viewModel.uiState.value.ready }
        compose.onNodeWithText("TODAY").assertDoesNotExist()
        compose.onNodeWithText("REPEAT").assertDoesNotExist()
        compose.onNodeWithText("Noon").assertIsDisplayed()
        compose.onNodeWithText("Night").assertIsDisplayed()
        compose.onNodeWithText("Anytime").assertIsDisplayed()
        compose.onNodeWithText("Buy milk", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Take meds", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun bandHeaderColorsFollowSelectedThemeViaLivePalette() {
        assertEquals(FocusColors.Text, paletteFor(ThemeId.SIGNAL).textPrimary)
        assertEquals(FocusColors.Muted, paletteFor(ThemeId.SIGNAL).textSecondary)
        assertNotEquals(FocusColors.Text, paletteFor(ThemeId.EMBER).textPrimary)
        assertNotEquals(FocusColors.Muted, paletteFor(ThemeId.EMBER).textSecondary)
        assertNotEquals(
            paletteFor(ThemeId.STILL_WATER).textPrimary,
            paletteFor(ThemeId.CLEAR_LINE).textPrimary,
        )
        for (themeId in THEMES) {
            showBands(themeId)
            val palette = paletteFor(themeId)
            assertEquals(themeId.name, palette.textSecondary, labelColor(morningPastSummary()))
            assertEquals(themeId.name, palette.textPrimary, labelColor("Noon"))
            assertEquals(themeId.name, palette.textSecondary, labelColor("Night"))
            assertEquals(themeId.name, palette.textPrimary, labelColor("Anytime"))
            assertEquals(themeId.name, palette.textSecondary, labelColor(leftCount(1)))
        }
    }

    private fun showHome(
        layout: HomeLayout,
        env: CloseableEnv? = null,
    ): CloseableEnv {
        val shown = env ?: openEnv()
        compose.setContent {
            FocusStackTheme {
                FocusApp(
                    viewModel = shown.viewModel,
                    homeLayout = layout,
                )
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitUntil(timeoutMillis = 5_000) { shown.viewModel.uiState.value.ready }
        return shown
    }

    private fun showBands(themeId: ThemeId) {
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
                    onLater = {},
                    onInterrupt = {},
                    onReorder = {},
                    onEditTask = {},
                    onCheckOffRepeat = {},
                    onEditRepeat = {},
                )
            }
        }
        compose.waitForIdle()
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

    private fun morningPastSummary(): String = compose.activity.getString(
        R.string.home_band_summary,
        compose.activity.getString(R.string.home_band_morning),
        listOf(
            compose.activity.getString(R.string.home_band_count_done, 1),
            compose.activity.getString(R.string.home_band_count_missed, 1),
        ).joinToString(compose.activity.getString(R.string.home_band_count_separator)),
    )

    private fun leftCount(left: Int): String = compose.activity.getString(R.string.home_band_count_left, left)

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
