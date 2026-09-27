package app.backpin.focus.domain

import java.time.LocalDate
import java.time.YearMonth
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
 * The next local date strictly after [today] whose day-of-month is [day].
 * A month that does not contain [day] is skipped, so the 31st never clamps to a short month.
 * One date, not a repeating reminder.
 */
fun nextDayOfMonth(
    today: LocalDate,
    day: Int,
): LocalDate {
    require(day in 1..31) { "Day of the month is 1 through 31" }
    var month = YearMonth.from(today)
    if (month.lengthOfMonth() >= day) {
        val candidate = month.atDay(day)
        if (candidate.isAfter(today)) return candidate
    }
    repeat(12) {
        month = month.plusMonths(1)
        if (month.lengthOfMonth() >= day) return month.atDay(day)
    }
    error("No month contains day $day")
}

/** Date line for the Scheduled screen. Due when that local date is today or earlier. */
fun scheduledWhenLabel(
    epochDay: Long,
    today: LocalDate,
    locale: Locale = Locale.getDefault(),
): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val formatted = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    return if (!date.isAfter(today)) "Due · $formatted" else formatted
}
