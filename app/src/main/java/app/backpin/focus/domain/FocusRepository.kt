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
     * Places a new task on Today without starting it.
     * A blank title changes nothing.
     * When Today is full, returns [PromoteOutcome.NeedsSwap] and writes nothing.
     */
    suspend fun captureIntoToday(title: String): PromoteOutcome

    /**
     * Starts a new task now. Nothing active means it just starts.
     * An active frame is paused and this one starts with no parent: a switch, not a nested interruption.
     * When Today has no free slot, returns [PromoteOutcome.NeedsSwap] and writes nothing.
     */
    suspend fun captureAndStart(title: String): PromoteOutcome

    /** Parks a new task in Later. Does not start it. A blank title changes nothing. */
    suspend fun captureLater(title: String)

    /**
     * Stores a new task outside Inbox, Today, and Later until [scheduledOnEpochDay].
     * Does not start it. A blank title changes nothing.
     */
    suspend fun captureScheduled(
        title: String,
        scheduledOnEpochDay: Long,
    )

    /**
     * Creates a task in the slot freed by parking [outgoingTaskId] in Later.
     * Does not start the new task. Used when a new capture finds Today full and Replace is chosen.
     */
    suspend fun captureReplacing(
        title: String,
        outgoingTaskId: String,
    ): UndoToken

    /**
     * Pauses the active Today task, parks it in Later, and starts a new nested task in that slot.
     * Used when a new capture finds Today full and Interrupt is chosen.
     */
    suspend fun interruptWithNew(title: String)

    /** Moves a scheduled task to Inbox without starting it. Other buckets are left alone. */
    suspend fun moveToInbox(taskId: String)

    /** Drops a scheduled task. Other buckets are left alone. */
    suspend fun removeScheduled(taskId: String)

    /**
     * Moves due scheduled tasks onto Today without starting them, while a slot is free.
     * When Today is full, leaves them scheduled. Does not park another Today task to make room.
     */
    suspend fun releaseDueScheduled()

    /**
     * Fills an empty Today slot. When open Today tasks already fill the stored cap,
     * returns [PromoteOutcome.NeedsSwap] and writes nothing. Does not start the task.
     */
    suspend fun promote(taskId: String): PromoteOutcome

    /**
     * Demotes [outgoingTaskId] to Later and places [incomingTaskId] in that Today slot
     * in one transaction. Does not start the incoming task. A rejected swap rolls both writes back.
     */
    suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ): UndoToken

    /**
     * Pauses the active frame, if any, and activates [taskId].
     * This is a switch, not an interruption: the new frame has no parent.
     * A task that is not already in Today takes a free slot in the same transaction.
     * When that slot is missing because Today is full, returns [PromoteOutcome.NeedsSwap]
     * and writes nothing. Refuses a frame that is waiting for an explicit resume.
     */
    suspend fun start(taskId: String): PromoteOutcome

    /**
     * Pauses the active Today frame and makes [taskId] the active child.
     * Reuses [taskId]; does not create another task.
     * When [taskId] is already in Today, both tasks stay in Today. A paused frame
     * for [taskId] is reactivated as the child. A frame waiting for an explicit
     * resume is not hijacked.
     * When [taskId] is not in Today, the previous Now moves to Later and stays the
     * parent frame, so Today stays within the cap and [taskId] is in Today.
     */
    suspend fun interruptWith(taskId: String)

    /**
     * Pauses the active frame and pushes a child frame for a new Inbox task.
     * A blank title is stored as [UNTITLED_INTERRUPTION].
     */
    suspend fun interrupt(title: String?): Task

    /**
     * Marks the active task done, closes its frame, and sets the parent to
     * [FrameState.AWAITING_RESUME] when one exists. The task stays in history.
     * Returns null when nothing is active.
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

    /**
     * Puts the snapshotted rows back. Used by the Undo snackbar for Today placement.
     * Done and Later do not offer that snackbar.
     */
    suspend fun undo(token: UndoToken)

    /**
     * Puts a done task back into action as an open Inbox task.
     * Does not start it, move it to Today, or create a focus frame.
     */
    suspend fun restore(taskId: String)

    /**
     * Marks [taskId] done. The active task follows [completeActive].
     * A task that still has an open child frame is left unchanged.
     */
    suspend fun completeTask(taskId: String): UndoToken?

    /**
     * Rewrites Today sort order. [orderedIds] must be exactly the open Today tasks.
     * Returns the previous rows so Undo can restore them. Returns null when the order
     * did not change.
     */
    suspend fun reorderToday(orderedIds: List<String>): UndoToken?

    /** Saves the nudge interval. [enabled] false is Off. Minutes are 30, 60, or 120. */
    suspend fun setNudge(
        enabled: Boolean,
        minutes: Int,
    )

    /** Quiet hours as `HH:mm`, or both null when quiet hours are cleared. */
    suspend fun setQuietHours(
        start: String?,
        end: String?,
    )

    suspend fun setCurrentNotificationEnabled(enabled: Boolean)

    /**
     * Stores the Today cap, from [MIN_TODAY_LIMIT] through [MAX_TODAY_LIMIT].
     * A smaller cap parks extra open Today tasks in Later. It does not pull tasks in to fill gaps.
     * The active Today task stays when it still fits.
     */
    suspend fun setTodayLimit(limit: Int)

    /** Replaces saved capture phrases. Order is the order they are shown. */
    suspend fun setSavedPhrases(phrases: List<String>)

    suspend fun recordNudge(taskId: String)
}
