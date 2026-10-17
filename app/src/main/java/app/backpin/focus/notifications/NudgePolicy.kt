// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import app.backpin.focus.domain.FocusSnapshot
import app.backpin.focus.domain.FrameState
import app.backpin.focus.domain.TaskStatus
import app.backpin.focus.domain.UserPreferences
import app.backpin.focus.domain.isQuietNow
import java.time.LocalTime

/** The task a nudge or the current-task notification can talk about. */
data class FocusTarget(
    val taskId: String,
    val title: String,
    val returnCue: String?,
    val recovery: Boolean,
)

/** Active frame, otherwise the open frame waiting for an explicit resume. */
fun relevantFocus(snapshot: FocusSnapshot): FocusTarget? {
    val tasks = snapshot.tasks.associateBy { it.id }
    val active = snapshot.frames.firstOrNull { it.state == FrameState.ACTIVE }
    if (active != null) {
        val task = tasks[active.taskId] ?: return null
        if (task.status != TaskStatus.OPEN) return null
        return FocusTarget(task.id, task.title, task.returnCue, recovery = false)
    }
    val awaiting =
        snapshot.frames
            .filter { it.state == FrameState.AWAITING_RESUME }
            .maxByOrNull { it.startedAtEpochMillis }
            ?: return null
    val task = tasks[awaiting.taskId] ?: return null
    if (task.status != TaskStatus.OPEN) return null
    return FocusTarget(task.id, task.title, task.returnCue, recovery = true)
}

sealed interface NudgeDecision {
    data class Post(
        val taskId: String,
        val title: String,
    ) : NudgeDecision

    data class Skip(
        val reason: String,
    ) : NudgeDecision
}

/**
 * Whether this worker run should post. Timing is still inexact; this only
 * refuses work that would be wrong if it did run.
 */
fun decideNudge(
    nowEpochMillis: Long,
    time: LocalTime,
    appVisible: Boolean,
    notificationsAllowed: Boolean,
    preferences: UserPreferences,
    target: FocusTarget?,
): NudgeDecision {
    if (!notificationsAllowed) return NudgeDecision.Skip("notifications-blocked")
    if (!preferences.nudgesEnabled) return NudgeDecision.Skip("nudges-off")
    if (target == null) return NudgeDecision.Skip("nothing-in-focus")
    if (appVisible) return NudgeDecision.Skip("app-visible")
    if (isQuietNow(preferences.quietStart, preferences.quietEnd, time)) {
        return NudgeDecision.Skip("quiet-hours")
    }
    val windowMillis = preferences.nudgeMinutes.coerceAtLeast(1) * 60_000L
    val interactionAt = preferences.lastInteractionAtEpochMillis
    if (interactionAt > 0L && nowEpochMillis - interactionAt < windowMillis) {
        return NudgeDecision.Skip("recent-activity")
    }
    val nudgedAt = preferences.lastNudgedAtEpochMillis
    if (
        preferences.lastNudgedTaskId == target.taskId &&
        nudgedAt > 0L &&
        nowEpochMillis - nudgedAt < windowMillis
    ) {
        return NudgeDecision.Skip("recent-nudge")
    }
    return NudgeDecision.Post(taskId = target.taskId, title = target.title)
}

/** Unique periodic work exists only while nudges are on, allowed, and there is a frame to mention. */
fun shouldEnqueueNudges(
    enabled: Boolean,
    notificationsAllowed: Boolean,
    hasRelevantTask: Boolean,
): Boolean = enabled && notificationsAllowed && hasRelevantTask
