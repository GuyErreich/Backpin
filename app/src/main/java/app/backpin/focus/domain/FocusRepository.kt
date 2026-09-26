package app.backpin.focus.domain

import kotlinx.coroutines.flow.Flow

/**
 * Transactional boundary for tasks, focus frames, and stored preferences.
 * UI state is derived from [observeSnapshot]; callers do not keep their own stack.
 */
interface FocusRepository {
    fun observeSnapshot(): Flow<FocusSnapshot>

    val preferences: Flow<UserPreferences>

    /** Adds an Inbox task. A blank title changes nothing and does not touch the active frame. */
    suspend fun capture(title: String)

    /**
     * Fills an empty Today slot. When three open Today tasks already exist, returns
     * [PromoteOutcome.NeedsSwap] and writes nothing.
     */
    suspend fun promote(taskId: String): PromoteOutcome

    /**
     * Demotes [outgoingTaskId] to Later and promotes [incomingTaskId] into Today
     * in one transaction. A rejected swap rolls both writes back.
     */
    suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    )

    /**
     * Pauses the active frame, if any, and activates [taskId].
     * This is a switch, not an interruption: the new frame has no parent.
     * Does not complete the paused task. Refuses a frame that is waiting for an explicit resume.
     */
    suspend fun start(taskId: String)

    /**
     * Pauses the active frame and pushes a child frame for a new Inbox task.
     * A blank title is stored as [UNTITLED_INTERRUPTION].
     */
    suspend fun interrupt(title: String?): Task

    /**
     * Marks the active task done, closes its frame, and sets the parent to
     * [FrameState.AWAITING_RESUME] when one exists. Returns null when nothing is active.
     */
    suspend fun completeActive(): UndoToken?

    /** Activates a frame that is [FrameState.AWAITING_RESUME]. Fails if another frame is active. */
    suspend fun resume(frameId: String)

    /**
     * Moves a task to Later. An active frame is paused and, when it has a parent,
     * that parent becomes [FrameState.AWAITING_RESUME]. An awaiting frame is parked
     * as paused without revealing another layer.
     */
    suspend fun demote(taskId: String): UndoToken

    /** Parks the active frame without pushing a child or opening recovery. */
    suspend fun pauseActive()

    suspend fun updateReturnCue(
        taskId: String,
        cue: String?,
    )

    suspend fun rename(
        taskId: String,
        title: String,
    )

    /** Puts the snapshotted rows back. Used by the Undo snackbar for Done and Later. */
    suspend fun undo(token: UndoToken)
}
