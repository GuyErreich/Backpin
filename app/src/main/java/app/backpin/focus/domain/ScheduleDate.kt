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
    IN_A_COUPLE_OF_DAYS,
    NEXT_WEEK,
}

/** Tomorrow, two local days out, or seven local days out. */
fun scheduleDate(
    today: LocalDate,
    offset: ScheduleOffset,
): LocalDate =
    when (offset) {
        ScheduleOffset.TOMORROW -> today.plusDays(1)
        ScheduleOffset.IN_A_COUPLE_OF_DAYS -> today.plusDays(2)
        ScheduleOffset.NEXT_WEEK -> today.plusDays(7)
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

/** "Thu, Oct 8", or "Thu, Oct 8 · 14:30" when a time is set. */
fun scheduleMomentLabel(
    epochDay: Long,
    minuteOfDay: Int?,
    locale: Locale = Locale.getDefault(),
): String {
    val formatted = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    return if (minuteOfDay == null) formatted else "$formatted · ${minuteOfDayLabel(minuteOfDay)}"
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

const val MINUTES_PER_DAY: Int = 24 * 60
