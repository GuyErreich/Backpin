package app.backpin.focus.domain

import kotlinx.coroutines.flow.Flow

/**
 * Task and focus operations the UI calls. Each method delegates to one repository
 * transaction so buttons and any later gestures share the same behavior.
 */
class FocusUseCases(
    private val repository: FocusRepository,
) {
    fun observeSnapshot(): Flow<FocusSnapshot> = repository.observeSnapshot()

    val preferences: Flow<UserPreferences> = repository.preferences

    suspend fun capture(title: String) {
        repository.capture(title)
    }

    suspend fun captureIntoToday(title: String): PromoteOutcome = repository.captureIntoToday(title)

    suspend fun captureAndStart(title: String): PromoteOutcome = repository.captureAndStart(title)

    suspend fun captureLater(title: String) {
        repository.captureLater(title)
    }

    suspend fun captureScheduled(
        title: String,
        scheduledOnEpochDay: Long,
    ) {
        repository.captureScheduled(title, scheduledOnEpochDay)
    }

    suspend fun captureReplacing(
        title: String,
        outgoingTaskId: String,
    ): UndoToken = repository.captureReplacing(title, outgoingTaskId)

    suspend fun interruptWithNew(title: String) {
        repository.interruptWithNew(title)
    }

    suspend fun moveToInbox(taskId: String) {
        repository.moveToInbox(taskId)
    }

    suspend fun removeScheduled(taskId: String) {
        repository.removeScheduled(taskId)
    }

    suspend fun releaseDueScheduled() {
        repository.releaseDueScheduled()
    }

    suspend fun promote(taskId: String): PromoteOutcome = repository.promote(taskId)

    suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ): UndoToken = repository.swapIntoToday(incomingTaskId, outgoingTaskId)

    suspend fun start(taskId: String): PromoteOutcome = repository.start(taskId)

    suspend fun interruptWith(taskId: String) {
        repository.interruptWith(taskId)
    }

    suspend fun interrupt(title: String?): Task = repository.interrupt(title)

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
}
