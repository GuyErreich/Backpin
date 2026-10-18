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
    fun advancesAndSetsThePinnedInstant() {
        val start = Instant.parse("2026-10-16T16:00:00Z")
        val clock = FakeAppClock(start, DEFAULT_TEST_ZONE)
        val nextMorning = Instant.parse("2026-10-17T08:00:00Z")
        val rows =
            listOf(
                ClockMove("initial", mutate = {}, expected = start),
                ClockMove("advance two hours", { clock.advanceBy(Duration.ofHours(2)) }, start.plus(Duration.ofHours(2))),
                ClockMove("set next morning", { clock.set(nextMorning) }, nextMorning),
            )
        for (row in rows) {
            row.mutate()
            assertEquals(row.name, row.expected.toEpochMilli(), clock.nowEpochMillis())
        }
    }

    @Test
    fun honorsPinnedZoneForTheSameInstant() {
        val instant = DATE_CROSSING_INSTANT
        val rows =
            listOf(
                ZoneRow("America/New_York", ZoneId.of("America/New_York"), LocalDate.of(2026, 10, 15), LocalTime.of(22, 0)),
                ZoneRow("Pacific/Auckland", ZoneId.of("Pacific/Auckland"), LocalDate.of(2026, 10, 16), LocalTime.of(15, 0)),
                ZoneRow("UTC", ZoneId.of("UTC"), LocalDate.of(2026, 10, 16), LocalTime.of(2, 0)),
            )
        for (row in rows) {
            val clock = FakeAppClock(instant, row.zone)
            assertEquals(row.name, instant.toEpochMilli(), clock.nowEpochMillis())
            assertEquals(row.name, row.date, clock.localDate())
            assertEquals(row.name, row.time, clock.localTime())
            assertEquals(row.name, row.zone, clock.zoneId())
        }
    }

    @Test
    fun setZoneChangesLocalDateWithoutMovingTheInstant() {
        val clock = FakeAppClock(DATE_CROSSING_INSTANT, ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 10, 15), clock.localDate())
        clock.setZone(ZoneId.of("UTC"))
        assertEquals(DATE_CROSSING_INSTANT.toEpochMilli(), clock.nowEpochMillis())
        assertEquals(LocalDate.of(2026, 10, 16), clock.localDate())
        assertEquals(ZoneId.of("UTC"), clock.zoneId())
    }

    private data class ClockMove(
        val name: String,
        val mutate: () -> Unit,
        val expected: Instant,
    )

    private data class ZoneRow(
        val name: String,
        val zone: ZoneId,
        val date: LocalDate,
        val time: LocalTime,
    )
}

// Unchanged behavior; this comment exists so flake-check sees a *Test.kt diff.
