package app.backpin.focus.notifications

import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.label

/**
 * Grouped band summary, matching the approved Figma frames:
 * `Morning · 3 things`. One thing uses the singular.
 */
fun bandSummaryTitle(
    band: RepeatBand,
    count: Int,
): String {
    val noun = if (count == 1) "thing" else "things"
    return "${band.label()} · $count $noun"
}

/** Quiet child body: daily is `Every day`, otherwise `Every N days`. */
fun bandChildText(everyNDays: Int): String {
    val step = everyNDays.coerceAtLeast(1)
    return if (step == 1) "Every day" else "Every $step days"
}

fun bandGroupKey(band: RepeatBand): String = "repeats_${band.name}"

fun repeatSummaryNotificationId(band: RepeatBand): Int =
    when (band) {
        RepeatBand.MORNING -> NotificationContract.ID_REPEAT_SUMMARY_MORNING
        RepeatBand.NOON -> NotificationContract.ID_REPEAT_SUMMARY_NOON
        RepeatBand.NIGHT -> NotificationContract.ID_REPEAT_SUMMARY_NIGHT
    }

fun repeatChildNotificationId(
    repeatId: String,
    epochDay: Long,
): Int {
    val hashed = 31 * repeatId.hashCode() + epochDay.hashCode()
    return NotificationContract.ID_REPEAT_CHILD_BASE + (hashed and 0x7fff)
}
