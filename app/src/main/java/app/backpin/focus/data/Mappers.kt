package app.backpin.focus.data

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatLog
import app.backpin.focus.domain.RepeatOutcome
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus

fun TaskEntity.toDomain(): Task =
    Task(
        id = id,
        title = title,
        bucket = TaskBucket.valueOf(bucket),
        status = TaskStatus.valueOf(status),
        returnCue = returnCue,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        scheduledOnEpochDay = scheduledOnEpochDay,
        scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        countdownIntervalDays = countdownIntervalDays,
    )

fun Task.toEntity(): TaskEntity =
    TaskEntity(
        id = id,
        title = title,
        bucket = bucket.name,
        status = status.name,
        returnCue = returnCue,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        scheduledOnEpochDay = scheduledOnEpochDay,
        scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        countdownIntervalDays = countdownIntervalDays,
    )

fun FocusFrameEntity.toDomain(): FocusFrame =
    FocusFrame(
        id = id,
        taskId = taskId,
        parentFrameId = parentFrameId,
        state = FrameState.valueOf(state),
        startedAtEpochMillis = startedAtEpochMillis,
        pausedAtEpochMillis = pausedAtEpochMillis,
        accumulatedMillis = accumulatedMillis,
    )

fun FocusFrame.toEntity(): FocusFrameEntity =
    FocusFrameEntity(
        id = id,
        taskId = taskId,
        parentFrameId = parentFrameId,
        state = state.name,
        startedAtEpochMillis = startedAtEpochMillis,
        pausedAtEpochMillis = pausedAtEpochMillis,
        accumulatedMillis = accumulatedMillis,
    )

fun RepeatEntity.toDomain(): Repeat =
    Repeat(
        id = id,
        title = title,
        band = RepeatBand.valueOf(band),
        everyNDays = everyNDays,
        anchorEpochDay = anchorEpochDay,
        reminderOn = reminderOn,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
    )

fun Repeat.toEntity(): RepeatEntity =
    RepeatEntity(
        id = id,
        title = title,
        band = band.name,
        everyNDays = everyNDays,
        anchorEpochDay = anchorEpochDay,
        reminderOn = reminderOn,
        sortOrder = sortOrder,
        createdAtEpochMillis = createdAtEpochMillis,
        archivedAtEpochMillis = archivedAtEpochMillis,
    )

fun RepeatLogEntity.toDomain(): RepeatLog =
    RepeatLog(
        repeatId = repeatId,
        epochDay = epochDay,
        outcome = RepeatOutcome.valueOf(outcome),
        atEpochMillis = atEpochMillis,
    )
