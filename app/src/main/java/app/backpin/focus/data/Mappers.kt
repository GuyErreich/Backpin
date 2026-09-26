package app.backpin.focus.data

import app.backpin.focus.domain.FocusFrame
import app.backpin.focus.domain.FrameState
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
