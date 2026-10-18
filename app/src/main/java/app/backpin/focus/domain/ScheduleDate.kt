// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A one-shot local date counted from today. Not a weekday name and not a series. */
enum class ScheduleOffset {
    TOMORROW,
    IN_7_DAYS,
}

/** Tomorrow, or seven local days out. */
fun scheduleDate(
    today: LocalDate,
    offset: ScheduleOffset,
): LocalDate =
    when (offset) {
        ScheduleOffset.TOMORROW -> today.plusDays(1)
        ScheduleOffset.IN_7_DAYS -> today.plusDays(7)
    }

/**
 * A scheduled task is due on an earlier local date, or on today once [minuteOfDay] has passed.
 * Without a time it is due any time on its date.
 */
fun isScheduledDue(
    epochDay: Long,
    minuteOfDay: Int?,
    now: LocalDateTime,
): Boolean {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = now.toLocalDate()
    if (date.isBefore(today)) return true
    if (date.isAfter(today)) return false
    return minuteOfDay == null || now.hour * 60 + now.minute >= minuteOfDay
}

/** `HH:mm` for a minute of the day. */
fun minuteOfDayLabel(minuteOfDay: Int): String {
    val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    return LocalTime.of(clamped / 60, clamped % 60).format(DateTimeFormatter.ofPattern("HH:mm"))
}

/** Same clock string Settings already shows. */
fun minuteOfDayClock(minute: Int): String = minuteOfDayLabel(minute)

/** Parses `HH:mm`. Anything else is null. */
fun parseClockToMinute(value: String): Int? {
    val time = runCatching { LocalTime.parse(value) }.getOrNull() ?: return null
    return time.hour * 60 + time.minute
}

/** "Thu, Oct 8", or "Thu, Oct 8 · 14:30" when a time is set. */
fun scheduleMomentLabel(
    epochDay: Long,
    minuteOfDay: Int?,
    locale: Locale = Locale.getDefault(),
): String {
    val formatted = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    return if (minuteOfDay == null) formatted else "$formatted · ${minuteOfDayLabel(minuteOfDay)}"
}

/** Snackbar after a countdown resume: days from today and the landing date. */
fun backInDaysLabel(
    days: Int,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    val landing = today.plusDays(days.toLong()).toEpochDay()
    return "Back in $days days · ${scheduleMomentLabel(landing, null, locale)}"
}

/** Date line for the Scheduled screen, prefixed with "Due" once [isScheduledDue] holds. */
fun scheduledWhenLabel(
    epochDay: Long,
    minuteOfDay: Int?,
    now: LocalDateTime,
    locale: Locale = Locale.getDefault(),
): String {
    val formatted = scheduleMomentLabel(epochDay, minuteOfDay, locale)
    return if (isScheduledDue(epochDay, minuteOfDay, now)) "Due · $formatted" else formatted
}

/** Epoch millis of a scheduled local moment, used to queue one inexact check at that time. */
fun scheduledMomentEpochMillis(
    epochDay: Long,
    minuteOfDay: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atTime(minuteOfDay / 60, minuteOfDay % 60)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

/** Local days from [today] to a countdown's event day. Negative once the day has passed. */
fun countdownDaysLeft(
    epochDay: Long,
    today: LocalDate,
): Long = epochDay - today.toEpochDay()

/** "Today", "Tomorrow", or "12 days". A day that has passed reads "Passed". */
fun countdownDayLabel(daysLeft: Long): String =
    when {
        daysLeft < 0L -> "Passed"
        daysLeft == 0L -> "Today"
        daysLeft == 1L -> "Tomorrow"
        else -> "$daysLeft days"
    }

/** A countdown arrives on its event day, or on the first check after it when that day was missed. */
fun isCountdownDue(
    epochDay: Long,
    today: LocalDate,
): Boolean = !LocalDate.ofEpochDay(epochDay).isAfter(today)

/** Days from today a countdown may be set. Typed and stored counts stay in this range. */
const val MIN_COUNTDOWN_DAYS: Int = 1

const val MAX_COUNTDOWN_DAYS: Int = 999

/**
 * Days from [today] to [epochDay], when that gap is a countdown that can be reused.
 * Today, the past, and more than [MAX_COUNTDOWN_DAYS] return null.
 */
fun countdownIntervalDays(
    epochDay: Long,
    today: LocalDate,
): Int? {
    val days = epochDay - today.toEpochDay()
    return days.toInt().takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS }
}

/**
 * The count the resume sheet offers first: the event's own interval, then the last
 * count used on this phone. Either value is ignored when it is outside the allowed range.
 */
fun suggestedCountdownDays(
    taskInterval: Int?,
    lastCountdownDays: Int?,
): Int? =
    taskInterval?.takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS }
        ?: lastCountdownDays?.takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS }

/** Days from today a countdown resume may land. */
const val MAX_COUNTDOWN_REARM_DAYS: Int = 365

/**
 * True when [epochDay] is tomorrow through [today] plus [MAX_COUNTDOWN_REARM_DAYS].
 * [FocusRepository.detachInterruption] rejects any other countdown day without writing.
 */
fun isCountdownRearmDay(
    epochDay: Long,
    today: LocalDate,
): Boolean {
    val days = epochDay - today.toEpochDay()
    return days in MIN_COUNTDOWN_DAYS.toLong()..MAX_COUNTDOWN_REARM_DAYS.toLong()
}

const val MINUTES_PER_DAY: Int = 24 * 60
