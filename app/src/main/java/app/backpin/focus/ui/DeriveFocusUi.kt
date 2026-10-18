// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.domain.editPlace
import app.backpin.focus.domain.countdownDayLabel
import app.backpin.focus.domain.countdownDaysLeft
import app.backpin.focus.domain.isScheduledDue
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.domain.scheduledWhenLabel
import java.time.LocalDate
import java.time.LocalTime

internal data class ChromeState(
    val captureOpen: Boolean,
    val captureInterrupts: Boolean = false,
    val swapIncomingId: String?,
    val freshSwapTitle: String? = null,
    val switchOpen: Boolean,
    val undo: PendingUndo?,
    val preferences: UserPreferences,
    val editingTaskId: String? = null,
)

internal fun deriveFocusUi(
    snapshot: FocusSnapshot,
    chrome: ChromeState,
    localDate: LocalDate = LocalDate.now(),
    localTime: LocalTime = LocalTime.now(),
): FocusUiState {
    val localNow = localDate.atTime(localTime)
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
                isEvent = task.bucket == TaskBucket.COUNTDOWN,
                countdownIntervalDays = task.countdownIntervalDays,
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
            .sortedWith(
                compareBy(
                    { it.scheduledOnEpochDay ?: Long.MAX_VALUE },
                    { it.scheduledAtMinuteOfDay ?: -1 },
                    { it.sortOrder },
                ),
            ).map { task ->
                val day = task.scheduledOnEpochDay
                val minute = task.scheduledAtMinuteOfDay
                ScheduledTaskUi(
                    id = task.id,
                    title = task.title,
                    whenLabel = if (day == null) "Unscheduled" else scheduledWhenLabel(day, minute, localNow),
                    due = day != null && isScheduledDue(day, minute, localNow),
                    epochDay = day,
                    minuteOfDay = minute,
                )
            }
    val framedTaskIds =
        snapshot.frames
            .filter { it.state != FrameState.CLOSED }
            .map { it.taskId }
            .toSet()
    val countdowns =
        openTasks
            .filter { it.bucket == TaskBucket.COUNTDOWN && it.id !in framedTaskIds }
            .mapNotNull { task -> task.scheduledOnEpochDay?.let { day -> task to day } }
            .sortedWith(compareBy({ it.second }, { it.first.sortOrder }))
            .map { (task, day) ->
                CountdownUi(
                    id = task.id,
                    title = task.title,
                    epochDay = day,
                    dayLabel = countdownDayLabel(countdownDaysLeft(day, localDate)),
                    dateLabel = scheduleMomentLabel(day, null),
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
    val editing =
        chrome.editingTaskId?.let { id ->
            tasksById[id]?.takeIf { it.status == TaskStatus.OPEN }?.toEditing()
        }

    return FocusUiState(
        ready = true,
        now = now,
        canInterrupt = now != null,
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
        countdowns = countdowns,
        captureOpen = chrome.captureOpen,
        captureInterrupts = chrome.captureInterrupts,
        editing = editing,
        swapIncoming = incoming,
        freshSwapTitle = chrome.freshSwapTitle,
        swapChoices = if (!swapping) emptyList() else today.map { it.toUi() },
        switchOpen = chrome.switchOpen,
        switchTargets = switchTargets,
        undo = chrome.undo,
        preferences = chrome.preferences,
    )
}

private fun Task.toUi(): TaskUi = TaskUi(
    id = id,
    title = title,
    returnCue = returnCue,
    scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
)

private fun Task.toEditing(): EditingUi = EditingUi(
    id = id,
    title = title,
    place = bucket.editPlace(),
    scheduledOnEpochDay = scheduledOnEpochDay,
)
