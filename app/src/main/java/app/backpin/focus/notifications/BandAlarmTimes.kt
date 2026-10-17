// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.notifications

import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.MINUTES_PER_DAY
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.bandWindow
import app.backpin.focus.domain.nextDueDayFor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class BandAlarmKind {
    START,
    FOLLOW_UP,
    END,
}

/** One band-level alarm. Never scheduled per repeat. */
data class BandAlarm(
    val band: RepeatBand,
    val kind: BandAlarmKind,
    val epochMillis: Long,
)

const val BAND_FOLLOW_UP_MINUTES: Int = 30

/**
 * Next start, follow-up, and end for each band that has an unarchived
 * reminder-on repeat. Times use [zone] so a DST spring-forward or
 * fall-back still lands on the user-set clock time.
 */
fun nextBandAlarms(
    nowEpochMillis: Long,
    zone: ZoneId,
    times: BandTimes,
    reminderRepeats: List<Repeat>,
): List<BandAlarm> {
    if (!times.isValid()) return emptyList()
    val active = reminderRepeats.filter { it.archivedAtEpochMillis == null && it.reminderOn }
    if (active.isEmpty()) return emptyList()
    return RepeatBand.entries.flatMap { band ->
        val inBand = active.filter { it.band == band }
        if (inBand.isEmpty()) return@flatMap emptyList()
        val window = bandWindow(band, times)
        val followUpMinute = window.endMinute - BAND_FOLLOW_UP_MINUTES
        buildList {
            nextAlarm(nowEpochMillis, zone, band, BandAlarmKind.START, window.startMinute, inBand)
                ?.let(::add)
            if (followUpMinute > window.startMinute) {
                nextAlarm(nowEpochMillis, zone, band, BandAlarmKind.FOLLOW_UP, followUpMinute, inBand)
                    ?.let(::add)
            }
            nextAlarm(nowEpochMillis, zone, band, BandAlarmKind.END, window.endMinute, inBand)
                ?.let(::add)
        }
    }
}

internal fun nextAlarm(
    nowEpochMillis: Long,
    zone: ZoneId,
    band: RepeatBand,
    kind: BandAlarmKind,
    minuteOfDay: Int,
    repeats: List<Repeat>,
): BandAlarm? {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate().toEpochDay()
    val todayMoment = localMomentEpochMillis(today, minuteOfDay, zone)
    if (todayMoment > nowEpochMillis && nextDueDayFor(repeats, today) == today) {
        return BandAlarm(band, kind, todayMoment)
    }
    val nextDay = nextDueDayFor(repeats, today + 1) ?: return null
    return BandAlarm(band, kind, localMomentEpochMillis(nextDay, minuteOfDay, zone))
}

/**
 * Epoch millis of a local clock time on [epochDay].
 * [minuteOfDay] of 1440 is midnight at the start of the next day.
 * A DST gap (spring forward) uses the zone's next valid instant.
 */
fun localMomentEpochMillis(
    epochDay: Long,
    minuteOfDay: Int,
    zone: ZoneId,
): Long {
    val date = LocalDate.ofEpochDay(epochDay)
    if (minuteOfDay >= MINUTES_PER_DAY) {
        return date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val clamped = minuteOfDay.coerceAtLeast(0)
    val time = LocalTime.of(clamped / 60, clamped % 60)
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
