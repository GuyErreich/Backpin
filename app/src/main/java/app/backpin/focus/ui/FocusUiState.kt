package app.backpin.focus.ui

import app.backpin.focus.domain.UndoToken
import app.backpin.focus.domain.UserPreferences

enum class SlotRole {
    NOW,
    WAITING,
    OPEN,
    EMPTY,
}

data class TaskUi(
    val id: String,
    val title: String,
    val returnCue: String?,
)

data class NowUi(
    val taskId: String,
    val frameId: String,
    val title: String,
    val returnCue: String?,
    val accumulatedMillis: Long,
    val startedAtEpochMillis: Long,
    val isInterruption: Boolean,
)

/** The single parent waiting to be picked up. The rest of the stack stays hidden. */
data class RecoveryUi(
    val frameId: String,
    val taskId: String,
    val title: String,
    val returnCue: String?,
)

data class PausedUi(
    val taskId: String,
    val title: String,
    val returnCue: String?,
)

data class SlotUi(
    val role: SlotRole,
    val task: TaskUi?,
)

data class PendingUndo(
    val id: Long,
    val message: String,
    val token: UndoToken,
)

data class FocusUiState(
    val ready: Boolean = false,
    val now: NowUi? = null,
    val recovery: RecoveryUi? = null,
    val paused: PausedUi? = null,
    val todaySlots: List<SlotUi> = List(3) { SlotUi(SlotRole.EMPTY, null) },
    val laterCount: Int = 0,
    val laterTasks: List<TaskUi> = emptyList(),
    val inboxCount: Int = 0,
    val inboxTasks: List<TaskUi> = emptyList(),
    val archiveCount: Int = 0,
    val archiveTasks: List<TaskUi> = emptyList(),
    val captureOpen: Boolean = false,
    val captureInterrupts: Boolean = false,
    val swapIncoming: TaskUi? = null,
    val swapChoices: List<TaskUi> = emptyList(),
    val switchOpen: Boolean = false,
    val switchTargets: List<TaskUi> = emptyList(),
    val undo: PendingUndo? = null,
    val preferences: UserPreferences = UserPreferences(),
)
