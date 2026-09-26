package app.backpin.focus.ui

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeriveFocusUiTest {
    @Test
    fun emptyTodayIsThreeEmptySlots() {
        val state = derive(FocusSnapshot())
        assertEquals(3, state.todaySlots.size)
        assertTrue(state.todaySlots.all { it.role == SlotRole.EMPTY && it.task == null })
        assertNull(state.now)
        assertNull(state.recovery)
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
        assertNull(state.recovery)
        assertEquals(SlotRole.OPEN, state.todaySlots.single { it.task != null }.role)
        assertEquals(2, state.todaySlots.count { it.role == SlotRole.EMPTY })
    }

    private fun derive(snapshot: FocusSnapshot): FocusUiState =
        deriveFocusUi(
            snapshot,
            ChromeState(
                captureOpen = false,
                inboxExpanded = false,
                laterExpanded = false,
                swapIncomingId = null,
                switchOpen = false,
                undo = null,
                preferences = UserPreferences(),
            ),
        )

    private fun task(
        id: String,
        title: String,
        bucket: TaskBucket,
        cue: String? = null,
        status: TaskStatus = TaskStatus.OPEN,
    ) = Task(
        id = id,
        title = title,
        bucket = bucket,
        status = status,
        returnCue = cue,
        sortOrder = 1,
        createdAtEpochMillis = 1,
        completedAtEpochMillis = null,
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
