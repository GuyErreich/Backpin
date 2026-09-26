package app.backpin.focus.domain

/**
 * Where a task sits. Placement is independent of which frame is in focus.
 */
enum class TaskBucket {
    INBOX,
    TODAY,
    LATER,
}

/** Open tasks can be placed and focused. Done tasks stay in history. */
enum class TaskStatus {
    OPEN,
    DONE,
}

/**
 * A focus frame is the saved context for a task.
 * At most one frame is [ACTIVE] at a time.
 */
enum class FrameState {
    ACTIVE,
    PAUSED,
    AWAITING_RESUME,
    CLOSED,
}

/**
 * A single task. [returnCue] is the one-line note that should still make
 * sense after an interruption.
 */
data class Task(
    val id: String,
    val title: String,
    val bucket: TaskBucket,
    val status: TaskStatus,
    val returnCue: String?,
    val sortOrder: Long,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)

/**
 * One layer of focus. [parentFrameId] links an interruption to the frame it paused.
 */
data class FocusFrame(
    val id: String,
    val taskId: String,
    val parentFrameId: String?,
    val state: FrameState,
    val startedAtEpochMillis: Long,
    val pausedAtEpochMillis: Long?,
    val accumulatedMillis: Long,
)

/**
 * Preference shape kept on device. Nudges are inexact WorkManager work.
 * [lastInteractionAtEpochMillis] and [lastNudgedAtEpochMillis] keep a nudge
 * from repeating during recent activity.
 */
data class UserPreferences(
    val nudgesEnabled: Boolean = false,
    val nudgeMinutes: Int = 30,
    val quietStart: String? = null,
    val quietEnd: String? = null,
    val currentNotificationEnabled: Boolean = false,
    val lastInteractionAtEpochMillis: Long = 0L,
    val lastNudgedAtEpochMillis: Long = 0L,
    val lastNudgedTaskId: String? = null,
)

/** Point-in-time tasks and frames used to rebuild the screen after process death. */
data class FocusSnapshot(
    val tasks: List<Task> = emptyList(),
    val frames: List<FocusFrame> = emptyList(),
)

/**
 * Prior task and frame rows for an Undo of Done or Later.
 * Restoring these rows puts bucket and frame state back; nothing is hard-deleted.
 */
data class UndoToken(
    val tasks: List<Task>,
    val frames: List<FocusFrame>,
)

/** Result of trying to move a task into Today. */
sealed interface PromoteOutcome {
    data object Promoted : PromoteOutcome

    data class NeedsSwap(
        val todayTasks: List<Task>,
    ) : PromoteOutcome

    data class Rejected(
        val reason: String,
    ) : PromoteOutcome
}

/** Time source so elapsed segments and tests share one clock. */
fun interface AppClock {
    fun nowEpochMillis(): Long
}

object SystemAppClock : AppClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

const val UNTITLED_INTERRUPTION: String = "Untitled interruption"

const val MAX_OPEN_TODAY: Int = 3

/**
 * Elapsed focus time from stored timestamps, shown while the UI is open.
 * Hours and minutes match the calm home card; sub-minute time reads as 0m.
 */
fun formatElapsed(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val totalMinutes = safe / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Focus-screen elapsed copy. Names the measurement, including time under a minute.
 */
fun timeInFocusLabel(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    if (safe < 60_000L) return "Under a minute in focus"
    return "${formatElapsed(safe)} in focus"
}

/** Running total: stored accumulation plus the open segment when the frame is active. */
fun elapsedMillis(
    accumulatedMillis: Long,
    startedAtEpochMillis: Long,
    nowEpochMillis: Long,
    running: Boolean,
): Long {
    val segment = if (running) (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0) else 0L
    return accumulatedMillis + segment
}
