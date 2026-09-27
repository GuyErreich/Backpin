package app.backpin.focus.data

import androidx.room.withTransaction
import app.backpin.focus.domain.AppClock
import app.backpin.focus.domain.FocusRepository
import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
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
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

    override suspend fun captureIntoToday(title: String): PromoteOutcome {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return PromoteOutcome.Rejected("Blank title")
        val limit = todayLimit()
        val outcome =
            database.withTransaction {
                if (taskDao.countOpenToday() >= limit) {
                    val today = taskDao.openInBucket(TaskBucket.TODAY.name).map { it.toDomain() }
                    return@withTransaction PromoteOutcome.NeedsSwap(today, canInterruptIncoming(""))
                }
                taskDao.upsert(newTask(trimmed, TaskBucket.TODAY))
                requireInvariants(limit)
                PromoteOutcome.Promoted
            }
        if (outcome is PromoteOutcome.Promoted) recordInteraction()
        return outcome
    }

    override suspend fun captureAndStart(title: String): PromoteOutcome {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return PromoteOutcome.Rejected("Blank title")
        val limit = todayLimit()
        val outcome =
            database.withTransaction {
                if (taskDao.countOpenToday() >= limit) {
                    val today = taskDao.openInBucket(TaskBucket.TODAY.name).map { it.toDomain() }
                    return@withTransaction PromoteOutcome.NeedsSwap(today, canInterruptIncoming(""))
                }
                val active = frameDao.getActive()
                val task = newTask(trimmed, TaskBucket.TODAY)
                taskDao.upsert(task)
                if (active != null) {
                    frameDao.upsert(pause(active))
                }
                frameDao.upsert(newFrame(task.id, parentFrameId = null))
                requireInvariants(limit)
                PromoteOutcome.Promoted
            }
        if (outcome is PromoteOutcome.Promoted) recordInteraction()
        return outcome
    }

    override suspend fun captureLater(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            taskDao.upsert(newTask(trimmed, TaskBucket.LATER))
        }
        recordInteraction()
    }

    override suspend fun captureScheduled(
        title: String,
        scheduledOnEpochDay: Long,
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            taskDao.upsert(newTask(trimmed, TaskBucket.SCHEDULED, scheduledOnEpochDay))
        }
        recordInteraction()
    }

    override suspend fun captureReplacing(
        title: String,
        outgoingTaskId: String,
    ): UndoToken {
        val trimmed = title.trim()
        check(trimmed.isNotEmpty()) { "Blank title" }
        val limit = todayLimit()
        val token =
            database.withTransaction {
                val outgoing = taskDao.getById(outgoingTaskId) ?: error("Missing outgoing task")
                val valid =
                    outgoing.bucket == TaskBucket.TODAY.name &&
                        outgoing.status == TaskStatus.OPEN.name
                if (!valid) error("Swap rejected")
                val slot = outgoing.sortOrder
                val parked = parkInLater(outgoing.id)
                val created = newTask(trimmed, TaskBucket.TODAY).copy(sortOrder = slot)
                taskDao.upsert(created)
                if (taskDao.countOpenToday() > limit) error("Swap rejected")
                requireInvariants(limit)
                UndoToken(
                    tasks = parked.tasks,
                    frames = parked.frames,
                    createdTaskIds = listOf(created.id),
                )
            }
        recordInteraction()
        return token
    }

    override suspend fun interruptWithNew(title: String) {
        val resolved = title.trim().ifEmpty { UNTITLED_INTERRUPTION }
        val limit = todayLimit()
        database.withTransaction {
            val active = frameDao.getActive() ?: error("Nothing is active")
            val activeTask = taskDao.getById(active.taskId) ?: error("Missing task")
            check(activeTask.status == TaskStatus.OPEN.name) { "Active task is done" }
            check(activeTask.bucket == TaskBucket.TODAY.name) { "Current task is not in Today" }
            val slot = activeTask.sortOrder
            frameDao.upsert(pause(active))
            taskDao.upsert(activeTask.movedTo(TaskBucket.LATER))
            val created = newTask(resolved, TaskBucket.TODAY).copy(sortOrder = slot)
            taskDao.upsert(created)
            frameDao.upsert(newFrame(created.id, parentFrameId = active.id))
            requireInvariants(limit)
        }
        recordInteraction()
    }

    override suspend fun moveToInbox(taskId: String) {
        val moved =
            database.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction false
                if (task.bucket != TaskBucket.SCHEDULED.name || task.status != TaskStatus.OPEN.name) {
                    return@withTransaction false
                }
                taskDao.upsert(task.movedTo(TaskBucket.INBOX, clock.nowEpochMillis()))
                requireInvariants(todayLimit())
                true
            }
        if (moved) recordInteraction()
    }

    override suspend fun removeScheduled(taskId: String) {
        val removed =
            database.withTransaction {
                val task = taskDao.getById(taskId) ?: return@withTransaction false
                if (task.bucket != TaskBucket.SCHEDULED.name) return@withTransaction false
                if (frameDao.getActive()?.taskId == taskId) return@withTransaction false
                frameDao.deleteForTask(taskId)
                taskDao.deleteById(taskId)
                true
            }
        if (removed) recordInteraction()
    }

    override suspend fun releaseDueScheduled() {
        val limit = todayLimit()
        val moved =
            database.withTransaction {
                val before = taskDao.countOpenToday()
                releaseDue(limit)
                val after = taskDao.countOpenToday()
                requireInvariants(limit)
                after - before
            }
        if (moved > 0) recordInteraction()
    }

    override suspend fun promote(taskId: String): PromoteOutcome {
        val limit = todayLimit()
        return database.withTransaction {
            val task = taskDao.getById(taskId) ?: return@withTransaction PromoteOutcome.Rejected("Missing task")
            if (task.status != TaskStatus.OPEN.name) {
                return@withTransaction PromoteOutcome.Rejected("Done tasks stay out of Today")
            }
            if (task.bucket == TaskBucket.TODAY.name) {
                return@withTransaction PromoteOutcome.Promoted
            }
            if (taskDao.countOpenToday() >= limit) {
                val today = taskDao.openInBucket(TaskBucket.TODAY.name).map { it.toDomain() }
                return@withTransaction PromoteOutcome.NeedsSwap(today, canInterruptIncoming(taskId))
            }
            taskDao.upsert(task.movedTo(TaskBucket.TODAY, clock.nowEpochMillis()))
            requireInvariants(limit)
            PromoteOutcome.Promoted
        }.also { outcome ->
            if (outcome is PromoteOutcome.Promoted) recordInteraction()
        }
    }

    override suspend fun swapIntoToday(
        incomingTaskId: String,
        outgoingTaskId: String,
    ): UndoToken {
        val limit = todayLimit()
        val token =
            database.withTransaction {
                val incoming = taskDao.getById(incomingTaskId) ?: error("Missing incoming task")
                val outgoing = taskDao.getById(outgoingTaskId) ?: error("Missing outgoing task")
                val valid =
                    incoming.status == TaskStatus.OPEN.name &&
                        incoming.bucket != TaskBucket.TODAY.name &&
                        outgoing.bucket == TaskBucket.TODAY.name &&
                        outgoing.status == TaskStatus.OPEN.name &&
                        incoming.id != outgoing.id
                if (!valid) error("Swap rejected")
                val slot = outgoing.sortOrder
                val parked = parkInLater(outgoing.id)
                val placed = taskDao.getById(incomingTaskId) ?: error("Missing incoming task")
                taskDao.upsert(placed.movedTo(TaskBucket.TODAY, slot))
                if (taskDao.countOpenToday() > limit) error("Swap rejected")
                requireInvariants(limit)
                UndoToken(
                    tasks = listOf(incoming.toDomain()) + parked.tasks,
                    frames = parked.frames,
                )
            }
        recordInteraction()
        return token
    }

    override suspend fun start(taskId: String): PromoteOutcome {
        val limit = todayLimit()
        val outcome =
            database.withTransaction {
                val task = taskDao.getById(taskId) ?: error("Missing task")
                check(task.status == TaskStatus.OPEN.name) { "Cannot start a done task" }
                val active = frameDao.getActive()
                if (active?.taskId == taskId) return@withTransaction PromoteOutcome.Promoted
                val existing = frameDao.openFrameForTask(taskId)
                if (existing?.state == FrameState.AWAITING_RESUME.name) {
                    return@withTransaction PromoteOutcome.Rejected("Resume the waiting frame explicitly")
                }
                val needsSlot = task.bucket != TaskBucket.TODAY.name
                if (needsSlot && taskDao.countOpenToday() >= limit) {
                    val today = taskDao.openInBucket(TaskBucket.TODAY.name).map { it.toDomain() }
                    return@withTransaction PromoteOutcome.NeedsSwap(today, canInterruptIncoming(taskId))
                }
                if (needsSlot) {
                    taskDao.upsert(task.movedTo(TaskBucket.TODAY, clock.nowEpochMillis()))
                }
                if (active != null) {
                    frameDao.upsert(pause(active))
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
                requireInvariants(limit)
                PromoteOutcome.Promoted
            }
        if (outcome is PromoteOutcome.Promoted) recordInteraction()
        return outcome
    }

    override suspend fun interruptWith(taskId: String) {
        val limit = todayLimit()
        database.withTransaction {
            val incoming = taskDao.getById(taskId) ?: error("Missing task")
            check(incoming.status == TaskStatus.OPEN.name) { "Cannot interrupt with a done task" }
            val active = frameDao.getActive() ?: error("Nothing is active")
            check(active.taskId != taskId) { "Task is already active" }
            val activeTask = taskDao.getById(active.taskId) ?: error("Missing task")
            check(activeTask.status == TaskStatus.OPEN.name) { "Active task is done" }
            check(activeTask.bucket == TaskBucket.TODAY.name) { "Current task is not in Today" }
            val existing = frameDao.openFrameForTask(taskId)
            if (existing?.state == FrameState.AWAITING_RESUME.name) {
                error("Resume the waiting frame explicitly")
            }
            if (existing != null && frameDao.countOpenChildren(existing.id) > 0) {
                error("Task still has an open interruption")
            }
            val staysInToday = incoming.bucket == TaskBucket.TODAY.name
            frameDao.upsert(pause(active))
            if (!staysInToday) {
                val slot = activeTask.sortOrder
                taskDao.upsert(activeTask.movedTo(TaskBucket.LATER))
                taskDao.upsert(incoming.movedTo(TaskBucket.TODAY, slot))
            }
            activateAsChild(existing, taskId, parentFrameId = active.id)
            requireInvariants(limit)
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
            requireInvariants(todayLimit())
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
            releaseDue(todayLimit())
            requireInvariants(todayLimit())
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
            requireInvariants(todayLimit())
        }
        recordInteraction()
    }

    override suspend fun demote(taskId: String): UndoToken {
        val limit = todayLimit()
        return database.withTransaction {
            val token = parkInLater(taskId)
            releaseDue(limit)
            requireInvariants(limit)
            token
        }.also { recordInteraction() }
    }

    override suspend fun pauseActive() {
        database.withTransaction {
            val active = frameDao.getActive() ?: return@withTransaction
            frameDao.upsert(pause(active))
            requireInvariants(todayLimit())
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
            token.createdTaskIds.forEach { id ->
                frameDao.deleteForTask(id)
                taskDao.deleteById(id)
            }
            token.tasks.forEach { taskDao.upsert(it.toEntity()) }
            token.frames.forEach { frameDao.upsert(it.toEntity()) }
            val heldScheduled =
                token.tasks
                    .filter { it.bucket == TaskBucket.SCHEDULED }
                    .map { it.id }
                    .toSet()
            releaseDue(todayLimit(), heldScheduled)
            requireInvariants(todayLimit())
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
                        scheduledOnEpochDay = null,
                    ),
                )
                requireInvariants(todayLimit())
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
                releaseDue(todayLimit())
                requireInvariants(todayLimit())
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

    override suspend fun setTodayLimit(limit: Int) {
        val clamped = limit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
        database.withTransaction {
            var guard = 0
            while (taskDao.countOpenToday() > clamped && guard < MAX_TODAY_LIMIT + 1) {
                guard += 1
                val open = taskDao.openInBucket(TaskBucket.TODAY.name)
                if (open.isEmpty()) break
                val activeId = frameDao.getActive()?.taskId
                val victim = open.lastOrNull { it.id != activeId } ?: open.last()
                parkInLater(victim.id)
            }
            releaseDue(clamped)
            check(taskDao.countOpenToday() <= clamped) {
                "Today cannot hold more than $clamped open tasks"
            }
            check(frameDao.countActive() <= 1) {
                "At most one frame can be active"
            }
        }
        preferencesStore.setTodayLimit(clamped)
        recordInteraction()
    }

    override suspend fun setSavedPhrases(phrases: List<String>) {
        preferencesStore.setSavedPhrases(phrases)
    }

    override suspend fun recordNudge(taskId: String) {
        preferencesStore.setLastNudge(clock.nowEpochMillis(), taskId)
    }

    private suspend fun recordInteraction() {
        preferencesStore.setLastInteractionAt(clock.nowEpochMillis())
    }

    private suspend fun todayLimit(): Int =
        preferencesStore.preferences.first().todayLimit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)

    private suspend fun canInterruptIncoming(incomingId: String): Boolean {
        val active = frameDao.getActive() ?: return false
        if (active.taskId == incomingId) return false
        val task = taskDao.getById(active.taskId) ?: return false
        return task.status == TaskStatus.OPEN.name && task.bucket == TaskBucket.TODAY.name
    }

    /**
     * Moves [taskId] to Later and parks its open frame the same way [demote] does.
     * Does not check the Today cap; callers do that after the surrounding writes.
     */
    private suspend fun parkInLater(taskId: String): UndoToken {
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
        taskDao.upsert(task.movedTo(TaskBucket.LATER))
        check(frameDao.countActive() <= 1) {
            "At most one frame can be active"
        }
        return token
    }

    private suspend fun requireInvariants(limit: Int) {
        check(taskDao.countOpenToday() <= limit) {
            "Today cannot hold more than $limit open tasks"
        }
        check(frameDao.countActive() <= 1) {
            "At most one frame can be active"
        }
    }

    private fun newTask(
        title: String,
        bucket: TaskBucket,
        scheduledOnEpochDay: Long? = null,
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
            scheduledOnEpochDay = scheduledOnEpochDay,
        )
    }

    private fun TaskEntity.movedTo(
        bucket: TaskBucket,
        sortOrder: Long = this.sortOrder,
    ): TaskEntity =
        copy(
            bucket = bucket.name,
            sortOrder = sortOrder,
            scheduledOnEpochDay = if (bucket == TaskBucket.SCHEDULED) scheduledOnEpochDay else null,
        )

    private fun localToday(): LocalDate =
        Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Fills free Today slots with due scheduled tasks, oldest date first.
     * Does not start them and does not move a Today task aside to make room.
     */
    private suspend fun releaseDue(
        limit: Int,
        exceptIds: Set<String> = emptySet(),
    ) {
        val today = localToday()
        val due =
            taskDao
                .openInBucket(TaskBucket.SCHEDULED.name)
                .filter { entity ->
                    if (entity.id in exceptIds) return@filter false
                    val day = entity.scheduledOnEpochDay ?: return@filter false
                    !LocalDate.ofEpochDay(day).isAfter(today)
                }.sortedWith(compareBy({ it.scheduledOnEpochDay }, { it.sortOrder }, { it.id }))
        for (entity in due) {
            if (taskDao.countOpenToday() >= limit) return
            taskDao.upsert(
                entity.copy(
                    bucket = TaskBucket.TODAY.name,
                    sortOrder = clock.nowEpochMillis(),
                    scheduledOnEpochDay = null,
                ),
            )
        }
    }

    /**
     * Makes [taskId] the active child of [parentFrameId].
     * A paused frame is reused. Otherwise a new frame is opened.
     */
    private suspend fun activateAsChild(
        existing: FocusFrameEntity?,
        taskId: String,
        parentFrameId: String,
    ) {
        if (existing?.state == FrameState.PAUSED.name) {
            frameDao.upsert(
                existing.copy(
                    state = FrameState.ACTIVE.name,
                    parentFrameId = parentFrameId,
                    startedAtEpochMillis = clock.nowEpochMillis(),
                    pausedAtEpochMillis = null,
                ),
            )
        } else {
            frameDao.upsert(newFrame(taskId, parentFrameId = parentFrameId))
        }
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
