// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ResumeChipSheetQaTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun resumeSheetUsesSharedChipsAndStillStartsInTypedDays() {
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
            compose.onNodeWithText("or choose").assertExists()
            compose.onNodeWithText("Pick a date").assertExists()
            assertSheetChip("Pick a date")
            assertEquals(FocusColors.Lavender, labelColor("Again in 180 days", substring = true))
            resumeDaysField().performTextInput("45")
            assertSheetChip("Start in 45 days")
            assertEquals(FocusColors.Lavender, labelColor("Start in 45 days"))
            assertEquals(FocusColors.Text, labelColor("Pick a date"))
            tap("Start in 45 days")

            val expected = backInDaysLabel(45, today)
            await { viewModel.uiState.value.undo?.message == expected }
            awaitSnapshot(useCases) { snapshot ->
                val event = snapshot.tasks.single { it.title == "Dentist checkup" }
                event.bucket == TaskBucket.COUNTDOWN &&
                    event.scheduledOnEpochDay == today.plusDays(45).toEpochDay() &&
                    event.countdownIntervalDays == 45 &&
                    parentWaits(snapshot)
            }
            compose.onNodeWithText("Count down again").assertDoesNotExist()
            compose.onNodeWithText("Home").assertIsSelected()
            compose.onNodeWithText(expected).assertExists()
        }
    }

    @Test
    fun resumeSheetPickADateSitsInTheChipRow() {
        var committed: Long? = null
        compose.setContent {
            FocusStackTheme {
                ResumeCountdownSheet(
                    title = "Dentist checkup",
                    suggestedDays = 25,
                    onDismiss = {},
                    onCommit = { committed = it },
                )
            }
        }
        compose.onNodeWithText("Count down again").assertExists()
        compose.onNodeWithText("Again in 25 days", substring = true).assertExists()
        assertSheetChip("Pick a date")
        compose.onNodeWithText("Start in 0 days").assertExists()
        tap("Pick a date")
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

    private fun resumeDaysField() = compose.onNode(hasSetTextAction() and hasText("Days from today"))

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
}
