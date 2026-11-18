// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.data

import app.backpin.focus.domain.ConvertToRepeatResult
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.testing.seedFullToday
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConvertToRepeatTest {
    private data class RejectRow(
        val name: String,
        val setup: suspend (FocusRepository) -> String,
    )

    @Test
    fun convertRejectsMissingDoneActiveNowAndOpenFrame() = runBlocking {
        val day = 20_000L
        val rows =
            listOf(
                RejectRow("missing") { "missing-task" },
                RejectRow("done") { repo ->
                    val id = repo.seedInbox("Done one").single().id
                    repo.completeTask(id)
                    id
                },
                RejectRow("active Now") { repo ->
                    val id = repo.seedInbox("Active one").single().id
                    check(repo.promote(id) is PromoteOutcome.Promoted)
                    repo.start(id)
                    id
                },
                RejectRow("open frame") { repo ->
                    val parent = repo.seedFullToday(listOf("Hold this")).single()
                    repo.start(parent.id)
                    repo.interrupt("Nested").id
                },
            )
        for (row in rows) {
            useTestFocus { fixture ->
                val repo = fixture.repository
                val taskId = row.setup(repo)
                val result =
                    repo.convertToRepeat(
                        taskId = taskId,
                        title = "Meds",
                        band = RepeatBand.MORNING,
                        everyNDays = 1,
                        anchorEpochDay = day,
                        reminderOn = true,
                    )
                assertEquals(row.name, ConvertToRepeatResult.Rejected, result)
            }
        }
    }

    @Test
    fun convertDeletesTaskAndCreatesRepeatThenUndoRestoresSameRow() = runBlocking {
        useTestFocus { fixture ->
            val repo = fixture.repository
            val day = fixture.clock.localDate().toEpochDay()
            val inbox = repo.seedInbox("Before", "Convert me", "After").sortedBy { it.sortOrder }
            val target = inbox.single { it.title == "Convert me" }
            val beforeOrder = inbox.map { it.id }
            val result =
                repo.convertToRepeat(
                    taskId = target.id,
                    title = "Convert me",
                    band = RepeatBand.NOON,
                    everyNDays = 2,
                    anchorEpochDay = day,
                    reminderOn = false,
                )
            check(result is ConvertToRepeatResult.Converted)
            val after = repo.observeSnapshot().first()
            assertTrue(after.tasks.none { it.id == target.id })
            assertEquals(
                listOf("Before", "After"),
                after.tasks.filter { it.bucket == TaskBucket.INBOX }.map { it.title },
            )
            val repeat = repo.observeRepeats().first().single { it.id == result.repeatId }
            assertEquals("Convert me", repeat.title)
            assertEquals(RepeatBand.NOON, repeat.band)
            assertEquals(2, repeat.everyNDays)

            repo.undoConvertToRepeat(result)
            val restored = repo.observeSnapshot().first()
            val task = restored.tasks.single { it.id == target.id }
            assertEquals(TaskBucket.INBOX, task.bucket)
            assertEquals(TaskStatus.OPEN, task.status)
            assertEquals(target.title, task.title)
            assertEquals(target.sortOrder, task.sortOrder)
            assertEquals(
                setOf("Before", "Convert me", "After"),
                restored.tasks.filter { it.bucket == TaskBucket.INBOX }.map { it.title }.toSet(),
            )
            assertTrue(beforeOrder.contains(task.id))
            assertTrue(repo.observeRepeats().first().none { it.id == result.repeatId })
        }
    }

    @Test
    fun undoIsNoOpWhenRepeatAlreadyGone() = runBlocking {
        useTestFocus { fixture ->
            val repo = fixture.repository
            val day = fixture.clock.localDate().toEpochDay()
            val target = repo.seedInbox("Still here").single()
            val ghost =
                ConvertToRepeatResult.Converted(
                    repeatId = "gone-repeat",
                    task =
                    target.copy(
                        id = "deleted-task",
                        title = "Ghost",
                    ),
                )
            repo.undoConvertToRepeat(ghost)
            val after = repo.observeSnapshot().first()
            assertEquals(listOf(target.id), after.tasks.map { it.id })
            assertTrue(after.tasks.none { it.id == "deleted-task" })
            assertTrue(repo.observeRepeats().first().none { it.id == "gone-repeat" })
            // Keep the day reference used by convert paths so the clock seam stays explicit.
            assertEquals(day, fixture.clock.localDate().toEpochDay())
        }
    }

    @Test
    fun convertLeavesActiveFrameStateAlone() = runBlocking {
        useTestFocus { fixture ->
            val repo = fixture.repository
            val day = fixture.clock.localDate().toEpochDay()
            val parent = repo.seedFullToday(listOf("Hold this")).single()
            repo.start(parent.id)
            assertEquals(
                FrameState.ACTIVE,
                repo.observeSnapshot().first().frames.single { it.taskId == parent.id }.state,
            )
            val rejected =
                repo.convertToRepeat(
                    taskId = parent.id,
                    title = "Hold this",
                    band = RepeatBand.MORNING,
                    everyNDays = 1,
                    anchorEpochDay = day,
                    reminderOn = true,
                )
            assertEquals(ConvertToRepeatResult.Rejected, rejected)
            assertEquals(
                FrameState.ACTIVE,
                repo.observeSnapshot().first().frames.single { it.taskId == parent.id }.state,
            )
        }
    }
}
