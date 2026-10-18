// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How Coming up writes the wait until a day.
 * [DAYS] is always a day count. [MONTHS] switches to months once the day is
 * a full month or more away.
 */
enum class CountdownUnit {
    DAYS,
    MONTHS,
}

/** Coming up groups, soonest first. Empty groups stay hidden. */
enum class ComingUpSection {
    TODAY,
    TOMORROW,
    THIS_WEEK,
    FURTHER_OUT,
}

/** Remaining wait shown on a Coming up row. */
sealed interface ComingUpRemaining {
    data object Passed : ComingUpRemaining

    data object Today : ComingUpRemaining

    data class Days(
        val count: Int,
    ) : ComingUpRemaining

    data class Months(
        val count: Int,
    ) : ComingUpRemaining
}

/**
 * Wait from [today] to [epochDay].
 * Months mode uses whole months only when [ChronoUnit.MONTHS] is at least 1.
 */
fun comingUpRemaining(
    epochDay: Long,
    today: LocalDate,
    unit: CountdownUnit,
): ComingUpRemaining {
    val target = LocalDate.ofEpochDay(epochDay)
    val days = ChronoUnit.DAYS.between(today, target)
    if (days < 0L) return ComingUpRemaining.Passed
    if (days == 0L) return ComingUpRemaining.Today
    if (unit == CountdownUnit.MONTHS) {
        val months = ChronoUnit.MONTHS.between(today, target)
        if (months >= 1L) return ComingUpRemaining.Months(months.toInt())
    }
    return ComingUpRemaining.Days(days.toInt())
}

/**
 * [daysLeft] is local days from today to the item's day.
 * Tomorrow is 1. This week is 2 through 7. Further out is 8 or more.
 * Today and overdue land in [ComingUpSection.TODAY].
 */
fun comingUpSection(daysLeft: Long): ComingUpSection = when {
    daysLeft <= 0L -> ComingUpSection.TODAY
    daysLeft == 1L -> ComingUpSection.TOMORROW
    daysLeft <= 7L -> ComingUpSection.THIS_WEEK
    else -> ComingUpSection.FURTHER_OUT
}
