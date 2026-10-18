// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.ui.ChromeState
import app.backpin.focus.ui.PendingUndo
import kotlinx.coroutines.flow.first

/** Titles that fill the default Today cap. */
fun defaultTodayTitles(): List<String> = listOf("Write the brief", "Call Dana", "File the form")

fun sampleTask(
    id: String = "task-1",
    title: String = "Write the brief",
    bucket: TaskBucket = TaskBucket.INBOX,
    status: TaskStatus = TaskStatus.OPEN,
    returnCue: String? = null,
    sortOrder: Long = 0L,
    createdAtEpochMillis: Long = DEFAULT_TEST_INSTANT.toEpochMilli(),
    completedAtEpochMillis: Long? = null,
    scheduledOnEpochDay: Long? = null,
    scheduledAtMinuteOfDay: Int? = null,
    countdownIntervalDays: Int? = null,
): Task =
    Task(
        id = id,
        title = title,
        bucket = bucket,
        status = status,
        returnCue = returnCue,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        scheduledOnEpochDay = scheduledOnEpochDay,
        scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        countdownIntervalDays = countdownIntervalDays,
    )

fun sampleRepeat(
    id: String = "repeat-1",
    title: String = "Take meds",
    band: RepeatBand = RepeatBand.MORNING,
    everyNDays: Int = 1,
    anchorEpochDay: Long = DEFAULT_TEST_INSTANT.atZone(DEFAULT_TEST_ZONE).toLocalDate().toEpochDay(),
    reminderOn: Boolean = true,
    sortOrder: Long = 0L,
    createdAtEpochMillis: Long = DEFAULT_TEST_INSTANT.toEpochMilli(),
    archivedAtEpochMillis: Long? = null,
): Repeat =
    Repeat(
        id = id,
        title = title,
        band = band,
        everyNDays = everyNDays,
        anchorEpochDay = anchorEpochDay,
        reminderOn = reminderOn,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
    )

fun sampleFrame(
    id: String = "frame-1",
    taskId: String = "task-1",
    parentFrameId: String? = null,
    state: FrameState = FrameState.ACTIVE,
    startedAtEpochMillis: Long = DEFAULT_TEST_INSTANT.toEpochMilli(),
    pausedAtEpochMillis: Long? = null,
    accumulatedMillis: Long = 0L,
): FocusFrame =
    FocusFrame(
        id = id,
        taskId = taskId,
        parentFrameId = parentFrameId,
        state = state,
        startedAtEpochMillis = startedAtEpochMillis,
        pausedAtEpochMillis = pausedAtEpochMillis,
        accumulatedMillis = accumulatedMillis,
    )

fun sampleSnapshot(
    tasks: List<Task> = emptyList(),
    frames: List<FocusFrame> = emptyList(),
): FocusSnapshot = FocusSnapshot(tasks = tasks, frames = frames)

fun sampleChrome(
    captureOpen: Boolean = false,
    captureInterrupts: Boolean = false,
    swapIncomingId: String? = null,
    freshSwapTitle: String? = null,
    switchOpen: Boolean = false,
    undo: PendingUndo? = null,
    preferences: UserPreferences = UserPreferences(),
    editingTaskId: String? = null,
): ChromeState =
    ChromeState(
        captureOpen = captureOpen,
        captureInterrupts = captureInterrupts,
        swapIncomingId = swapIncomingId,
        freshSwapTitle = freshSwapTitle,
        switchOpen = switchOpen,
        undo = undo,
        preferences = preferences,
        editingTaskId = editingTaskId,
    )

/**
 * Places [titles] on Today through the real repository. Defaults to three
 * titles so the default Today cap is full.
 */
suspend fun FocusRepository.seedFullToday(
    titles: List<String> = defaultTodayTitles(),
): List<Task> {
    for (title in titles) {
        val outcome = captureIntoToday(title)
        check(outcome is PromoteOutcome.Promoted) {
            "seedFullToday could not place '$title': $outcome"
        }
    }
    return observeSnapshot()
        .first()
        .tasks
        .filter { it.bucket == TaskBucket.TODAY && it.status == TaskStatus.OPEN }
        .sortedBy { it.sortOrder }
}

suspend fun FocusRepository.seedInbox(vararg titles: String): List<Task> {
    titles.forEach { capture(it) }
    val wanted = titles.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return observeSnapshot().first().tasks.filter { it.title in wanted }
}

suspend fun FocusRepository.seedRepeat(
    title: String = "Take meds",
    band: RepeatBand = RepeatBand.MORNING,
    everyNDays: Int = 1,
    anchorEpochDay: Long,
    reminderOn: Boolean = true,
): Repeat {
    val id =
        createRepeat(
            title = title,
            band = band,
            everyNDays = everyNDays,
            anchorEpochDay = anchorEpochDay,
            reminderOn = reminderOn,
        ) ?: error("seedRepeat wrote nothing for '$title'")
    return observeRepeats().first().single { it.id == id }
}
