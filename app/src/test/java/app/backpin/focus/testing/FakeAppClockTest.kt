// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.testing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FakeAppClockTest {
    @Test
    fun advancesPinnedInstant() {
        val clock =
            FakeAppClock(
                initial = Instant.parse("2026-10-16T16:00:00Z"),
                initialZone = ZoneId.of("America/New_York"),
            )
        assertEquals(Instant.parse("2026-10-16T16:00:00Z"), clock.instant)
        clock.advanceBy(Duration.ofHours(2))
        assertEquals(Instant.parse("2026-10-16T18:00:00Z"), clock.instant)
        val next = Instant.parse("2026-10-17T08:00:00Z")
        clock.set(next)
        assertEquals(next, clock.instant)
        assertEquals(next.toEpochMilli(), clock.nowEpochMillis())
    }

    @Test
    fun honorsPinnedZoneNotSystemDefault() {
        val instant = Instant.parse("2026-10-16T02:00:00Z")
        val newYork = FakeAppClock(instant, ZoneId.of("America/New_York"))
        val auckland = FakeAppClock(instant, ZoneId.of("Pacific/Auckland"))
        assertEquals(instant.toEpochMilli(), newYork.nowEpochMillis())
        assertEquals(newYork.nowEpochMillis(), auckland.nowEpochMillis())
        assertEquals(LocalDate.of(2026, 10, 15), newYork.localDate())
        assertEquals(LocalTime.of(22, 0), newYork.localTime())
        assertEquals(LocalDate.of(2026, 10, 16), auckland.localDate())
        assertEquals(LocalTime.of(15, 0), auckland.localTime())
        assertEquals(ZoneId.of("America/New_York"), newYork.zone)
        newYork.setZone(ZoneId.of("UTC"))
        assertEquals(ZoneId.of("UTC"), newYork.zoneId())
        assertEquals(LocalDate.of(2026, 10, 16), newYork.localDate())
        assertEquals(instant.toEpochMilli(), newYork.nowEpochMillis())
    }
}
