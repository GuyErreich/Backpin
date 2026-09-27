package app.backpin.focus.ui

import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.domain.scheduledWhenLabel
import java.time.LocalDate

internal data class ChromeState(
    val captureOpen: Boolean,
    val captureInterrupts: Boolean = false,
    val swapIncomingId: String?,
    val freshSwapTitle: String? = null,
    val switchOpen: Boolean,
    val undo: PendingUndo?,
    val preferences: UserPreferences,
)

internal fun deriveFocusUi(
    snapshot: FocusSnapshot,
    chrome: ChromeState,
    localDate: LocalDate = LocalDate.now(),
): FocusUiState {
    val tasksById = snapshot.tasks.associateBy { it.id }
    val openTasks = snapshot.tasks.filter { it.status == TaskStatus.OPEN }
    val active = snapshot.frames.firstOrNull { it.state == FrameState.ACTIVE }
    val awaiting =
        snapshot.frames
            .filter { it.state == FrameState.AWAITING_RESUME }
            .maxByOrNull { it.startedAtEpochMillis }

    val now =
        active?.let { frame ->
            val task = tasksById[frame.taskId] ?: return@let null
            if (task.status != TaskStatus.OPEN) return@let null
            NowUi(
                taskId = task.id,
                frameId = frame.id,
                title = task.title,
                returnCue = task.returnCue,
                accumulatedMillis = frame.accumulatedMillis,
                startedAtEpochMillis = frame.startedAtEpochMillis,
                isInterruption = frame.parentFrameId != null,
            )
        }

    val recovery =
        if (now == null) {
            awaiting?.let { frame ->
                val task = tasksById[frame.taskId] ?: return@let null
                if (task.status != TaskStatus.OPEN) return@let null
                RecoveryUi(
                    frameId = frame.id,
                    taskId = task.id,
                    title = task.title,
                    returnCue = task.returnCue,
                )
            }
        } else {
            null
        }

    val paused =
        if (now == null && recovery == null) {
            snapshot.frames
                .filter { it.state == FrameState.PAUSED }
                .maxByOrNull { it.pausedAtEpochMillis ?: 0L }
                ?.let { frame ->
                    val task = tasksById[frame.taskId] ?: return@let null
                    if (task.status != TaskStatus.OPEN) return@let null
                    PausedUi(taskId = task.id, title = task.title, returnCue = task.returnCue)
                }
        } else {
            null
        }

    val limit = chrome.preferences.todayLimit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
    val today = openTasks.filter { it.bucket == TaskBucket.TODAY }.sortedBy { it.sortOrder }
    val slots =
        List(maxOf(limit, today.size)) { index ->
            val task = today.getOrNull(index)
            if (task == null) {
                SlotUi(SlotRole.EMPTY, null)
            } else {
                val role =
                    when (task.id) {
                        now?.taskId -> SlotRole.NOW
                        recovery?.taskId -> SlotRole.WAITING
                        else -> SlotRole.OPEN
                    }
                SlotUi(role, task.toUi())
            }
        }

    val later = openTasks.filter { it.bucket == TaskBucket.LATER }.sortedBy { it.sortOrder }.map { it.toUi() }
    val inbox = openTasks.filter { it.bucket == TaskBucket.INBOX }.sortedBy { it.sortOrder }.map { it.toUi() }
    val scheduled =
        openTasks
            .filter { it.bucket == TaskBucket.SCHEDULED }
            .sortedWith(compareBy({ it.scheduledOnEpochDay ?: Long.MAX_VALUE }, { it.sortOrder }))
            .map { task ->
                val day = task.scheduledOnEpochDay
                ScheduledTaskUi(
                    id = task.id,
                    title = task.title,
                    whenLabel = if (day == null) "Unscheduled" else scheduledWhenLabel(day, localDate),
                    due = day != null && !LocalDate.ofEpochDay(day).isAfter(localDate),
                )
            }
    val archive =
        snapshot.tasks
            .filter { it.status == TaskStatus.DONE }
            .sortedWith(compareByDescending<Task> { it.completedAtEpochMillis ?: 0L }.thenByDescending { it.sortOrder })
            .map { it.toUi() }
    val incoming = chrome.swapIncomingId?.let { id -> tasksById[id]?.toUi() }
    val swapping = incoming != null || chrome.freshSwapTitle != null
    val switchTargets =
        today
            .filter { it.id != now?.taskId }
            .map { it.toUi() }

    return FocusUiState(
        ready = true,
        now = now,
        recovery = recovery,
        paused = paused,
        todaySlots = slots,
        laterCount = later.size,
        laterTasks = later,
        inboxCount = inbox.size,
        inboxTasks = inbox,
        archiveCount = archive.size,
        archiveTasks = archive,
        scheduledCount = scheduled.size,
        scheduledTasks = scheduled,
        captureOpen = chrome.captureOpen,
        captureInterrupts = chrome.captureInterrupts,
        swapIncoming = incoming,
        freshSwapTitle = chrome.freshSwapTitle,
        swapChoices = if (!swapping) emptyList() else today.map { it.toUi() },
        switchOpen = chrome.switchOpen,
        switchTargets = switchTargets,
        undo = chrome.undo,
        preferences = chrome.preferences,
    )
}

private fun Task.toUi(): TaskUi = TaskUi(id = id, title = title, returnCue = returnCue)
