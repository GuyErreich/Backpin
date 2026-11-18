// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.os.Looper
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.testing.seedInbox
import app.backpin.focus.testing.useTestFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConvertToRepeatFlowTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
    }

    @Test
    fun editRepeatSaveLeavesNoDuplicateTaskAndUndoRestoresIt() = runBlocking {
        useTestFocus { fixture ->
            val day = fixture.clock.localDate().toEpochDay()
            val task = fixture.repository.seedInbox("Water plants").single()
            val viewModel = fixture.viewModel()
            val collect = launch { viewModel.uiState.collect {} }
            try {
                viewModel.openRepeatCreate("Water plants", convertsTaskId = task.id)
                assertEquals(task.id, fixture.repeats.observeEditing().first()?.convertsTaskId)
                viewModel.commitRepeatDraft(
                    RepeatDraft(
                        title = "Water plants",
                        band = RepeatBand.MORNING,
                        everyNDays = 1,
                        anchorEpochDay = day,
                        reminderOn = true,
                        convertsTaskId = task.id,
                    ),
                )
                shadowOf(Looper.getMainLooper()).idle()
                withTimeout(5_000) {
                    viewModel.uiState.first { it.undo?.target is UndoTarget.ConvertToRepeat }
                }
                val snapshot = fixture.useCases.observeSnapshot().first()
                assertTrue(snapshot.tasks.none { it.id == task.id })
                assertTrue(snapshot.tasks.none { it.title == "Water plants" })
                assertEquals(
                    1,
                    fixture.repeats.observeRepeats().first().count { it.title == "Water plants" },
                )
                val undo = viewModel.uiState.value.undo
                check(undo != null)
                viewModel.undo(undo.id)
                shadowOf(Looper.getMainLooper()).idle()
                withTimeout(5_000) {
                    fixture.useCases.observeSnapshot().first { snap ->
                        snap.tasks.any { it.id == task.id }
                    }
                }
                val restored = fixture.useCases.observeSnapshot().first().tasks.single { it.id == task.id }
                assertEquals(TaskBucket.INBOX, restored.bucket)
                assertEquals(task.sortOrder, restored.sortOrder)
                assertTrue(fixture.repeats.observeRepeats().first().none { it.title == "Water plants" })
            } finally {
                collect.cancel()
            }
        }
    }

    @Test
    fun rejectedConversionKeepsTheTaskAndShowsNoUndo() = runBlocking {
        useTestFocus { fixture ->
            val day = fixture.clock.localDate().toEpochDay()
            val today = fixture.seedFullToday().first()
            fixture.useCases.start(today.id)
            val viewModel = fixture.viewModel()
            val collect = launch { viewModel.uiState.collect {} }
            try {
                viewModel.openRepeatCreate(today.title, convertsTaskId = today.id)
                viewModel.commitRepeatDraft(
                    RepeatDraft(
                        title = today.title,
                        band = RepeatBand.MORNING,
                        everyNDays = 1,
                        anchorEpochDay = day,
                        reminderOn = true,
                        convertsTaskId = today.id,
                    ),
                )
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(viewModel.uiState.value.undo?.target !is UndoTarget.ConvertToRepeat)
                assertTrue(fixture.useCases.observeSnapshot().first().tasks.any { it.id == today.id })
            } finally {
                collect.cancel()
            }
        }
    }
}
