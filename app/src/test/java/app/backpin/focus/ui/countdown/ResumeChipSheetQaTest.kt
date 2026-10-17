// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.backInDaysLabel
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ResumeChipSheetQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resumeSheetShowsTomorrowIn3DaysNextWeekAndPickADate() {
        var liveText by mutableStateOf(Color.Unspecified)
        showResumeSheet(onLive = { text, _, _ -> liveText = text })
        assertEquals(RESUME_CHIPS, sheetChips(RESUME_CHIPS))
        RESUME_CHIPS.forEach { label ->
            assertSheetChip(label)
            assertEquals(label, liveText, labelColor(label))
        }
    }

    @Test
    fun resumeSheetHasNoTextFieldOrStartInNDaysInput() {
        showResumeSheet()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Days from today").assertDoesNotExist()
        compose.onNodeWithText("Start in", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Start in 0 days").assertDoesNotExist()
    }

    @Test
    fun resumeSheetShowsNoDesignNote() {
        showResumeSheet()
        compose.onNodeWithText("or choose").assertDoesNotExist()
        compose.onNodeWithText("uses this event's own interval", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Back leaves the event as it was.", substring = true).assertDoesNotExist()
    }

    @Test
    fun resumeSheetLeadsWithInNDaysWhenSuggestedIsNot1Or3Or7() {
        var liveText by mutableStateOf(Color.Unspecified)
        var liveMuted by mutableStateOf(Color.Unspecified)
        var liveMint by mutableStateOf(Color.Unspecified)
        showResumeSheet(
            suggestedDays = 5,
            onLive = { text, muted, mint ->
                liveText = text
                liveMuted = muted
                liveMint = mint
            },
        )
        val chips = listOf("In 5 days") + RESUME_CHIPS
        assertEquals(chips, sheetChips(chips))
        assertSheetChip("In 5 days")
        val color = labelColor("In 5 days")
        assertEquals("In 5 days", liveText, color)
        assertNotEquals("In 5 days", liveMuted, color)
        assertNotEquals("In 5 days", liveMint, color)
        compose.onNodeWithText("or choose").assertDoesNotExist()
        compose.onNodeWithText("Again in 5 days", substring = true).assertDoesNotExist()
    }

    @Test
    fun resumeSheetOmitsSuggestedChipWhenItWouldDuplicateAStandardChip() {
        listOf(1, 3, 7).forEach { days ->
            showResumeSheet(suggestedDays = days)
            assertEquals(days.toString(), RESUME_CHIPS, sheetChips(RESUME_CHIPS))
            assertEquals("In 3 days", 1, sheetChips(listOf("In 3 days")).size)
            compose.onNodeWithText("In 1 day").assertDoesNotExist()
            compose.onNodeWithText("In 1 days").assertDoesNotExist()
            compose.onNodeWithText("In 7 days").assertDoesNotExist()
            compose.onNodeWithText("or choose").assertDoesNotExist()
            compose.onNodeWithText("Again in $days days", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun resumeSheetEnabledChipsUseLiveTextNotMintOrMuted() {
        var liveText by mutableStateOf(Color.Unspecified)
        var liveMuted by mutableStateOf(Color.Unspecified)
        var liveMint by mutableStateOf(Color.Unspecified)
        showResumeSheet(onLive = { text, muted, mint ->
            liveText = text
            liveMuted = muted
            liveMint = mint
        })
        RESUME_CHIPS.forEach { label ->
            assertSheetChip(label)
            val color = labelColor(label)
            assertEquals(label, liveText, color)
            assertNotEquals(label, liveMuted, color)
            assertNotEquals(label, liveMint, color)
            assertEquals("$label uses disabled alpha", 1f, color.alpha, 0.001f)
        }
    }

    @Test
    fun resumeSheetIn3DaysCommitsThreeDaysAndStaysOnHome() {
        withViewModel { viewModel, useCases, repository, database ->
            val today = LocalDate.now()
            seedArrivedEvent(useCases, repository, database, intervalDays = 180)
            compose.setContent {
                FocusStackTheme {
                    FocusApp(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Dentist checkup" }
            tap("Count down again")
            compose.onNode(hasText("Count down again") and isHeading()).assertExists()
            compose.onAllNodesWithText("Dentist checkup").assertCountEquals(2)
            assertEquals(RESUME_CHIPS, sheetChips(RESUME_CHIPS))
            tap("In 3 days")
            val expected = backInDaysLabel(3, today)
            await { viewModel.uiState.value.undo?.message == expected }
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist checkup" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(3).toEpochDay() &&
                    event.countdownIntervalDays == 3 &&
                    parentWaits(snapshot)
            }
            compose.onNodeWithText("Count down again").assertDoesNotExist()
            compose.onNodeWithText("Home").assertIsSelected()
            compose.onNodeWithText(expected).assertExists()
        }
    }

    @Test
    fun resumeSheetPickADateOpensCalendarEarliestTomorrow() {
        var committed: Long? = null
        showResumeSheet(onCommit = { committed = it })
        assertSheetChip("Pick a date")
        tap("Pick a date")
        assertTodayCellDisabled(LocalDate.now())
        tap("OK")
        assertEquals(LocalDate.now().plusDays(1).toEpochDay(), committed)
    }

    private fun seedArrivedEvent(
        useCases: FocusUseCases,
        repository: FocusRepositoryImpl,
        database: FocusStackDatabase,
        intervalDays: Int,
    ) {
        runBlocking {
            useCases.capture("Write the report")
            val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
            useCases.promote(work.id)
            useCases.start(work.id)
            useCases.captureCountdown("Dentist checkup", LocalDate.now().toEpochDay())
            val dentist = useCases.observeSnapshot().first().tasks.single { it.title == "Dentist checkup" }
            val row = database.taskDao().getById(dentist.id) ?: error("Missing dentist")
            database.taskDao().upsert(row.copy(countdownIntervalDays = intervalDays))
            repository.releaseDueCountdowns()
        }
    }

    private fun parentWaits(snapshot: FocusSnapshot): Boolean {
        val work = snapshot.tasks.firstOrNull { it.title == "Write the report" } ?: return false
        val frame = snapshot.frames.firstOrNull { it.taskId == work.id } ?: return false
        return frame.state == FrameState.AWAITING_RESUME && snapshot.frames.none { it.state == FrameState.ACTIVE }
    }

    private fun withViewModel(
        block: (FocusStackViewModel, FocusUseCases, FocusRepositoryImpl, FocusStackDatabase) -> Unit,
    ) {
        val database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "resume-chip-${UUID.randomUUID()}"),
                clock = AppClock { System.currentTimeMillis() },
            )
        val useCases = FocusUseCases(repository)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            block(viewModel, useCases, repository, database)
        } finally {
            store.clear()
            database.close()
        }
    }

    private fun showResumeSheet(
        suggestedDays: Int? = 25,
        onCommit: (Long) -> Unit = {},
        onLive: (Color, Color, Color) -> Unit = { _, _, _ -> },
    ) {
        compose.setContent {
            FocusStackTheme {
                val text = FocusColors.Live.Text
                val muted = FocusColors.Live.Muted
                val mint = FocusColors.Live.Mint
                SideEffect { onLive(text, muted, mint) }
                ResumeCountdownSheet(
                    title = "Dentist checkup",
                    suggestedDays = suggestedDays,
                    onDismiss = {},
                    onCommit = onCommit,
                )
            }
        }
    }

    private fun sheetChips(labels: List<String>): List<String> {
        data class Hit(
            val label: String,
            val left: androidx.compose.ui.unit.Dp,
            val top: androidx.compose.ui.unit.Dp,
        )

        val hits = mutableListOf<Hit>()
        labels.forEach { label ->
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

    private fun assertTodayCellDisabled(today: LocalDate) {
        val spoken = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()).format(today)
        val short = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()).format(today)
        listOf(
            hasContentDescription(spoken, substring = true),
            hasContentDescription(short, substring = true),
        ).forEach { matcher ->
            val count = compose.onAllNodes(matcher).fetchSemanticsNodes().size
            if (count > 0) {
                compose.onNode(matcher).assertIsNotEnabled()
            }
        }
        compose.onNodeWithText("OK").assertIsEnabled()
    }

    private fun assertSheetChip(label: String) {
        val node = compose.onNodeWithText(label)
        val bounds = node.getBoundsInRoot()
        assertTrue("$label is $bounds tall", bounds.bottom - bounds.top >= 50.dp)
        node.assertIsEnabled().assertHasClickAction()
    }

    private fun labelColor(
        text: String,
        substring: Boolean = false,
    ): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose
            .onNodeWithText(text, substring = substring, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    private fun tap(label: String) {
        compose.waitForIdle()
        val node = compose.onNodeWithText(label)
        node.assertIsEnabled().assertHasClickAction()
        node.performTouchInput { click(center) }
        compose.waitForIdle()
    }

    private fun await(predicate: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            predicate()
        }
    }

    private fun awaitSnapshot(
        useCases: FocusUseCases,
        predicate: (FocusSnapshot) -> Boolean,
    ) {
        await { runBlocking { predicate(useCases.observeSnapshot().first()) } }
    }

    private companion object {
        val RESUME_CHIPS = listOf("Tomorrow", "In 3 days", "Next week", "Pick a date")
    }
}
