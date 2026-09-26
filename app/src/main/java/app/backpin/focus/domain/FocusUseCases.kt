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

    suspend fun promote(taskId: String): PromoteOutcome = repository.promote(taskId)

    suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ) {
        repository.swapIntoToday(incomingTaskId, outgoingTaskId)
    }

    suspend fun start(taskId: String) {
        repository.start(taskId)
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
}
