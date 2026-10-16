package app.backpin.focus.ui.repeat

import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.TodaysRepeats
import app.backpin.focus.domain.label
import java.time.LocalTime

internal fun todaysRepeatList(todays: TodaysRepeats): List<RepeatOccurrence> =
    todays.morning + todays.noon + todays.night

/**
 * Open rows stay in band order. Missed stays in place below those.
 * Checked rows drop to the bottom, keeping their relative order.
 */
internal fun orderedRepeatRows(todays: TodaysRepeats): List<RepeatOccurrence> {
    val all = todaysRepeatList(todays)
    return all.filter { it.status == RepeatDayStatus.OPEN } +
        all.filter { it.status == RepeatDayStatus.MISSED } +
        all.filter { it.status == RepeatDayStatus.DONE }
}

internal fun repeatsLeftLabel(rows: List<RepeatOccurrence>): String {
    if (rows.isEmpty()) return ""
    val left = rows.count { it.status == RepeatDayStatus.OPEN }
    return "$left of ${rows.size} left"
}

internal fun repeatBandMark(band: RepeatBand): String = band.name

internal fun everyNDaysLabel(everyNDays: Int): String? =
    if (everyNDays <= 1) null else "every $everyNDays days"

internal fun missedRepeatCopy(band: RepeatBand): String =
    "Missed at ${band.label().lowercase()} · muted until end of day"

internal fun repeatRowDescription(occurrence: RepeatOccurrence): String {
    val interval = everyNDaysLabel(occurrence.repeat.everyNDays)?.let { ". $it" }.orEmpty()
    val title = occurrence.repeat.title
    val band = occurrence.repeat.band.label()
    return when (occurrence.status) {
        RepeatDayStatus.OPEN -> "$title. $band$interval. Check off."
        RepeatDayStatus.DONE -> "$title. $band$interval. Done."
        RepeatDayStatus.MISSED -> "$title. ${missedRepeatCopy(occurrence.repeat.band)}"
    }
}

internal fun minuteOfDayClock(minute: Int): String {
    val clamped = minute.coerceIn(0, MINUTES_PER_DAY - 1)
    return "%02d:%02d".format(clamped / 60, clamped % 60)
}

internal fun parseClockToMinute(value: String): Int? {
    val time = runCatching { LocalTime.parse(value) }.getOrNull() ?: return null
    return time.hour * 60 + time.minute
}

private const val MINUTES_PER_DAY: Int = 24 * 60
