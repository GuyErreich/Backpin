// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import app.backpin.focus.data.FocusStackDatabase
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InMemoryFocusTest {
    @get:Rule
    val focus =
        InMemoryFocusRule {
            FakeAppClock(DATE_CROSSING_INSTANT, DEFAULT_TEST_ZONE)
        }

    @Test
    fun repositoryRoundTripUsesTheFixtureClock() = runBlocking {
        val written = focus.repository.seedInbox("Write the brief").single()
        val snapshot =
            focus.repository.observeSnapshot().awaitState { state ->
                state.tasks.any { it.id == written.id }
            }
        val task = snapshot.tasks.single { it.id == written.id }
        assertEquals("Write the brief", task.title)
        assertEquals(TaskBucket.INBOX, task.bucket)
        assertEquals(focus.clock.nowEpochMillis(), task.createdAtEpochMillis)
    }

    @Test
    fun repositoryAndRepeatsShareOneClockAndZone() = runBlocking {
        assertEquals(DEFAULT_TEST_ZONE, focus.fixture.zone)
        assertEquals(LocalDate.of(2026, 10, 15), focus.clock.localDate())
        assertEquals(focus.clock.localEpochDay(), focus.repeats.observeLocalEpochDay().first())
        val repeat =
            focus.repository.seedRepeat(
                title = sampleRepeat().title,
                anchorEpochDay = focus.clock.localEpochDay(),
            )
        assertEquals(focus.clock.nowEpochMillis(), repeat.createdAtEpochMillis)
        val rows =
            focus.repeats.observeTodaysRepeats().awaitState { today ->
                today.morning.any { it.repeat.id == repeat.id }
            }
        assertEquals(sampleRepeat().title, rows.morning.single().repeat.title)
    }

    @Test
    fun seedFullTodayFillsTheCapThroughRealQueries() = runBlocking {
        val today = focus.repository.seedFullToday()
        assertEquals(defaultTodayTitles(), today.map { it.title })
        assertTrue(today.all { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN })
        val blocked = focus.repository.captureIntoToday("Extra")
        assertTrue(blocked is PromoteOutcome.NeedsSwap)
        assertEquals(defaultTodayTitles().size, (blocked as PromoteOutcome.NeedsSwap).todayTasks.size)
    }

    @Test
    fun deriveUiUsesTheFixtureLocalDate() {
        val eventDay = LocalDate.of(2026, 10, 15).toEpochDay()
        val event = sampleTask(
            id = "event-1",
            title = "Dana's birthday",
            bucket = TaskBucket.COUNTDOWN,
            scheduledOnEpochDay = eventDay,
        )
        val rows =
            listOf(
                DeriveRow("New York still the 15th", ZoneId.of("America/New_York"), "Today"),
                DeriveRow("UTC already the 16th", ZoneId.of("UTC"), "Passed"),
            )
        for (row in rows) {
            useTestFocus(clock = FakeAppClock(DATE_CROSSING_INSTANT, row.zone)) { fixture ->
                val ui = fixture.deriveUi(sampleSnapshot(tasks = listOf(event)))
                assertEquals(row.name, row.dayLabel, ui.countdowns.single().dayLabel)
            }
        }
    }

    @Test
    fun useClosesTheDatabaseAfterARealWrite() = runBlocking {
        lateinit var database: FocusStackDatabase
        useTestFocus { fixture ->
            database = fixture.database
            fixture.repository.seedInbox("Write the brief")
            val title =
                fixture.repository
                    .observeSnapshot()
                    .awaitState { it.tasks.isNotEmpty() }
                    .tasks
                    .single()
                    .title
            assertEquals("Write the brief", title)
            assertTrue(database.isOpen)
        }
        assertFalse(database.isOpen)
    }

    private data class DeriveRow(
        val name: String,
        val zone: ZoneId,
        val dayLabel: String,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InMemoryFocusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher = UnconfinedTestDispatcher())

    @get:Rule
    val focus = InMemoryFocusRule()

    @Test
    fun withViewModelShowsSeededInbox() = runBlocking {
        focus.repository.seedInbox("Write the brief")
        focus.fixture.withViewModel { viewModel ->
            val state =
                viewModel.uiState.first { ui ->
                    ui.inboxTasks.any { it.title == "Write the brief" }
                }
            assertEquals(listOf("Write the brief"), state.inboxTasks.map { it.title })
        }
    }
}
