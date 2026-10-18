// SPDX-License-Identifier: GPL-3.0-or-later
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
     * When Today is full, saves the task to Inbox in the same transaction and returns
     * [PromoteOutcome.NeedsSwap] with its id, so the capture is never lost.
     */
    suspend fun captureIntoToday(title: String): PromoteOutcome

    /**
     * Starts a new task now. Nothing active means it just starts.
     * An active frame is parked and this one starts with no parent: a switch, not a nested interruption.
     * A nested child is closed so it starts to count. When that child is still open and
     * Today exceeds the cap, the child waits in Inbox and this returns [PromoteOutcome.NeedsSwap].
     * When Today has no free slot for the new task, saves it to Inbox in the same transaction
     * and returns [PromoteOutcome.NeedsSwap] with its id. It is not started.
     */
    suspend fun captureAndStart(title: String): PromoteOutcome

    /**
     * Undo for a capture kept in Inbox: deletes [taskId] only while it is still an open Inbox
     * task with no open frame. Anything else is a no-op.
     */
    suspend fun discardCapture(taskId: String)

    /** Parks a new task in Later. Does not start it. A blank title changes nothing. */
    suspend fun captureLater(title: String)

    /**
     * Stores a new task outside Inbox, Today, and Later until [scheduledOnEpochDay], and,
     * when [scheduledAtMinuteOfDay] is set, until that local time on that day.
     * Does not start it. A blank title changes nothing.
     */
    suspend fun captureScheduled(
        title: String,
        scheduledOnEpochDay: Long,
        scheduledAtMinuteOfDay: Int? = null,
    )

    /**
     * Adds a countdown to the event on [epochDay]. It takes no Today slot and does not start.
     * A blank title changes nothing.
     */
    suspend fun captureCountdown(
        title: String,
        epochDay: Long,
    )

    /** Moves a waiting countdown to [epochDay]. An arrived one, or another bucket, is left alone. */
    suspend fun rescheduleCountdown(
        taskId: String,
        epochDay: Long,
    )

    /** Drops a waiting countdown. An arrived one, or another bucket, is left alone. */
    suspend fun removeCountdown(taskId: String)

    /**
     * Brings the oldest due countdown into focus, outside quiet hours only.
     * An active frame pauses and becomes the event's parent, like a pile interrupt.
     * With nothing active, the event starts on its own. Only one countdown is in the
     * stack at a time: while one has an open frame, the next waits.
     * Returns the event that arrived, or null.
     */
    suspend fun releaseDueCountdowns(): Task?

    /**
     * Closes the open frame of [taskId] without marking it done, sets its parent to
     * [FrameState.AWAITING_RESUME], and moves the task to [destination]. Nothing resumes on its own.
     * [DetachDestination.Today] places it without starting it. When Today is full it
     * waits in Inbox and returns [PromoteOutcome.NeedsSwap], so Replace can place it.
     * A nested child already on Today is treated the same once its parent frame is
     * closed: if it now exceeds the cap, Inbox and [PromoteOutcome.NeedsSwap].
     * [DetachDestination.Later] waits in Inbox. Later is not a placement anymore.
     * [DetachDestination.Countdown] needs a day after today.
     */
    suspend fun detachInterruption(
        taskId: String,
        destination: DetachDestination,
    ): PromoteOutcome

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

    /**
     * Moves an open task to Inbox without starting it. Parks an open frame the same
     * way Later does. A schedule is cleared. Already-Inbox tasks are left alone.
     */
    suspend fun moveToInbox(taskId: String)

    /** Drops a scheduled task. Other buckets are left alone. */
    suspend fun removeScheduled(taskId: String)

    /**
     * Moves an open scheduled task to [epochDay] and keeps its time.
     * Does not create a task. Other buckets are left alone.
     * Returns false when nothing changed.
     */
    suspend fun rescheduleScheduled(
        taskId: String,
        epochDay: Long,
    ): Boolean

    /**
     * Moves an open task onto [scheduledOnEpochDay] as [TaskBucket.SCHEDULED].
     * Parks an open frame the same way Later does. Keeps a time already on a
     * scheduled task. Countdown tasks are left alone; use [rescheduleCountdown].
     * Returns false when nothing changed.
     */
    suspend fun scheduleExisting(
        taskId: String,
        scheduledOnEpochDay: Long,
    ): Boolean

    /**
     * Sets or clears the local time on an open scheduled task.
     * Null clears the time. Does not create a task. Other buckets are left alone.
     * Returns false when nothing changed.
     */
    suspend fun setScheduledTime(
        taskId: String,
        minuteOfDay: Int?,
    ): Boolean

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
     * Parks the active frame, if any, and activates [taskId].
     * This is a switch, not an interruption: the new frame has no parent.
     * A nested child is closed so it starts to count. When that child is still open
     * and Today exceeds the cap, the child waits in Inbox and this returns
     * [PromoteOutcome.NeedsSwap].
     * A task that is not already in Today takes a free slot in the same transaction.
     * When that slot is missing because Today is full, returns [PromoteOutcome.NeedsSwap]
     * and writes nothing. Refuses a frame that is waiting for an explicit resume.
     */
    suspend fun start(taskId: String): PromoteOutcome

    /**
     * Pauses the active Today frame and makes [taskId] the active child.
     * Reuses [taskId]; does not create another task.
     * The paused parent keeps its Today slot. When [taskId] is not already in
     * Today, it moves onto Today as the child. That child does not count toward
     * the Today cap while it remains an open child of the current frame.
     * A paused frame for [taskId] is reactivated as the child. A frame waiting
     * for an explicit resume is not hijacked.
     */
    suspend fun interruptWith(taskId: String)

    /**
     * Pauses the active frame and pushes a child frame for a new Inbox task.
     * A blank title is stored as [UNTITLED_INTERRUPTION].
     */
    suspend fun interrupt(title: String?): Task

    /**
     * Pauses the active task and starts [taskId] nested under it.
     * [taskId] must be an open Inbox, Later, or Scheduled task. It leaves that pile,
     * and a schedule is cleared so it cannot also land on Today.
     * The parent stays where it was, including a full Today, and keeps its return cue.
     * Does not park or replace another Today task. Refuses when nothing is active,
     * and does not hijack a frame that is waiting for an explicit resume.
     */
    suspend fun interruptFromPile(taskId: String)

    /**
     * Marks the active task done, closes its frame, and sets the parent to
     * [FrameState.AWAITING_RESUME] when one exists. The task stays in history.
     * Returns null when nothing is active.
     */
    suspend fun completeActive(): UndoToken?

    /**
     * Activates a frame that is [FrameState.AWAITING_RESUME]. Fails if another frame is active.
     * A still-open un-nested Today child that now exceeds the cap waits in Inbox
     * and returns [PromoteOutcome.NeedsSwap].
     */
    suspend fun resume(frameId: String): PromoteOutcome

    /**
     * Moves a task to Later. A nested active child is closed; a root active frame
     * is paused. When it has a parent, that parent becomes [FrameState.AWAITING_RESUME].
     * An awaiting frame is parked as paused without revealing another layer.
     */
    suspend fun demote(taskId: String): UndoToken

    /**
     * Parks the active frame. A nested child is closed so the parent can wait,
     * and a still-open Today child that now exceeds the cap waits in Inbox
     * and returns [PromoteOutcome.NeedsSwap].
     */
    suspend fun pauseActive(): PromoteOutcome

    suspend fun updateReturnCue(
        taskId: String,
        cue: String?,
    )

    suspend fun rename(
        taskId: String,
        title: String,
    )

    /**
     * Puts the snapshotted rows back. Used by the Undo snackbar for Today placement
     * and countdown resume. Done and Later do not offer that snackbar.
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

    /** Unarchived repeats, in sort order. Used by the REPEAT section to edit the list. */
    fun observeRepeats(): Flow<List<Repeat>>

    /**
     * Due repeats for the current local day, per band, with open/done/missed.
     * Repeats never appear in [observeSnapshot] and never count toward Today.
     */
    fun observeTodaysRepeats(): Flow<TodaysRepeats>

    /**
     * Adds a repeat. A blank title writes nothing and returns null.
     * [everyNDays] below 1 is stored as 1. [reminderOn] defaults to true.
     */
    suspend fun createRepeat(
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean = true,
    ): String?

    /**
     * Rewrites every field on an existing, unarchived repeat.
     * A blank title, a missing id, or an archived row writes nothing and returns false.
     */
    suspend fun updateRepeat(
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean,
        sortOrder: Long,
    ): Boolean

    /** Soft-deletes a repeat. Logs stay. Returns false when the id is missing or already archived. */
    suspend fun archiveRepeat(id: String): Boolean

    /**
     * Writes [RepeatOutcome.DONE] for that due day. Safe to call twice.
     * A missed row becomes done. Returns false when the repeat is missing or archived.
     */
    suspend fun markRepeatDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean

    /**
     * Removes a [RepeatOutcome.DONE] log row so the item is open again.
     * A missed row is left alone. Returns false when there was nothing to undo.
     */
    suspend fun undoRepeatDone(
        repeatId: String,
        epochDay: Long,
    ): Boolean

    /**
     * Writes [outcome] for that due day. Used to restore MISSED after a late Done.
     * Returns false when the repeat is missing or archived.
     */
    suspend fun writeRepeatOutcome(
        repeatId: String,
        epochDay: Long,
        outcome: RepeatOutcome,
    ): Boolean

    /**
     * Writes [RepeatOutcome.MISSED] for still-open due repeats in [band] on [epochDay].
     * Existing log rows are left alone.
     */
    suspend fun markOpenRepeatsMissed(
        band: RepeatBand,
        epochDay: Long,
    )

    /** User-set band times. Invalid or out-of-order values write nothing and return false. */
    suspend fun setBandTimes(times: BandTimes): Boolean

    /** Remembers that Settings dismissed the one-time exact-alarm prompt. */
    suspend fun dismissExactAlarmPrompt()

    /** Holds a band nudge until the active focus frame ends, or clears the hold. */
    suspend fun setHeldBandNudge(
        band: RepeatBand?,
        epochDay: Long?,
    )

    suspend fun followUpPostedDay(band: RepeatBand): Long?

    suspend fun setFollowUpPostedDay(
        band: RepeatBand,
        epochDay: Long,
    )

    /** True when an [FrameState.ACTIVE] frame exists. Used by band alarms to hold a nudge. */
    suspend fun hasActiveFocus(): Boolean
}
