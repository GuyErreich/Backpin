// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.isQuietNow
import java.time.LocalTime

sealed interface BandNudgeDecision {
    data object Post : BandNudgeDecision

    data object Hold : BandNudgeDecision

    data class Skip(
        val reason: String,
    ) : BandNudgeDecision
}

/**
 * Whether a band start should post, hold until focus ends, or stay quiet.
 * Follow-up uses [decideBandFollowUp] so a running focus does not store another hold.
 */
fun decideBandNudge(
    openReminders: List<RepeatOccurrence>,
    focusActive: Boolean,
    notificationsAllowed: Boolean,
    quietStart: String?,
    quietEnd: String?,
    time: LocalTime,
): BandNudgeDecision {
    if (openReminders.none { it.status == RepeatDayStatus.OPEN && it.repeat.reminderOn }) {
        return BandNudgeDecision.Skip("nothing-open")
    }
    if (!notificationsAllowed) return BandNudgeDecision.Skip("notifications-blocked")
    if (isQuietNow(quietStart, quietEnd, time)) return BandNudgeDecision.Skip("quiet-hours")
    if (focusActive) return BandNudgeDecision.Hold
    return BandNudgeDecision.Post
}

/**
 * Quiet follow-up near band end. Posts only when items remain open and no
 * focus is running. Never holds; the start alarm already did that.
 */
fun decideBandFollowUp(
    openReminders: List<RepeatOccurrence>,
    focusActive: Boolean,
    notificationsAllowed: Boolean,
    quietStart: String?,
    quietEnd: String?,
    time: LocalTime,
    alreadyPosted: Boolean,
): BandNudgeDecision {
    if (alreadyPosted) return BandNudgeDecision.Skip("follow-up-already-posted")
    if (focusActive) return BandNudgeDecision.Skip("focus-active")
    return decideBandNudge(
        openReminders = openReminders,
        focusActive = false,
        notificationsAllowed = notificationsAllowed,
        quietStart = quietStart,
        quietEnd = quietEnd,
        time = time,
    )
}

/** Open due items that still want a reminder. Done and missed stay out. */
fun openReminderOccurrences(items: List<RepeatOccurrence>): List<RepeatOccurrence> =
    items.filter { it.status == RepeatDayStatus.OPEN && it.repeat.reminderOn }
