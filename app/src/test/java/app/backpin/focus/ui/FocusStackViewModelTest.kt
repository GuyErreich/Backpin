// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import android.content.Context
import android.os.Looper
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
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.FocusUseCases
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.repeats.RepeatsService
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusStackViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: FocusStackDatabase
    private lateinit var useCases: FocusUseCases

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database =
            Room
                .inMemoryDatabaseBuilder(context, FocusStackDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        useCases =
            FocusUseCases(
                FocusRepositoryImpl(
                    database = database,
                    preferencesStore = PreferencesDataStore(context, "vm-${UUID.randomUUID()}"),
                    clock = object : AppClock {
                        override fun nowEpochMillis(): Long = 5_000L
                    },
                ),
            )
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        try {
            database.close()
        } finally {
            // FullTodayStartTest's MainDispatcherRule may already have cleared Main
            // when flake-check reuses the JVM across classes.
            runCatching { Dispatchers.resetMain() }
        }
    }

    @Test
    fun openCaptureRemembersInterruptVersusInbox() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        viewModel.openCapture(interrupts = false)
        withTimeout(5_000) {
            viewModel.uiState.first { it.captureOpen && !it.captureInterrupts }
        }
        viewModel.openCapture(interrupts = true)
        withTimeout(5_000) {
            viewModel.uiState.first { it.captureOpen && it.captureInterrupts }
        }
        viewModel.dismissCapture()
        withTimeout(5_000) {
            viewModel.uiState.first { !it.captureOpen && !it.captureInterrupts }
        }
        collect.cancel()
    }

    @Test
    fun captureFromTheViewModelLeavesTheActiveFrame() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        val original = seedToday("Review CI failure")
        useCases.start(original)
        val before = useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id
        viewModel.saveToInbox("Buy milk")
        withTimeout(5_000) {
            viewModel.uiState.first { state -> state.inboxCount == 1 && state.now?.taskId == original }
        }
        val after = useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id
        assertEquals(before, after)
        viewModel.saveToInbox("   ")
        assertEquals(1, useCases.observeSnapshot().first().tasks.count { it.bucket == TaskBucket.INBOX })
        viewModel.dismissCapture()
        assertEquals(1, useCases.observeSnapshot().first().tasks.count { it.bucket == TaskBucket.INBOX })
        collect.cancel()
    }

    @Test
    fun doubleInterruptAndNewViewModelShowTheParentCue() = runBlocking {
        val first = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { first.uiState.collect {} }
        val original = seedToday("Review CI failure")
        useCases.updateReturnCue(original, "Next: reproduce CI failure")
        useCases.start(original)
        withTimeout(5_000) { first.uiState.first { it.now?.taskId == original } }
        first.interruptNow("Message John")
        withTimeout(5_000) { first.uiState.first { it.now?.title == "Message John" } }
        first.interruptNow("Ping")
        val ping =
            withTimeout(5_000) {
                first.uiState.first { it.now?.title == "Ping" }
            }
        first.rename(ping.now!!.taskId, "Talk to Sarah")
        withTimeout(5_000) { first.uiState.first { it.now?.title == "Talk to Sarah" } }
        first.completeActive()
        val recovered =
            withTimeout(5_000) {
                first.uiState.first {
                    it.recovery?.title == "Message John" &&
                        it.undo == null &&
                        it.archiveTasks.any { task -> task.title == "Talk to Sarah" }
                }
            }
        assertNull(recovered.now)
        assertNull(recovered.undo)
        collect.cancel()

        val recreated = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collectAgain = launch { recreated.uiState.collect {} }
        val restored =
            withTimeout(5_000) {
                recreated.uiState.first { it.recovery?.title == "Message John" }
            }
        assertEquals("Message John", restored.recovery?.title)
        recreated.resume(restored.recovery!!.frameId)
        withTimeout(5_000) { recreated.uiState.first { it.now?.title == "Message John" } }
        recreated.completeActive()
        val originalRecovery =
            withTimeout(5_000) {
                recreated.uiState.first { it.recovery?.title == "Review CI failure" }
            }
        assertEquals("Next: reproduce CI failure", originalRecovery.recovery?.returnCue)
        val frameId = originalRecovery.recovery!!.frameId
        recreated.resume(frameId)
        val resumed =
            withTimeout(5_000) {
                recreated.uiState.first { it.now?.title == "Review CI failure" }
            }
        assertEquals("Next: reproduce CI failure", resumed.now?.returnCue)
        assertTrue(resumed.now?.isInterruption == false)
        collectAgain.cancel()
    }

    @Test
    fun doneLandsInArchiveAndRestoreReturnsOpenInboxWithoutChangingTheActiveFrame() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        val original = seedToday("Review CI failure")
        useCases.start(original)
        val activeBefore = useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id
        viewModel.saveToInbox("Buy milk")
        val captured =
            withTimeout(5_000) {
                viewModel.uiState.first { state -> state.inboxTasks.any { it.title == "Buy milk" } && state.now?.taskId == original }
            }
        val milkId = captured.inboxTasks.single { it.title == "Buy milk" }.id

        viewModel.markDone(milkId)
        val archived =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state.undo == null &&
                        state.archiveTasks.any { it.id == milkId } &&
                        state.inboxTasks.none { it.id == milkId } &&
                        state.now?.taskId == original
                }
            }
        assertEquals(listOf(milkId), archived.archiveTasks.map { it.id })
        assertNull(archived.undo)
        assertEquals(activeBefore, useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id)

        viewModel.restore(milkId)
        val restored =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state.undo == null &&
                        state.archiveTasks.none { it.id == milkId } &&
                        state.inboxTasks.any { it.id == milkId } &&
                        state.now?.taskId == original &&
                        state.todaySlots.none { it.task?.id == milkId }
                }
            }
        val snapshot = useCases.observeSnapshot().first()
        val milk = snapshot.tasks.single { it.id == milkId }
        assertEquals(TaskStatus.OPEN, milk.status)
        assertEquals(TaskBucket.INBOX, milk.bucket)
        assertNull(milk.completedAtEpochMillis)
        assertEquals(activeBefore, snapshot.frames.single { it.state == FrameState.ACTIVE }.id)
        assertEquals(original, snapshot.frames.single { it.state == FrameState.ACTIVE }.taskId)
        assertTrue(snapshot.frames.none { it.taskId == milkId })
        assertEquals(SlotRole.NOW, restored.todaySlots.single { it.task?.id == original }.role)

        viewModel.demote(milkId)
        val later =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state.undo == null &&
                        state.laterTasks.any { it.id == milkId } &&
                        state.now?.taskId == original
                }
            }
        assertNull(later.undo)
        assertEquals(activeBefore, useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.id)

        viewModel.completeActive()
        val finished =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state.undo == null && state.now == null && state.archiveTasks.any { it.id == original }
                }
            }
        assertNull(finished.undo)
        assertNull(finished.now)
        collect.cancel()
    }

    @Test
    fun placingOnAFullTodayKeepsTheCaptureInInbox() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        repeat(3) { index -> seedToday("Today $index") }
        withTimeout(5_000) {
            viewModel.uiState.first { state -> state.todaySlots.count { it.task != null } == 3 }
        }
        viewModel.placeOnToday("Call the school")
        val waiting =
            withTimeout(5_000) {
                viewModel.uiState.first { it.swapIncoming?.title == "Call the school" && it.swapChoices.size == 3 }
            }
        val captured = useCases.observeSnapshot().first().tasks.single { it.title == "Call the school" }
        assertEquals(TaskBucket.INBOX, captured.bucket)
        assertEquals(3, waiting.swapChoices.size)
        assertEquals(captured.id, waiting.swapIncoming?.id)
        viewModel.cancelSwap()
        withTimeout(5_000) {
            viewModel.uiState.first { it.swapIncoming == null }
        }
        assertEquals(TaskBucket.INBOX, useCases.observeSnapshot().first().tasks.single { it.title == "Call the school" }.bucket)
        collect.cancel()
    }

    @Test
    fun captureActionsPlaceTheTask() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        viewModel.placeOnToday("Write the notes")
        val today =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    state.todaySlots.any { it.task?.title == "Write the notes" }
                }
            }
        val placed = useCases.observeSnapshot().first()
        assertEquals(TaskBucket.TODAY, placed.tasks.single { it.title == "Write the notes" }.bucket)
        assertTrue(placed.frames.isEmpty())
        assertNull(today.now)

        val current = placed.tasks.single { it.title == "Write the notes" }.id
        useCases.start(current)
        withTimeout(5_000) {
            viewModel.uiState.first { it.now?.taskId == current }
        }
        viewModel.placeAndStart("Call Dana")
        withTimeout(5_000) {
            viewModel.uiState.first { it.now?.title == "Call Dana" }
        }
        val afterStart = useCases.observeSnapshot().first()
        val started = afterStart.frames.single { it.state == FrameState.ACTIVE }
        assertEquals(TaskBucket.TODAY, afterStart.tasks.single { it.title == "Call Dana" }.bucket)
        assertNull(started.parentFrameId)
        assertEquals(FrameState.PAUSED, afterStart.frames.single { it.taskId == current }.state)

        viewModel.interruptNow("Ping the vendor")
        val nested =
            withTimeout(5_000) {
                viewModel.uiState.first { state ->
                    val now = state.now
                    now != null && now.title == "Ping the vendor" && now.isInterruption
                }
            }
        val afterInterrupt = useCases.observeSnapshot().first()
        val child = afterInterrupt.frames.single { it.state == FrameState.ACTIVE }
        val parentTaskId = afterInterrupt.tasks.single { it.title == "Call Dana" }.id
        val parent = afterInterrupt.frames.single { it.taskId == parentTaskId }
        assertEquals(parent.id, child.parentFrameId)
        assertEquals(FrameState.PAUSED, parent.state)
        assertEquals(TaskBucket.INBOX, afterInterrupt.tasks.single { it.title == "Ping the vendor" }.bucket)
        assertEquals(true, nested.now?.isInterruption)

        viewModel.completeActive()
        val returned =
            withTimeout(5_000) {
                viewModel.uiState.first { it.now == null && it.recovery?.title == "Call Dana" }
            }
        assertEquals("Call Dana", returned.recovery?.title)
        val afterReturn = useCases.observeSnapshot().first()
        assertEquals(FrameState.AWAITING_RESUME, afterReturn.frames.single { it.taskId == parent.taskId }.state)
        assertEquals(TaskStatus.DONE, afterReturn.tasks.single { it.title == "Ping the vendor" }.status)

        collect.cancel()
    }

    @Test
    fun emptyCaptureCreatesNothingAndInterruptNeedsAnActiveFrame() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        viewModel.openCapture()
        withTimeout(5_000) { viewModel.uiState.first { it.captureOpen } }
        viewModel.saveToInbox(" ")
        viewModel.placeOnToday("")
        viewModel.placeAndStart("  ")
        viewModel.interruptNow(" ")
        viewModel.interruptNow("Call Dana")
        shadowOf(Looper.getMainLooper()).idle()
        val idle = useCases.observeSnapshot().first()
        assertTrue(idle.tasks.isEmpty())
        assertTrue(idle.frames.isEmpty())
        assertTrue(viewModel.uiState.value.captureOpen)

        val original = seedToday("Review CI failure")
        useCases.start(original)
        viewModel.placeAndStart("  ")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original, useCases.observeSnapshot().first().frames.single { it.state == FrameState.ACTIVE }.taskId)
        collect.cancel()
    }

    @Test
    fun editSheetSavesATitleAndABlankKeepsTheOldOne() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        val original = seedToday("Review CI failure")
        useCases.start(original)
        withTimeout(5_000) { viewModel.uiState.first { it.now?.taskId == original } }
        viewModel.openCapture()
        withTimeout(5_000) { viewModel.uiState.first { it.captureOpen } }
        viewModel.openEdit(original)
        val opened =
            withTimeout(5_000) {
                viewModel.uiState.first { it.editing?.id == original && !it.captureOpen }
            }
        assertEquals("Review CI failure", opened.editing?.title)
        viewModel.saveEdit("   ")
        val kept =
            withTimeout(5_000) {
                viewModel.uiState.first { it.now?.title == "Review CI failure" && it.editing == null }
            }
        assertEquals("Review CI failure", kept.now?.title)
        viewModel.openEdit(original)
        withTimeout(5_000) { viewModel.uiState.first { it.editing?.id == original } }
        viewModel.saveEdit("Talk to Sarah")
        val saved =
            withTimeout(5_000) {
                viewModel.uiState.first { it.now?.title == "Talk to Sarah" && it.editing == null }
            }
        assertEquals("Talk to Sarah", saved.now?.title)
        assertTrue(saved.todaySlots.any { it.task?.title == "Talk to Sarah" })
        viewModel.openEdit(original)
        withTimeout(5_000) { viewModel.uiState.first { it.editing?.id == original } }
        viewModel.openCapture()
        withTimeout(5_000) {
            viewModel.uiState.first { it.captureOpen && it.editing == null }
        }
        collect.cancel()
    }

    @Test
    fun editSheetCanMoveATaskToLater() = runBlocking {
        val viewModel = FocusStackViewModel(useCases, clock = AppClock { 5_000L })
        val collect = launch { viewModel.uiState.collect {} }
        useCases.capture("Park me")
        val id = useCases.observeSnapshot().first().tasks.single { it.title == "Park me" }.id
        viewModel.openEdit(id)
        withTimeout(5_000) { viewModel.uiState.first { it.editing?.id == id } }
        viewModel.saveEdit("Park me later", CapturePlace.LATER)
        val moved =
            withTimeout(5_000) {
                viewModel.uiState.first { it.editing == null && it.laterTasks.any { task -> task.id == id } }
            }
        assertEquals("Park me later", moved.laterTasks.single { it.id == id }.title)
        collect.cancel()
    }

    @Test
    fun checkOffUndoRestoresTheRepeat() = runBlocking {
        val today = LocalDate.of(2026, 10, 16)
        val morning =
            today.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val repository =
            FocusRepositoryImpl(
                database = database,
                preferencesStore = PreferencesDataStore(context, "vm-repeat-${UUID.randomUUID()}"),
                clock = AppClock { morning },
            )
        val repeats = RepeatsService(repository, clock = AppClock { morning })
        val useCases = FocusUseCases(repository)
        val store = ViewModelStore()
        val viewModel =
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FocusStackViewModel(useCases, repeats, AppClock { morning }) as T
                    }
                },
            )[FocusStackViewModel::class.java]
        val collect = launch { viewModel.uiState.collect {} }
        val rows = launch { viewModel.repeatRows.collect {} }
        try {
            val id = repeats.create("Take meds", RepeatBand.MORNING, 1, today.toEpochDay())!!
            withTimeout(5_000) {
                viewModel.repeatRows.first { state ->
                    state.rows.any { it.repeat.id == id && it.status == RepeatDayStatus.OPEN }
                }
            }
            viewModel.markRepeatDone(id, today.toEpochDay())
            val undo =
                withTimeout(5_000) {
                    viewModel.uiState.first { it.undo?.target is UndoTarget.RepeatCheckOff }.undo!!
                }
            assertEquals("", undo.message)
            val checkedOff = context.getString(R.string.undo_checked_off)
            assertEquals(checkedOff, undoSnackbarMessage(undo, checkedOff))
            assertTrue(undoSnackbarMessage(undo, checkedOff).isNotBlank())
            withTimeout(5_000) {
                viewModel.repeatRows.first { state ->
                    state.rows.single { it.repeat.id == id }.status == RepeatDayStatus.DONE
                }
            }
            viewModel.undo(undo.id)
            val restored =
                withTimeout(5_000) {
                    viewModel.repeatRows.first { state ->
                        state.rows.single { it.repeat.id == id }.status == RepeatDayStatus.OPEN
                    }
                }
            assertEquals(RepeatDayStatus.OPEN, restored.rows.single { it.repeat.id == id }.status)
            withTimeout(5_000) {
                viewModel.uiState.first { it.undo == null }
            }
            assertNull(viewModel.uiState.value.undo)
        } finally {
            collect.cancel()
            rows.cancel()
            store.clear()
        }
    }

    private suspend fun seedToday(title: String): String {
        useCases.capture(title)
        val id = useCases.observeSnapshot().first().tasks.single { it.title == title }.id
        useCases.promote(id)
        return id
    }
}
