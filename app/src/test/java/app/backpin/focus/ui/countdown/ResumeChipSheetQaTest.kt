// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.data.FocusRepositoryImpl
import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.data.PreferencesDataStore
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.inMemoryFocusDatabase
import app.backpin.focus.ui.FocusApp
import app.backpin.focus.ui.FocusStackViewModel
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
    fun startsOnHomeOpensFocusDetachCountDownAgainOpensResumeSheetInOrder() {
        withArrivedEvent { _, _, clock ->
            compose.onNodeWithText("Home").assertIsSelected()
            tapHomeNowCardOpensFocus()
            tapDetachCountDownAgain()
            assertResumeSheet(intervalDays = 180, today = clock.localDate())
        }
    }

    @Test
    fun focusDetachCountDownAgainOpensResumeSheetInOrder() {
        withArrivedEvent { _, _, clock ->
            tapFocusTabOpensFocus()
            tapDetachCountDownAgain()
            assertResumeSheet(intervalDays = 180, today = clock.localDate())
        }
    }

    private fun tapHomeNowCardOpensFocus() {
        compose.waitForIdle()
        val now = compose.onNode(hasContentDescription("Opens focus.", substring = true))
        now.assertHasClickAction()
        now.performTouchInput { click(center) }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        awaitFocusEventDetach()
    }

    private fun tapFocusTabOpensFocus() {
        tap("Focus")
        awaitFocusEventDetach()
    }

    private fun awaitFocusEventDetach() {
        await {
            compose.onAllNodesWithText("Count down again").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Focus").assertIsSelected()
        compose.onNodeWithText("Count down again").assertIsEnabled().assertHasClickAction()
    }

    private fun tapDetachCountDownAgain() {
        awaitFocusEventDetach()
        tap("Count down again")
    }

    private fun assertResumeSheet(
        intervalDays: Int,
        today: LocalDate,
    ) {
        val again = againInDaysLabel(intervalDays, today)
        await {
            compose.onAllNodesWithText(again, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(again).assertIsEnabled().assertHasClickAction()
        compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().let { nodes ->
            assertEquals("resume sheet days field", 1, nodes.size)
        }
        compose.onNodeWithText("Days from today").assertExists()
        compose.onNodeWithText("Pick a date").assertIsEnabled().assertHasClickAction()
        compose.onNodeWithContentDescription("Close").assertExists()
        compose.onNodeWithText("Count down again").assertDoesNotExist()
        compose.onNodeWithText("Countdown").assertDoesNotExist()
        compose.onNodeWithText("Coming up").assertExists()
        val againTop = compose.onNodeWithText(again).fetchSemanticsNode().boundsInRoot.top
        val pickTop = compose.onNodeWithText("Pick a date").fetchSemanticsNode().boundsInRoot.top
        val fieldTop = compose.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot.top
        assertTrue("Again in N days is primary", againTop < fieldTop)
        assertTrue("days field sits above Pick a date", fieldTop < pickTop)
    }

    private fun withArrivedEvent(
        block: (FocusStackViewModel, FocusUseCases, FakeAppClock) -> Unit,
    ) {
        withViewModel { viewModel, useCases, repository, database, clock ->
            seedArrivedEvent(useCases, repository, database, clock, intervalDays = 180)
            compose.setContent {
                FocusStackTheme {
                    FocusApp(viewModel)
                }
            }
            await { viewModel.uiState.value.now?.title == "Dentist checkup" }
            block(viewModel, useCases, clock)
        }
    }

    private fun seedArrivedEvent(
        useCases: FocusUseCases,
        repository: FocusRepositoryImpl,
        database: FocusStackDatabase,
        clock: FakeAppClock,
        intervalDays: Int,
    ) {
        runBlocking {
            useCases.capture("Write the report")
            val work = useCases.observeSnapshot().first().tasks.single { it.title == "Write the report" }
            useCases.promote(work.id)
            useCases.start(work.id)
            useCases.captureCountdown("Dentist checkup", clock.localDate().toEpochDay())
            val dentist = useCases.observeSnapshot().first().tasks.single { it.title == "Dentist checkup" }
            val row = database.taskDao().getById(dentist.id) ?: error("Missing dentist")
            database.taskDao().upsert(row.copy(countdownIntervalDays = intervalDays))
            repository.releaseDueCountdowns()
        }
    }

    private fun withViewModel(
        block: (FocusStackViewModel, FocusUseCases, FocusRepositoryImpl, FocusStackDatabase, FakeAppClock) -> Unit,
    ) {
        val clock = FakeAppClock()
        val database = inMemoryFocusDatabase(context)
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "resume-chip-${UUID.randomUUID()}"),
                clock = clock,
            )
        val useCases = FocusUseCases(repository)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases, clock = clock) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        try {
            block(viewModel, useCases, repository, database, clock)
        } finally {
            store.clear()
            database.close()
        }
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
}
