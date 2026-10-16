package app.backpin.focus.domain

/**
 * Where a task sits. Placement is independent of which frame is in focus.
 * [SCHEDULED] waits outside Inbox, Today, and Later until its local date.
 * [INTERRUPTION] is a nested task taken off those piles. It does not take a Today slot.
 * [COUNTDOWN] is an event on a local day, stored in [Task.scheduledOnEpochDay]. It does not
 * take a Today slot. While it has an open focus frame it has arrived and is off the Countdown list.
 */
enum class TaskBucket {
    INBOX,
    TODAY,
    LATER,
    SCHEDULED,
    INTERRUPTION,
    COUNTDOWN,
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
 * sense after an interruption. [scheduledAtMinuteOfDay] is an optional local
 * time on [scheduledOnEpochDay] for a scheduled task. Null means any time that day.
 * [countdownIntervalDays] is the last count used for this event, so resume can
 * offer it again. It is not a schedule field and survives a move off COUNTDOWN.
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
    val scheduledOnEpochDay: Long? = null,
    val scheduledAtMinuteOfDay: Int? = null,
    val countdownIntervalDays: Int? = null,
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
 * [todayLimit] is how many open tasks Today can hold. Empty places are not filled on their own.
 * [savedPhrases] is the capture list, in the order it is shown. Angle brackets mark one blank.
 * [lastCountdownDays] is the last count used on this phone, when an event has none of its own.
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
    val todayLimit: Int = DEFAULT_TODAY_LIMIT,
    val savedPhrases: List<String> = emptyList(),
    val lastCountdownDays: Int? = null,
)

/** Point-in-time tasks and frames used to rebuild the screen after process death. */
data class FocusSnapshot(
    val tasks: List<Task> = emptyList(),
    val frames: List<FocusFrame> = emptyList(),
)

/**
 * Prior task and frame rows for Undo of Today placement or a countdown resume.
 * Done and Later commit without this snackbar. Nothing is hard-deleted.
 */
data class UndoToken(
    val tasks: List<Task>,
    val frames: List<FocusFrame>,
    val createdTaskIds: List<String> = emptyList(),
)

/**
 * Result of trying to move a task into Today, or of starting one that needs a Today slot.
 * [NeedsSwap.canInterrupt] is true when an open Today task is the active frame, so the
 * full list can offer Interrupt as well as Replace.
 */
sealed interface PromoteOutcome {
    data object Promoted : PromoteOutcome

    data class NeedsSwap(
        val todayTasks: List<Task>,
        val canInterrupt: Boolean = false,
    ) : PromoteOutcome

    data class Rejected(
        val reason: String,
    ) : PromoteOutcome
}

/**
 * Where an arrived countdown goes when it leaves focus without being done.
 * [Countdown.epochDay] is the new event day.
 */
sealed interface DetachDestination {
    data object Today : DetachDestination

    data object Later : DetachDestination

    data class Countdown(
        val epochDay: Long,
    ) : DetachDestination
}

/** Time source so elapsed segments and tests share one clock. */
fun interface AppClock {
    fun nowEpochMillis(): Long
}

object SystemAppClock : AppClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

const val UNTITLED_INTERRUPTION: String = "Untitled interruption"

const val MIN_TODAY_LIMIT: Int = 1
const val MAX_TODAY_LIMIT: Int = 5
const val DEFAULT_TODAY_LIMIT: Int = 3

/** Default Today cap. The stored limit in preferences can be [MIN_TODAY_LIMIT] through [MAX_TODAY_LIMIT]. */
const val MAX_OPEN_TODAY: Int = DEFAULT_TODAY_LIMIT

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
