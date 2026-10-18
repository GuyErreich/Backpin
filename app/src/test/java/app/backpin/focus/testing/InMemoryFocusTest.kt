// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InMemoryFocusTest {
    @get:Rule
    val focus =
        InMemoryFocusRule {
            FakeAppClock(
                initial = Instant.parse("2026-10-16T02:00:00Z"),
                initialZone = ZoneId.of("America/New_York"),
            )
        }

    @Test
    fun repositoryRoundTripUsesTheFixtureClock() = runBlocking {
        val written = focus.repository.seedInbox("Write the brief").single()
        val snapshot =
            focus.repository.observeSnapshot().awaitState { state ->
                state.tasks.any { it.id == written.id }
            }
        val task = snapshot.tasks.single()
        assertEquals("Write the brief", task.title)
        assertEquals(TaskBucket.INBOX, task.bucket)
        assertEquals(focus.clock.nowEpochMillis(), task.createdAtEpochMillis)
    }

    @Test
    fun repositoryAndRepeatsShareOneClockAndZone() = runBlocking {
        assertEquals(ZoneId.of("America/New_York"), focus.fixture.zone)
        assertEquals(focus.clock.zoneId(), focus.fixture.zoneProvider().invoke())
        assertEquals(LocalDate.of(2026, 10, 15), focus.clock.localDate())
        assertEquals(
            focus.clock.localEpochDay(),
            focus.repeats.observeLocalEpochDay().first(),
        )
        val repeat =
            focus.repository.seedRepeat(
                title = "Take meds",
                anchorEpochDay = focus.clock.localEpochDay(),
            )
        assertEquals(focus.clock.nowEpochMillis(), repeat.createdAtEpochMillis)
        val rows =
            focus.repeats.observeTodaysRepeats().awaitState { today ->
                today.morning.any { it.repeat.id == repeat.id }
            }
        assertEquals("Take meds", rows.morning.single().repeat.title)
    }

    @Test
    fun seedFullTodayFillsTheCapThroughRealQueries() = runBlocking {
        val today = focus.repository.seedFullToday()
        assertEquals(listOf("Write the brief", "Call Dana", "File the form"), today.map { it.title })
        assertTrue(today.all { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN })
        val blocked = focus.repository.captureIntoToday("Extra")
        assertTrue(blocked is PromoteOutcome.NeedsSwap)
        assertEquals(3, (blocked as PromoteOutcome.NeedsSwap).todayTasks.size)
    }

    @Test
    fun deriveUiUsesTheFixtureLocalDate() {
        val countdown =
            sampleTask(
                id = "event-1",
                title = "Dana's birthday",
                bucket = TaskBucket.COUNTDOWN,
                scheduledOnEpochDay = focus.clock.localEpochDay(),
            )
        val ui = focus.fixture.deriveUi(sampleSnapshot(tasks = listOf(countdown)))
        assertEquals("Today", ui.countdowns.single().dayLabel)
    }

    @Test
    fun useClosesTheDatabase() = runBlocking {
        val fixture = TestFocusFixture.open()
        val database = fixture.database
        fixture.use { opened ->
            assertSame(database, opened.database)
            opened.repository.seedInbox("Write the brief")
            assertTrue(opened.database.isOpen)
        }
        assertFalse(database.isOpen)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InMemoryFocusViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val focus = InMemoryFocusRule()

    @Test
    fun withViewModelBuildsThroughTheFactory() {
        focus.fixture.withViewModel { viewModel ->
            assertEquals(false, viewModel.uiState.value.captureOpen)
        }
    }
}
