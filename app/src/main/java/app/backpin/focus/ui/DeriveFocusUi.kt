package app.backpin.focus.ui

import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UndoToken
import app.backpin.focus.domain.UserPreferences

internal data class ChromeState(
    val captureOpen: Boolean,
    val inboxExpanded: Boolean,
    val laterExpanded: Boolean,
    val swapIncomingId: String?,
    val switchOpen: Boolean,
    val undo: PendingUndo?,
    val preferences: UserPreferences,
)

internal fun deriveFocusUi(
    snapshot: FocusSnapshot,
    chrome: ChromeState,
): FocusUiState {
    val shown = projectPendingDone(snapshot, chrome.undo)
    val tasksById = shown.tasks.associateBy { it.id }
    val openTasks = shown.tasks.filter { it.status == TaskStatus.OPEN }
    val active = shown.frames.firstOrNull { it.state == FrameState.ACTIVE }
    val awaiting =
        shown.frames
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
            shown.frames
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

    val today = openTasks.filter { it.bucket == TaskBucket.TODAY }.sortedBy { it.sortOrder }.take(3)
    val slots =
        List(3) { index ->
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
    val incoming = chrome.swapIncomingId?.let { id -> tasksById[id]?.toUi() }
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
        laterExpanded = chrome.laterExpanded,
        laterTasks = later,
        inboxCount = inbox.size,
        inboxExpanded = chrome.inboxExpanded,
        inboxTasks = inbox,
        captureOpen = chrome.captureOpen,
        swapIncoming = incoming,
        swapChoices = if (incoming == null) emptyList() else today.map { it.toUi() },
        switchOpen = chrome.switchOpen,
        switchTargets = switchTargets,
        undo = chrome.undo,
        preferences = chrome.preferences,
    )
}

private fun Task.toUi(): TaskUi = TaskUi(id = id, title = title, returnCue = returnCue)

/**
 * Done commits in the database, then offers Undo. The snapshot flow can lag that
 * offer by one emission, which left the finished task on screen under the Undo bar.
 * While the rows still match the pre-image, show the post-done screen: no active
 * task, and the parent — if this token has one — waiting to be resumed.
 */
private fun projectPendingDone(
    snapshot: FocusSnapshot,
    undo: PendingUndo?,
): FocusSnapshot {
    val pending = undo ?: return snapshot
    if (pending.message != MARKED_DONE_UNDO) return snapshot
    val token = pending.token
    if (token.tasks.isEmpty() || !stillThePreImage(snapshot, token)) return snapshot
    val doneIds = token.tasks.map { it.id }.toSet()
    val closedIds = token.frames.filter { it.taskId in doneIds }.map { it.id }.toSet()
    val awaitingIds = token.frames.filter { it.taskId !in doneIds }.map { it.id }.toSet()
    return snapshot.copy(
        tasks =
            snapshot.tasks.map { task ->
                if (task.id in doneIds) task.copy(status = TaskStatus.DONE) else task
            },
        frames =
            snapshot.frames.map { frame ->
                when (frame.id) {
                    in closedIds -> frame.copy(state = FrameState.CLOSED)
                    in awaitingIds -> frame.copy(state = FrameState.AWAITING_RESUME)
                    else -> frame
                }
            },
    )
}

private fun stillThePreImage(
    snapshot: FocusSnapshot,
    token: UndoToken,
): Boolean {
    val tasksById = snapshot.tasks.associateBy { it.id }
    val framesById = snapshot.frames.associateBy { it.id }
    return token.tasks.all { saved -> tasksById[saved.id]?.status == saved.status } &&
        token.frames.all { saved -> framesById[saved.id]?.state == saved.state }
}
