// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.isCountdownRearmDay
import app.backpin.focus.testing.FakeAppClock
import app.backpin.focus.testing.TestFocusFixture
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountdownRearmTest {
    @Test
    fun isCountdownRearmDayAllowsTomorrowThrough365() {
        val today = todayOn(FakeAppClock())
        REARM_CASES.forEach { case ->
            assertEquals(
                "days=${case.days}",
                case.accepted,
                isCountdownRearmDay(today.plusDays(case.days).toEpochDay(), today),
            )
        }
    }

    @Test
    fun detachCountdownWritesOnlyInRangeDays() {
        REARM_CASES.forEach { case ->
            useTestFocus { fixture ->
                runBlocking {
                    val today = todayOn(fixture.clock)
                    val event = arriveToday(fixture, today)
                    val beforeRow =
                        fixture.database.taskDao().getById(event.id) ?: error("missing ${event.id}")
                    val beforeFrameRow = fixture.database.focusFrameDao().openFrameForTask(event.id)
                    val beforeSnap = fixture.useCases.observeSnapshot().first()
                    val beforeTask = beforeSnap.tasks.single { it.id == event.id }
                    val target = today.plusDays(case.days).toEpochDay()
                    val outcome =
                        fixture.useCases.detachInterruption(
                            event.id,
                            DetachDestination.Countdown(target),
                        )
                    if (case.accepted) {
                        assertEquals("days=${case.days}", PromoteOutcome.Promoted, outcome)
                        val after =
                            fixture.useCases.observeSnapshot().first().tasks.single { it.id == event.id }
                        assertEquals(target, after.scheduledOnEpochDay)
                        assertEquals(TaskBucket.COUNTDOWN, after.bucket)
                        assertEquals(
                            target,
                            fixture.database.taskDao().getById(event.id)?.scheduledOnEpochDay,
                        )
                    } else {
                        assertTrue("days=${case.days}", outcome is PromoteOutcome.Rejected)
                        assertEquals(beforeRow, fixture.database.taskDao().getById(event.id))
                        assertEquals(
                            beforeFrameRow,
                            fixture.database.focusFrameDao().openFrameForTask(event.id),
                        )
                        val after = fixture.useCases.observeSnapshot().first()
                        assertEquals(beforeTask, after.tasks.single { it.id == event.id })
                        assertEquals(
                            beforeSnap.frames.single { it.taskId == event.id },
                            after.frames.single { it.taskId == event.id },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun detachCountdownClampsLastInterval500To365() {
        useTestFocus { fixture ->
            runBlocking {
                val today = todayOn(fixture.clock)
                val event = arriveToday(fixture, today)
                val row = fixture.database.taskDao().getById(event.id) ?: error("missing ${event.id}")
                fixture.database.taskDao().upsert(row.copy(countdownIntervalDays = 500))
                val tooFar = today.plusDays(500).toEpochDay()
                assertEquals(false, isCountdownRearmDay(tooFar, today))
                val rejected =
                    fixture.useCases.detachInterruption(
                        event.id,
                        DetachDestination.Countdown(tooFar),
                    )
                assertTrue(rejected is PromoteOutcome.Rejected)
                assertEquals(
                    500,
                    fixture.database.taskDao().getById(event.id)?.countdownIntervalDays,
                )
                assertEquals(
                    FrameState.ACTIVE,
                    fixture.useCases.observeSnapshot().first().frames.single { it.taskId == event.id }.state,
                )
                val clamped = today.plusDays(365).toEpochDay()
                assertEquals(true, isCountdownRearmDay(clamped, today))
                assertEquals(
                    PromoteOutcome.Promoted,
                    fixture.useCases.detachInterruption(
                        event.id,
                        DetachDestination.Countdown(clamped),
                    ),
                )
                val stored =
                    fixture.useCases.observeSnapshot().first().tasks.single { it.id == event.id }
                assertEquals(clamped, stored.scheduledOnEpochDay)
                assertEquals(365, stored.countdownIntervalDays)
            }
        }
    }

    private fun todayOn(clock: FakeAppClock): LocalDate = Instant
        .ofEpochMilli(clock.nowEpochMillis())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    private suspend fun arriveToday(
        fixture: TestFocusFixture,
        today: LocalDate,
    ): Task {
        val work = fixture.repository.seedFullToday(listOf("Write the report")).single()
        fixture.useCases.start(work.id)
        fixture.useCases.captureCountdown("Dentist checkup", today.toEpochDay())
        return fixture.repository.releaseDueCountdowns() ?: error("Countdown did not arrive")
    }

    private data class RearmCase(
        val days: Long,
        val accepted: Boolean,
    )

    private companion object {
        val REARM_CASES =
            listOf(
                RearmCase(0, accepted = false),
                RearmCase(1, accepted = true),
                RearmCase(365, accepted = true),
                RearmCase(366, accepted = false),
            )
    }
}
