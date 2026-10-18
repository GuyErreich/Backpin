// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import app.backpin.focus.domain.AppClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Default instant for [FakeAppClock]: 16:00 UTC, noon in [DEFAULT_TEST_ZONE]. */
val DEFAULT_TEST_INSTANT: Instant = Instant.parse("2026-10-16T16:00:00Z")

/** Explicit test zone. Never [ZoneId.systemDefault]. */
val DEFAULT_TEST_ZONE: ZoneId = ZoneId.of("America/New_York")

/**
 * Pinned [AppClock] for unit tests. Instant and zone are both explicit so
 * repository code and [app.backpin.focus.repeats.RepeatsService] can share one
 * fixture without drifting onto [ZoneId.systemDefault].
 *
 * [zone] is shaped for a future `AppZone` next to [AppClock]: adding that
 * interface to this class is a one-line change. Do not add `AppZone` here.
 */
class FakeAppClock(
    initial: Instant = DEFAULT_TEST_INSTANT,
    initialZone: ZoneId = DEFAULT_TEST_ZONE,
) : AppClock {
    @Volatile
    var instant: Instant = initial
        private set

    /**
     * Pinned zone. When `AppZone` lands, add it to this class and keep this
     * property (or [zoneId]) as the implementation.
     */
    @Volatile
    var zone: ZoneId = initialZone
        private set

    override fun nowEpochMillis(): Long = instant.toEpochMilli()

    /** Ready for `fun interface AppZone { fun zoneId(): ZoneId }`. */
    fun zoneId(): ZoneId = zone

    fun localDateTime(): LocalDateTime = instant.atZone(zone).toLocalDateTime()

    fun localDate(): LocalDate = localDateTime().toLocalDate()

    fun localTime(): LocalTime = localDateTime().toLocalTime()

    fun localEpochDay(): Long = localDate().toEpochDay()

    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }

    fun set(instant: Instant) {
        this.instant = instant
    }

    fun setZone(zone: ZoneId) {
        this.zone = zone
    }
}
