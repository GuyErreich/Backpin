package app.backpin.focus.data

import androidx.room.withTransaction
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.MAX_OPEN_TODAY
import app.backpin.focus.domain.PromoteOutcome
import app.backpin.focus.domain.SystemAppClock
import app.backpin.focus.domain.Task
import app.backpin.focus.domain.TaskBucket
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
import app.backpin.focus.domain.UndoToken
import app.backpin.focus.domain.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class FocusRepositoryImpl(
    private val database: FocusStackDatabase,
    private val preferencesStore: PreferencesDataStore,
    private val clock: AppClock = SystemAppClock,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : FocusRepository {
    private val taskDao: TaskDao = database.taskDao()
    private val frameDao: FocusFrameDao = database.focusFrameDao()

    override fun observeSnapshot(): Flow<FocusSnapshot> =
        combine(taskDao.observeAll(), frameDao.observeAll()) { tasks, frames ->
            FocusSnapshot(
                tasks = tasks.map { it.toDomain() },
                frames = frames.map { it.toDomain() },
            )
        }

    override val preferences: Flow<UserPreferences> = preferencesStore.preferences

    override suspend fun capture(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            taskDao.upsert(newTask(trimmed, TaskBucket.INBOX))
        }
        recordInteraction()
    }

    override suspend fun promote(taskId: String): PromoteOutcome =
        database.withTransaction {
            val task = taskDao.getById(taskId) ?: return@withTransaction PromoteOutcome.Rejected("Missing task")
            if (task.status != TaskStatus.OPEN.name) {
                return@withTransaction PromoteOutcome.Rejected("Done tasks stay out of Today")
            }
            if (task.bucket == TaskBucket.TODAY.name) {
                return@withTransaction PromoteOutcome.Promoted
            }
            if (taskDao.countOpenToday() >= MAX_OPEN_TODAY) {
                val today = taskDao.openInBucket(TaskBucket.TODAY.name).map { it.toDomain() }
                return@withTransaction PromoteOutcome.NeedsSwap(today)
            }
            taskDao.upsert(task.copy(bucket = TaskBucket.TODAY.name, sortOrder = clock.nowEpochMillis()))
            requireInvariants()
            PromoteOutcome.Promoted
        }.also { outcome ->
            if (outcome is PromoteOutcome.Promoted) recordInteraction()
        }

    override suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ) {
        database.withTransaction {
            val incoming = taskDao.getById(incomingTaskId) ?: error("Missing incoming task")
            val outgoing = taskDao.getById(outgoingTaskId) ?: error("Missing outgoing task")
            taskDao.upsert(incoming.copy(bucket = TaskBucket.TODAY.name, sortOrder = clock.nowEpochMillis()))
            taskDao.upsert(outgoing.copy(bucket = TaskBucket.LATER.name))
            val valid =
                incoming.status == TaskStatus.OPEN.name &&
                    incoming.bucket != TaskBucket.TODAY.name &&
                    outgoing.bucket == TaskBucket.TODAY.name &&
                    outgoing.status == TaskStatus.OPEN.name &&
                    incoming.id != outgoing.id &&
                    taskDao.countOpenToday() <= MAX_OPEN_TODAY
            if (!valid) error("Swap rejected")
            requireInvariants()
        }
        recordInteraction()
    }

    override suspend fun start(taskId: String) {
        database.withTransaction {
            val task = taskDao.getById(taskId) ?: error("Missing task")
            check(task.status == TaskStatus.OPEN.name) { "Cannot start a done task" }
            val active = frameDao.getActive()
            if (active?.taskId == taskId) return@withTransaction
            if (active != null) {
                frameDao.upsert(pause(active))
            }
            val existing = frameDao.openFrameForTask(taskId)
            if (existing?.state == FrameState.AWAITING_RESUME.name) {
                error("Resume the waiting frame explicitly")
            }
            if (existing?.state == FrameState.PAUSED.name) {
                frameDao.upsert(
                    existing.copy(
                        state = FrameState.ACTIVE.name,
                        startedAtEpochMillis = clock.nowEpochMillis(),
                        pausedAtEpochMillis = null,
                    ),
                )
            } else {
                frameDao.upsert(newFrame(taskId, parentFrameId = null))
            }
            requireInvariants()
        }
        recordInteraction()
    }

    override suspend fun interrupt(title: String?): Task =
        database.withTransaction {
            val active = frameDao.getActive()
            if (active != null) {
                frameDao.upsert(pause(active))
            }
            val resolved = title?.trim()?.takeIf { it.isNotEmpty() } ?: UNTITLED_INTERRUPTION
            val task = newTask(resolved, TaskBucket.INBOX)
            taskDao.upsert(task)
            frameDao.upsert(newFrame(task.id, parentFrameId = active?.id))
            requireInvariants()
            task.toDomain()
        }.also { recordInteraction() }

    override suspend fun completeActive(): UndoToken? =
        database.withTransaction {
            val active = frameDao.getActive() ?: return@withTransaction null
            val task = taskDao.getById(active.taskId) ?: error("Missing task")
            val parent = active.parentFrameId?.let { frameDao.getById(it) }
            val token =
                UndoToken(
                    tasks = listOf(task.toDomain()),
                    frames = listOfNotNull(active.toDomain(), parent?.toDomain()),
                )
            taskDao.upsert(
                task.copy(
                    status = TaskStatus.DONE.name,
                    completedAtEpochMillis = clock.nowEpochMillis(),
                ),
            )
            frameDao.upsert(close(active))
            if (parent != null) {
                frameDao.upsert(parent.copy(state = FrameState.AWAITING_RESUME.name))
            }
            requireInvariants()
            token
        }.also { token ->
            if (token != null) recordInteraction()
        }

    override suspend fun resume(frameId: String) {
        database.withTransaction {
            val frame = frameDao.getById(frameId) ?: error("Missing frame")
            check(frame.state == FrameState.AWAITING_RESUME.name) {
                "Resume is only valid for the frame waiting to be picked up"
            }
            check(frameDao.getActive() == null) { "Another frame is already active" }
            frameDao.upsert(
                frame.copy(
                    state = FrameState.ACTIVE.name,
                    startedAtEpochMillis = clock.nowEpochMillis(),
                    pausedAtEpochMillis = null,
                ),
            )
            requireInvariants()
        }
        recordInteraction()
    }

    override suspend fun demote(taskId: String): UndoToken =
        database.withTransaction {
            val task = taskDao.getById(taskId) ?: error("Missing task")
            val openFrame = frameDao.openFrameForTask(taskId)
            val parent = openFrame?.parentFrameId?.let { frameDao.getById(it) }
            val frames =
                buildList {
                    if (openFrame != null) add(openFrame.toDomain())
                    if (openFrame?.state == FrameState.ACTIVE.name && parent != null) {
                        add(parent.toDomain())
                    }
                }
            val token = UndoToken(tasks = listOf(task.toDomain()), frames = frames)
            if (openFrame?.state == FrameState.ACTIVE.name) {
                frameDao.upsert(pause(openFrame))
                if (parent != null) {
                    frameDao.upsert(parent.copy(state = FrameState.AWAITING_RESUME.name))
                }
            } else if (openFrame?.state == FrameState.AWAITING_RESUME.name) {
                frameDao.upsert(
                    openFrame.copy(
                        state = FrameState.PAUSED.name,
                        pausedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
            }
            taskDao.upsert(task.copy(bucket = TaskBucket.LATER.name))
            requireInvariants()
            token
        }.also { recordInteraction() }

    override suspend fun pauseActive() {
        database.withTransaction {
            val active = frameDao.getActive() ?: return@withTransaction
            frameDao.upsert(pause(active))
            requireInvariants()
        }
        recordInteraction()
    }

    override suspend fun updateReturnCue(
        taskId: String,
        cue: String?,
    ) {
        database.withTransaction {
            val task = taskDao.getById(taskId) ?: error("Missing task")
            val trimmed = cue?.trim()?.ifEmpty { null }
            taskDao.upsert(task.copy(returnCue = trimmed))
        }
        recordInteraction()
    }

    override suspend fun rename(
        taskId: String,
        title: String,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            val task = taskDao.getById(taskId) ?: error("Missing task")
            taskDao.upsert(task.copy(title = trimmed))
        }
        recordInteraction()
    }

    override suspend fun undo(token: UndoToken) {
        database.withTransaction {
            token.tasks.forEach { taskDao.upsert(it.toEntity()) }
            token.frames.forEach { frameDao.upsert(it.toEntity()) }
            requireInvariants()
        }
        recordInteraction()
    }

    override suspend fun restore(taskId: String) {
        val restored =
            database.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction false
                if (task.status != TaskStatus.DONE.name) return@withTransaction false
                taskDao.upsert(
                    task.copy(
                        bucket = TaskBucket.INBOX.name,
                        status = TaskStatus.OPEN.name,
                        completedAtEpochMillis = null,
                        sortOrder = clock.nowEpochMillis(),
                    ),
                )
                requireInvariants()
                true
            }
        if (restored) recordInteraction()
    }

    override suspend fun completeTask(taskId: String): UndoToken? {
        if (frameDao.getActive()?.taskId == taskId) return completeActive()
        val token =
            database.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction null
                if (task.status != TaskStatus.OPEN.name) return@withTransaction null
                val frame = frameDao.openFrameForTask(taskId)
                if (frame != null && frameDao.countOpenChildren(frame.id) > 0) {
                    return@withTransaction null
                }
                val parent = frame?.parentFrameId?.let { frameDao.getById(it) }
                val surfaceParent = frame != null && parent != null
                val snapshot =
                    UndoToken(
                        tasks = listOf(task.toDomain()),
                        frames = listOfNotNull(frame?.toDomain(), if (surfaceParent) parent.toDomain() else null),
                    )
                taskDao.upsert(
                    task.copy(
                        status = TaskStatus.DONE.name,
                        completedAtEpochMillis = clock.nowEpochMillis(),
                    ),
                )
                if (frame != null) {
                    frameDao.upsert(close(frame))
                    if (surfaceParent) {
                        frameDao.upsert(parent.copy(state = FrameState.AWAITING_RESUME.name))
                    }
                }
                requireInvariants()
                snapshot
            } ?: return null
        recordInteraction()
        return token
    }

    override suspend fun reorderToday(orderedIds: List<String>): UndoToken? {
        val token =
            database.withTransaction {
                val today = taskDao.openInBucket(TaskBucket.TODAY.name)
                val currentIds = today.map { it.id }
                if (orderedIds.size != currentIds.size || orderedIds.toSet() != currentIds.toSet()) {
                    return@withTransaction null
                }
                if (orderedIds == currentIds) return@withTransaction null
                val snapshot = UndoToken(tasks = today.map { it.toDomain() }, frames = emptyList())
                orderedIds.forEachIndexed { index, id ->
                    val task = today.first { it.id == id }
                    taskDao.upsert(task.copy(sortOrder = index.toLong()))
                }
                snapshot
            } ?: return null
        recordInteraction()
        return token
    }

    override suspend fun setNudge(
        enabled: Boolean,
        minutes: Int,
    ) {
        preferencesStore.setNudge(enabled, minutes)
    }

    override suspend fun setQuietHours(
        start: String?,
        end: String?,
    ) {
        preferencesStore.setQuietHours(start, end)
    }

    override suspend fun setCurrentNotificationEnabled(enabled: Boolean) {
        preferencesStore.setCurrentNotificationEnabled(enabled)
    }

    override suspend fun recordNudge(taskId: String) {
        preferencesStore.setLastNudge(clock.nowEpochMillis(), taskId)
    }

    private suspend fun recordInteraction() {
        preferencesStore.setLastInteractionAt(clock.nowEpochMillis())
    }

    private suspend fun requireInvariants() {
        check(taskDao.countOpenToday() <= MAX_OPEN_TODAY) {
            "Today cannot hold more than 3 open tasks"
        }
        check(frameDao.countActive() <= 1) {
            "At most one frame can be active"
        }
    }

    private fun newTask(
        title: String,
        bucket: TaskBucket,
    ): TaskEntity {
        val now = clock.nowEpochMillis()
        return TaskEntity(
            id = ids(),
            title = title,
            bucket = bucket.name,
            status = TaskStatus.OPEN.name,
            returnCue = null,
            sortOrder = now,
            createdAtEpochMillis = now,
            completedAtEpochMillis = null,
        )
    }

    private fun newFrame(
        taskId: String,
        parentFrameId: String?,
    ): FocusFrameEntity {
        val now = clock.nowEpochMillis()
        return FocusFrameEntity(
            id = ids(),
            taskId = taskId,
            parentFrameId = parentFrameId,
            state = FrameState.ACTIVE.name,
            startedAtEpochMillis = now,
            pausedAtEpochMillis = null,
            accumulatedMillis = 0,
        )
    }

    private fun pause(frame: FocusFrameEntity): FocusFrameEntity {
        val now = clock.nowEpochMillis()
        val segment = (now - frame.startedAtEpochMillis).coerceAtLeast(0)
        return frame.copy(
            state = FrameState.PAUSED.name,
            pausedAtEpochMillis = now,
            accumulatedMillis = frame.accumulatedMillis + segment,
        )
    }

    private fun close(frame: FocusFrameEntity): FocusFrameEntity {
        val now = clock.nowEpochMillis()
        val segment =
            if (frame.state == FrameState.ACTIVE.name) {
                (now - frame.startedAtEpochMillis).coerceAtLeast(0)
            } else {
                0L
            }
        return frame.copy(
            state = FrameState.CLOSED.name,
            pausedAtEpochMillis = now,
            accumulatedMillis = frame.accumulatedMillis + segment,
        )
    }
}
