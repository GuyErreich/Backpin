// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeriveFocusUiTest {
    @Test
    fun emptyTodayIsThreeEmptySlots() {
        val state = derive(FocusSnapshot())
        assertEquals(3, state.todaySlots.size)
        assertTrue(state.todaySlots.all { it.role == SlotRole.EMPTY && it.task == null })
        assertNull(state.now)
        assertNull(state.recovery)
        assertFalse(state.canInterrupt)
    }

    @Test
    fun recoveryShowsOnlyTheWaitingParent() {
        val original = task("original", "Review CI failure", TaskBucket.TODAY, cue = "Next: reproduce CI failure")
        val john = task("john", "Message John", TaskBucket.INBOX)
        val sarah = task("sarah", "Talk to Sarah", TaskBucket.INBOX, status = TaskStatus.DONE)
        val snapshot =
            FocusSnapshot(
                tasks = listOf(original, john, sarah),
                frames =
                    listOf(
                        frame("f-original", "original", parent = null, FrameState.PAUSED),
                        frame("f-john", "john", parent = "f-original", FrameState.AWAITING_RESUME),
                        frame("f-sarah", "sarah", parent = "f-john", FrameState.CLOSED),
                    ),
            )
        val state = derive(snapshot)
        assertNull(state.now)
        assertEquals("Message John", state.recovery?.title)
        assertEquals("f-john", state.recovery?.frameId)
        assertEquals(3, state.todaySlots.size)
        assertEquals(SlotRole.OPEN, state.todaySlots[0].role)
        assertEquals(listOf("Talk to Sarah"), state.archiveTasks.map { it.title })
        assertEquals(1, state.archiveCount)
    }

    @Test
    fun finishedTaskLeavesFocusWhenNothingIsWaiting() {
        val done = task("ship", "Ship the release notes", TaskBucket.TODAY, status = TaskStatus.DONE)
        val snapshot =
            FocusSnapshot(
                tasks = listOf(done),
                frames = listOf(frame("f-ship", "ship", parent = null, FrameState.CLOSED)),
            )
        val state = derive(snapshot)
        assertNull(state.now)
        assertNull(state.recovery)
        assertNull(state.paused)
        assertEquals(listOf("Ship the release notes"), state.archiveTasks.map { it.title })
        assertTrue(state.todaySlots.all { it.task == null })
    }

    @Test
    fun doneTasksStayInArchiveApartFromOpenBuckets() {
        val today = task("today", "Write the notes", TaskBucket.TODAY)
        val later = task("later", "Call the vendor", TaskBucket.LATER)
        val inbox = task("inbox", "Buy milk", TaskBucket.INBOX)
        val older = task("older", "Ship the release notes", TaskBucket.TODAY, status = TaskStatus.DONE, completedAt = 10)
        val newer = task("newer", "File the receipt", TaskBucket.INBOX, status = TaskStatus.DONE, completedAt = 40)
        val state =
            derive(
                FocusSnapshot(tasks = listOf(today, later, inbox, older, newer)),
            )
        assertEquals(listOf("File the receipt", "Ship the release notes"), state.archiveTasks.map { it.title })
        assertEquals(2, state.archiveCount)
        assertEquals(listOf("Write the notes"), state.todaySlots.mapNotNull { it.task?.title })
        assertEquals(listOf("Call the vendor"), state.laterTasks.map { it.title })
        assertEquals(listOf("Buy milk"), state.inboxTasks.map { it.title })
    }

    @Test
    fun activeFrameHidesTheRestOfTheStack() {
        val original = task("original", "Review CI failure", TaskBucket.TODAY)
        val john = task("john", "Message John", TaskBucket.INBOX)
        val snapshot =
            FocusSnapshot(
                tasks = listOf(original, john),
                frames =
                    listOf(
                        frame("f-original", "original", parent = null, FrameState.PAUSED),
                        frame("f-john", "john", parent = "f-original", FrameState.ACTIVE, accumulated = 90_000),
                    ),
            )
        val state = derive(snapshot)
        assertEquals("Message John", state.now?.title)
        assertTrue(state.now?.isInterruption == true)
        assertTrue(state.canInterrupt)
        assertNull(state.recovery)
        assertEquals(SlotRole.OPEN, state.todaySlots.single { it.task != null }.role)
        assertEquals(2, state.todaySlots.count { it.role == SlotRole.EMPTY })
    }

    @Test
    fun interruptIsAbsentWhenNothingIsActiveOrOnlyWaiting() {
        assertFalse(derive(FocusSnapshot()).canInterrupt)
        val waiting =
            FocusSnapshot(
                tasks = listOf(task("original", "Review CI failure", TaskBucket.TODAY)),
                frames = listOf(frame("f-original", "original", parent = null, FrameState.AWAITING_RESUME)),
            )
        val recovery = derive(waiting)
        assertNull(recovery.now)
        assertEquals("Review CI failure", recovery.recovery?.title)
        assertFalse(recovery.canInterrupt)
        val paused =
            FocusSnapshot(
                tasks = listOf(task("original", "Review CI failure", TaskBucket.TODAY)),
                frames = listOf(frame("f-original", "original", parent = null, FrameState.PAUSED)),
            )
        assertFalse(derive(paused).canInterrupt)
    }

    @Test
    fun anArrivedEventCarriesItsStoredInterval() {
        val event =
            task(
                "event",
                "Dentist",
                TaskBucket.COUNTDOWN,
                scheduledOnEpochDay = LocalDate.of(2026, 9, 27).toEpochDay(),
            ).copy(countdownIntervalDays = 30)
        val state =
            derive(
                FocusSnapshot(
                    tasks = listOf(event),
                    frames = listOf(frame("f-event", "event", parent = null, FrameState.ACTIVE)),
                ),
            )
        assertEquals("Dentist", state.now?.title)
        assertTrue(state.now?.isEvent == true)
        assertEquals(30, state.now?.countdownIntervalDays)
        assertTrue(state.countdowns.isEmpty())
    }

    @Test
    fun aNestedInterruptionLeavesThePiles() {
        val parent = task("parent", "Review CI failure", TaskBucket.TODAY)
        val child = task("child", "Call Dana", TaskBucket.INTERRUPTION)
        val state =
            derive(
                FocusSnapshot(
                    tasks = listOf(parent, child),
                    frames =
                        listOf(
                            frame("f-parent", "parent", parent = null, FrameState.PAUSED),
                            frame("f-child", "child", parent = "f-parent", FrameState.ACTIVE),
                        ),
                ),
            )
        assertEquals("Call Dana", state.now?.title)
        assertTrue(state.inboxTasks.isEmpty())
        assertTrue(state.laterTasks.isEmpty())
        assertTrue(state.scheduledTasks.isEmpty())
        assertEquals(listOf("Review CI failure"), state.todaySlots.mapNotNull { it.task?.title })
    }

    @Test
    fun todaySlotCountFollowsTheStoredLimit() {
        val one = derive(FocusSnapshot(), preferences = UserPreferences(todayLimit = 1))
        assertEquals(1, one.todaySlots.size)
        assertTrue(one.todaySlots.all { it.role == SlotRole.EMPTY })
        val five = derive(FocusSnapshot(), preferences = UserPreferences(todayLimit = 5))
        assertEquals(5, five.todaySlots.size)
        val notes = task("notes", "Write the notes", TaskBucket.TODAY)
        val capped =
            derive(
                FocusSnapshot(tasks = listOf(notes)),
                preferences = UserPreferences(todayLimit = 1),
            )
        assertEquals(listOf("Write the notes"), capped.todaySlots.mapNotNull { it.task?.title })
        assertTrue(capped.todaySlots.none { it.role == SlotRole.EMPTY })
    }

    @Test
    fun scheduledTasksStayOffInboxAndShowWhenTheyAreDue() {
        val dueDay = LocalDate.of(2026, 9, 27)
        val laterDay = dueDay.plusDays(2)
        val due = task("due", "Call Dana", TaskBucket.SCHEDULED, scheduledOnEpochDay = dueDay.toEpochDay())
        val later = task("later", "File the form", TaskBucket.SCHEDULED, scheduledOnEpochDay = laterDay.toEpochDay())
        val inbox = task("inbox", "Buy milk", TaskBucket.INBOX)
        val state =
            derive(
                FocusSnapshot(tasks = listOf(due, later, inbox)),
                localDate = dueDay,
            )
        assertEquals(2, state.scheduledCount)
        assertEquals(listOf("Call Dana", "File the form"), state.scheduledTasks.map { it.title })
        assertTrue(state.scheduledTasks[0].due)
        assertFalse(state.scheduledTasks[1].due)
        assertEquals(listOf("Buy milk"), state.inboxTasks.map { it.title })
        assertTrue(state.todaySlots.all { it.task == null })
    }

    @Test
    fun aFreshCaptureOnAFullTodayStillListsReplaceChoices() {
        val notes = task("notes", "Write the notes", TaskBucket.TODAY)
        val incoming = task("incoming", "Call the school", TaskBucket.INBOX)
        val state =
            deriveFocusUi(
                FocusSnapshot(tasks = listOf(notes, incoming)),
                ChromeState(
                    captureOpen = false,
                    swapIncomingId = incoming.id,
                    switchOpen = false,
                    undo = null,
                    preferences = UserPreferences(todayLimit = 1),
                ),
            )
        assertEquals("Call the school", state.swapIncoming?.title)
        assertEquals(listOf("Write the notes"), state.swapChoices.map { it.title })
    }

    @Test
    fun editingNamesOnlyAnOpenTask() {
        val open = task("open", "Buy milk", TaskBucket.INBOX)
        val done = task("done", "Old work", TaskBucket.INBOX, status = TaskStatus.DONE)
        val shown =
            deriveFocusUi(
                FocusSnapshot(tasks = listOf(open, done)),
                ChromeState(
                    captureOpen = false,
                    swapIncomingId = null,
                    switchOpen = false,
                    undo = null,
                    preferences = UserPreferences(),
                    editingTaskId = "open",
                ),
            )
        assertEquals("Buy milk", shown.editing?.title)
        val hidden =
            deriveFocusUi(
                FocusSnapshot(tasks = listOf(open, done)),
                ChromeState(
                    captureOpen = false,
                    swapIncomingId = null,
                    switchOpen = false,
                    undo = null,
                    preferences = UserPreferences(),
                    editingTaskId = "done",
                ),
            )
        assertNull(hidden.editing)
    }

    @Test
    fun captureReasonAndPhrasesStayOnTheSheetState() {
        val state =
            deriveFocusUi(
                FocusSnapshot(),
                ChromeState(
                    captureOpen = true,
                    captureInterrupts = true,
                    swapIncomingId = null,
                    switchOpen = false,
                    undo = null,
                    preferences =
                        UserPreferences(
                            savedPhrases = listOf("Talk to my wife", "Talk to <name>"),
                        ),
                ),
            )
        assertTrue(state.captureOpen)
        assertTrue(state.captureInterrupts)
        assertEquals(listOf("Talk to my wife", "Talk to <name>"), state.preferences.savedPhrases)
    }

    private fun derive(
        snapshot: FocusSnapshot,
        undo: PendingUndo? = null,
        preferences: UserPreferences = UserPreferences(),
        localDate: LocalDate = LocalDate.of(2026, 9, 27),
    ): FocusUiState =
        deriveFocusUi(
            snapshot,
            ChromeState(
                captureOpen = false,
                swapIncomingId = null,
                switchOpen = false,
                undo = undo,
                preferences = preferences,
            ),
            localDate = localDate,
        )

    private fun task(
        id: String,
        title: String,
        bucket: TaskBucket,
        cue: String? = null,
        status: TaskStatus = TaskStatus.OPEN,
        completedAt: Long? = null,
        scheduledOnEpochDay: Long? = null,
    ) = Task(
        id = id,
        title = title,
        bucket = bucket,
        status = status,
        returnCue = cue,
        sortOrder = 1,
        createdAtEpochMillis = 1,
        completedAtEpochMillis = completedAt,
        scheduledOnEpochDay = scheduledOnEpochDay,
    )

    private fun frame(
        id: String,
        taskId: String,
        parent: String?,
        state: FrameState,
        accumulated: Long = 0,
    ) = FocusFrame(
        id = id,
        taskId = taskId,
        parentFrameId = parent,
        state = state,
        startedAtEpochMillis = 10,
        pausedAtEpochMillis = null,
        accumulatedMillis = accumulated,
    )
}
