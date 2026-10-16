package app.backpin.focus.ui

import app.backpin.focus.domain.CapturePlace
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

/** The open task currently in the edit sheet. [place] is null for a nested interruption. */
data class EditingUi(
    val id: String,
    val title: String,
    val place: CapturePlace?,
    val scheduledOnEpochDay: Long? = null,
)

/** A task waiting on a local date. [due] means that date is today or earlier and it is still scheduled. */
data class ScheduledTaskUi(
    val id: String,
    val title: String,
    val whenLabel: String,
    val due: Boolean,
    val epochDay: Long? = null,
    val minuteOfDay: Int? = null,
)

/** A countdown still waiting for its day. [dayLabel] is "Today", "Tomorrow", or "12 days". */
data class CountdownUi(
    val id: String,
    val title: String,
    val epochDay: Long,
    val dayLabel: String,
    val dateLabel: String,
)

/** [isEvent] is a countdown that reached its day and came into focus. */
data class NowUi(
    val taskId: String,
    val frameId: String,
    val title: String,
    val returnCue: String?,
    val accumulatedMillis: Long,
    val startedAtEpochMillis: Long,
    val isInterruption: Boolean,
    val isEvent: Boolean = false,
    val countdownIntervalDays: Int? = null,
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
    val canInterrupt: Boolean = false,
    val recovery: RecoveryUi? = null,
    val paused: PausedUi? = null,
    val todaySlots: List<SlotUi> = List(3) { SlotUi(SlotRole.EMPTY, null) },
    val laterCount: Int = 0,
    val laterTasks: List<TaskUi> = emptyList(),
    val inboxCount: Int = 0,
    val inboxTasks: List<TaskUi> = emptyList(),
    val archiveCount: Int = 0,
    val archiveTasks: List<TaskUi> = emptyList(),
    val scheduledCount: Int = 0,
    val scheduledTasks: List<ScheduledTaskUi> = emptyList(),
    val countdowns: List<CountdownUi> = emptyList(),
    val captureOpen: Boolean = false,
    val captureInterrupts: Boolean = false,
    val editing: EditingUi? = null,
    val swapIncoming: TaskUi? = null,
    val freshSwapTitle: String? = null,
    val swapChoices: List<TaskUi> = emptyList(),
    val switchOpen: Boolean = false,
    val switchTargets: List<TaskUi> = emptyList(),
    val undo: PendingUndo? = null,
    val preferences: UserPreferences = UserPreferences(),
)
