// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Task and focus operations the UI calls. Each method delegates to one repository
 * transaction so buttons and any later gestures share the same behavior.
 */
class FocusUseCases(
    private val repository: FocusRepository,
    private val wakeUp: ReleaseWakeUp = ReleaseWakeUp {},
) {
    fun observeSnapshot(): Flow<FocusSnapshot> = repository.observeSnapshot()

    val preferences: Flow<UserPreferences> = repository.preferences

    suspend fun capture(title: String): String? = repository.capture(title)

    suspend fun captureIntoToday(title: String): PromoteOutcome = repository.captureIntoToday(title)

    suspend fun captureAndStart(title: String): PromoteOutcome = repository.captureAndStart(title)

    suspend fun discardCapture(taskId: String) {
        repository.discardCapture(taskId)
    }

    /** A set time also queues one inexact check for that moment. */
    suspend fun captureScheduled(
        title: String,
        scheduledOnEpochDay: Long,
        scheduledAtMinuteOfDay: Int? = null,
    ) {
        if (title.isBlank()) return
        repository.captureScheduled(title, scheduledOnEpochDay, scheduledAtMinuteOfDay)
        if (scheduledAtMinuteOfDay != null) {
            wakeUp.wakeAt(scheduledMomentEpochMillis(scheduledOnEpochDay, scheduledAtMinuteOfDay))
        }
    }

    suspend fun captureCountdown(
        title: String,
        epochDay: Long,
    ) {
        repository.captureCountdown(title, epochDay)
    }

    suspend fun rescheduleCountdown(
        taskId: String,
        epochDay: Long,
    ) {
        repository.rescheduleCountdown(taskId, epochDay)
    }

    suspend fun removeCountdown(taskId: String) {
        repository.removeCountdown(taskId)
    }

    suspend fun detachInterruption(
        taskId: String,
        destination: DetachDestination,
    ): PromoteOutcome = repository.detachInterruption(taskId, destination)

    suspend fun moveToInbox(taskId: String) {
        repository.moveToInbox(taskId)
    }

    /**
     * Moves [taskId] onto [epochDay]. A countdown stays a countdown.
     * A timed scheduled task queues one inexact check for the new day.
     */
    suspend fun scheduleExisting(
        taskId: String,
        epochDay: Long,
    ) {
        val task = observeSnapshot().first().tasks.firstOrNull { it.id == taskId } ?: return
        if (task.bucket == TaskBucket.COUNTDOWN) {
            repository.rescheduleCountdown(taskId, epochDay)
            return
        }
        if (!repository.scheduleExisting(taskId, epochDay)) return
        val minute = task.scheduledAtMinuteOfDay ?: return
        wakeUp.wakeAt(scheduledMomentEpochMillis(epochDay, minute))
    }

    suspend fun removeScheduled(taskId: String) {
        repository.removeScheduled(taskId)
    }

    /** Keeps the time already on the task. A set time queues one inexact check for the new day. */
    suspend fun rescheduleScheduled(
        taskId: String,
        epochDay: Long,
    ) {
        val task = observeSnapshot().first().tasks.firstOrNull { it.id == taskId } ?: return
        if (task.bucket != TaskBucket.SCHEDULED) return
        if (!repository.rescheduleScheduled(taskId, epochDay)) return
        val minute = task.scheduledAtMinuteOfDay ?: return
        wakeUp.wakeAt(scheduledMomentEpochMillis(epochDay, minute))
    }

    /** Null clears the time. A set time queues one inexact check on the task's existing day. */
    suspend fun setScheduledTime(
        taskId: String,
        minuteOfDay: Int?,
    ) {
        val task = observeSnapshot().first().tasks.firstOrNull { it.id == taskId } ?: return
        if (task.bucket != TaskBucket.SCHEDULED) return
        val day = task.scheduledOnEpochDay ?: return
        if (!repository.setScheduledTime(taskId, minuteOfDay)) return
        if (minuteOfDay != null) wakeUp.wakeAt(scheduledMomentEpochMillis(day, minuteOfDay))
    }

    suspend fun releaseDueScheduled() {
        repository.releaseDueScheduled()
    }

    suspend fun promote(taskId: String): PromoteOutcome = repository.promote(taskId)

    suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ): UndoToken = repository.swapIntoToday(incomingTaskId, outgoingTaskId)

    suspend fun start(taskId: String): StartOutcome = repository.start(taskId)

    suspend fun interruptWith(taskId: String) {
        repository.interruptWith(taskId)
    }

    suspend fun interrupt(title: String?): Task = repository.interrupt(title)

    suspend fun interruptFromPile(taskId: String) {
        repository.interruptFromPile(taskId)
    }

    suspend fun completeActive(): UndoToken? = repository.completeActive()

    suspend fun resume(frameId: String) {
        repository.resume(frameId)
    }

    suspend fun demote(taskId: String): UndoToken = repository.demote(taskId)

    suspend fun pauseActive() {
        repository.pauseActive()
    }

    suspend fun updateReturnCue(
        taskId: String,
        cue: String?,
    ) {
        repository.updateReturnCue(taskId, cue)
    }

    suspend fun rename(
        taskId: String,
        title: String,
    ) {
        repository.rename(taskId, title)
    }

    suspend fun undo(token: UndoToken) {
        repository.undo(token)
    }

    suspend fun restore(taskId: String) {
        repository.restore(taskId)
    }

    suspend fun completeTask(taskId: String): UndoToken? = repository.completeTask(taskId)

    suspend fun reorderToday(orderedIds: List<String>): UndoToken? = repository.reorderToday(orderedIds)

    suspend fun setNudge(
        enabled: Boolean,
        minutes: Int,
    ) {
        repository.setNudge(enabled, minutes)
    }

    suspend fun setQuietHours(
        start: String?,
        end: String?,
    ) {
        repository.setQuietHours(start, end)
    }

    suspend fun setCurrentNotificationEnabled(enabled: Boolean) {
        repository.setCurrentNotificationEnabled(enabled)
    }

    suspend fun setTodayLimit(limit: Int) {
        repository.setTodayLimit(limit)
    }

    suspend fun setSavedPhrases(phrases: List<String>) {
        repository.setSavedPhrases(phrases)
    }

    suspend fun convertToRepeat(
        taskId: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        anchorEpochDay: Long,
        reminderOn: Boolean = true,
    ): ConvertToRepeatResult = repository.convertToRepeat(taskId, title, band, everyNDays, anchorEpochDay, reminderOn)

    suspend fun undoConvertToRepeat(snapshot: ConvertToRepeatResult.Converted) {
        repository.undoConvertToRepeat(snapshot)
    }
}
