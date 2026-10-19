// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.testing.DEFAULT_TEST_INSTANT
import app.backpin.focus.testing.DEFAULT_TEST_ZONE
import app.backpin.focus.testing.InMemoryFocusRule
import app.backpin.focus.testing.defaultTodayTitles
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureDestinationTest {
    @get:Rule
    val focus = InMemoryFocusRule()

    private val repository get() = focus.repository

    @Test
    fun inboxLeavesTheActiveTaskAlone() = runBlocking {
        val current = repository.seedFullToday(listOf("Hold this")).single()
        repository.start(current.id)
        val before = snapshot().frames.single { it.state == FrameState.ACTIVE }.id
        repository.capture("Buy milk")
        val after = snapshot()
        assertEquals(before, after.frames.single { it.state == FrameState.ACTIVE }.id)
        assertEquals(TaskBucket.INBOX, taskNamed("Buy milk").bucket)
        assertTrue(after.frames.none { it.taskId == taskNamed("Buy milk").id })
    }

    @Test
    fun todayPlacesWithoutStarting() = runBlocking {
        val outcome = repository.captureIntoToday("Write the notes")
        assertTrue(outcome is PromoteOutcome.Promoted)
        val task = taskNamed("Write the notes")
        assertEquals(TaskBucket.TODAY, task.bucket)
        assertTrue(snapshot().frames.none { it.taskId == task.id })
        assertNull(snapshot().frames.firstOrNull { it.state == FrameState.ACTIVE })
    }

    @Test
    fun aFreshCaptureOnAFullTodayLandsInInboxWithItsId() = runBlocking {
        val rows =
            listOf(
                FullCaptureRow("Today while idle", startActive = false, captureIntoToday = true),
                FullCaptureRow("Today while active", startActive = true, captureIntoToday = true),
                FullCaptureRow("Start while idle", startActive = false, captureIntoToday = false),
                FullCaptureRow("Start while active", startActive = true, captureIntoToday = false),
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val repo = fixture.repository
                val today = repo.seedFullToday()
                if (row.startActive) {
                    repo.start(today.first().id)
                }
                val blocked =
                    if (row.captureIntoToday) {
                        repo.captureIntoToday("Call the school")
                    } else {
                        repo.captureAndStart("Call the school")
                    }
                val swap = blocked as PromoteOutcome.NeedsSwap
                val kept =
                    repo
                        .observeSnapshot()
                        .first()
                        .tasks
                        .single { it.title == "Call the school" }
                assertEquals(row.name, TaskBucket.INBOX, kept.bucket)
                assertEquals(row.name, TaskStatus.OPEN, kept.status)
                assertEquals(row.name, kept.id, swap.incomingTaskId)
                assertEquals(row.name, row.startActive, swap.canInterrupt)
                val openToday =
                    repo
                        .observeSnapshot()
                        .first()
                        .tasks
                        .filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }
                assertEquals(row.name, defaultTodayTitles().toSet(), openToday.map { it.title }.toSet())
                val frames = repo.observeSnapshot().first().frames
                assertTrue(row.name, frames.none { it.taskId == kept.id })
                if (row.startActive) {
                    assertEquals(
                        row.name,
                        today.first().id,
                        frames.single { it.state == FrameState.ACTIVE }.taskId,
                    )
                } else {
                    assertNull(row.name, frames.firstOrNull { it.state == FrameState.ACTIVE })
                }
            }
        }
    }

    @Test
    fun discardCaptureDeletesOnlyAnOpenInboxTask() = runBlocking {
        val laterDay =
            DEFAULT_TEST_INSTANT.atZone(DEFAULT_TEST_ZONE).toLocalDate().toEpochDay() + 3
        val rows =
            listOf(
                DiscardRow("open Inbox", deleted = true) { repo ->
                    repo.seedInbox("Call the school").single().id
                },
                DiscardRow("completed", deleted = false) { repo ->
                    val id = repo.seedInbox("Call the school").single().id
                    repo.completeTask(id)
                    id
                },
                DiscardRow("moved to Today", deleted = false) { repo ->
                    val id = repo.seedInbox("Call the school").single().id
                    check(repo.promote(id) is PromoteOutcome.Promoted)
                    id
                },
                DiscardRow("moved to Later", deleted = false) { repo ->
                    val id = repo.seedInbox("Call the school").single().id
                    repo.demote(id)
                    id
                },
                DiscardRow("scheduled", deleted = false) { repo ->
                    repo.captureScheduled("Call the school", laterDay)
                    repo.observeSnapshot().first().tasks.single { it.title == "Call the school" }.id
                },
                DiscardRow("countdown", deleted = false) { repo ->
                    repo.captureCountdown("Call the school", laterDay)
                    repo.observeSnapshot().first().tasks.single { it.title == "Call the school" }.id
                },
                DiscardRow("open frame", deleted = false) { repo ->
                    val parent = repo.seedFullToday(listOf("Hold this")).single()
                    repo.start(parent.id)
                    repo.interrupt("Call the school").id
                },
                DiscardRow("missing id", deleted = false) { "missing-task" },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val repo = fixture.repository
                val sentinel = repo.seedInbox("Keep me").single()
                val target = row.create(repo)
                repo.discardCapture(target)
                val after = repo.observeSnapshot().first()
                assertTrue(row.name, after.tasks.any { it.id == sentinel.id })
                val remaining = after.tasks.firstOrNull { it.id == target }
                if (row.deleted) {
                    assertNull(row.name, remaining)
                } else if (target != "missing-task") {
                    assertTrue(row.name, remaining != null)
                } else {
                    assertEquals(row.name, 1, after.tasks.size)
                }
            }
        }
    }

    @Test
    fun scheduleStaysOutsideInboxTodayAndLater() = runBlocking {
        val day = LocalDate.of(2026, 10, 6).toEpochDay()
        repository.captureScheduled("Talk to Dana", day)
        val task = taskNamed("Talk to Dana")
        assertEquals(TaskBucket.SCHEDULED, task.bucket)
        assertEquals(day, task.scheduledOnEpochDay)
        assertTrue(snapshot().frames.none { it.taskId == task.id })
    }

    @Test
    fun startWithNothingActiveJustStarts() = runBlocking {
        val outcome = repository.captureAndStart("Write the notes")
        assertTrue(outcome is PromoteOutcome.Promoted)
        val task = taskNamed("Write the notes")
        val active = snapshot().frames.single { it.state == FrameState.ACTIVE }
        assertEquals(task.id, active.taskId)
        assertNull(active.parentFrameId)
        assertEquals(TaskBucket.TODAY, task.bucket)
    }

    @Test
    fun startWhileActiveIsASwitch() = runBlocking {
        val current = repository.seedFullToday(listOf("Hold this")).single()
        repository.start(current.id)
        val outcome = repository.captureAndStart("Call Dana")
        assertTrue(outcome is PromoteOutcome.Promoted)
        val snapshot = snapshot()
        val active = snapshot.frames.single { it.state == FrameState.ACTIVE }
        val started = taskNamed("Call Dana")
        assertEquals(started.id, active.taskId)
        assertNull(active.parentFrameId)
        assertEquals(FrameState.PAUSED, snapshot.frames.single { it.taskId == current.id }.state)
        assertEquals(TaskBucket.TODAY, taskNamed("Call Dana").bucket)
        assertEquals(TaskBucket.TODAY, task(current.id).bucket)
    }

    private suspend fun snapshot(): FocusSnapshot = repository.observeSnapshot().first()

    private suspend fun task(id: String) = snapshot().tasks.single { it.id == id }

    private suspend fun taskNamed(title: String) = snapshot().tasks.single { it.title == title }

    private data class FullCaptureRow(
        val name: String,
        val startActive: Boolean,
        val captureIntoToday: Boolean,
    )

    private data class DiscardRow(
        val name: String,
        val deleted: Boolean,
        val create: suspend (FocusRepository) -> String,
    )
}
