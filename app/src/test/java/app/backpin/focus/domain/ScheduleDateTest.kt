package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class ScheduleDateTest {
    private val today: LocalDate = LocalDate.of(2026, 1, 15)

    @Test
    fun offsetsAreLocalDaysFromToday() {
        assertEquals(LocalDate.of(2026, 1, 16), scheduleDate(today, ScheduleOffset.TOMORROW))
        assertEquals(LocalDate.of(2026, 1, 22), scheduleDate(today, ScheduleOffset.IN_7_DAYS))
    }

    @Test
    fun theQuickDaysAreTomorrowAndInSevenDaysOnly() {
        assertEquals(listOf(ScheduleOffset.TOMORROW, ScheduleOffset.IN_7_DAYS), ScheduleOffset.entries)
    }

    @Test
    fun offsetsCrossAMonthBoundary() {
        val late = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), scheduleDate(late, ScheduleOffset.TOMORROW))
        assertEquals(LocalDate.of(2026, 2, 7), scheduleDate(late, ScheduleOffset.IN_7_DAYS))
    }

    @Test
    fun dueLabelMarksTodayAndEarlier() {
        val now = today.atTime(9, 0)
        val due = LocalDate.of(2026, 1, 15)
        val later = LocalDate.of(2026, 1, 20)
        assertEquals("Due · Thu, Jan 15", scheduledWhenLabel(due.toEpochDay(), null, now, Locale.US))
        assertEquals("Tue, Jan 20", scheduledWhenLabel(later.toEpochDay(), null, now, Locale.US))
        assertEquals(
            "Due · Wed, Jan 14",
            scheduledWhenLabel(LocalDate.of(2026, 1, 14).toEpochDay(), null, now, Locale.US),
        )
    }

    @Test
    fun aSetTimeIsShownAfterTheDate() {
        val now = today.atTime(9, 0)
        val later = LocalDate.of(2026, 10, 8).toEpochDay()
        assertEquals("Thu, Oct 8 · 14:30", scheduleMomentLabel(later, 14 * 60 + 30, Locale.US))
        assertEquals("Thu, Oct 8 · 14:30", scheduledWhenLabel(later, 14 * 60 + 30, now, Locale.US))
        assertEquals("Thu, Jan 15 · 14:30", scheduledWhenLabel(today.toEpochDay(), 14 * 60 + 30, now, Locale.US))
        assertEquals("Due · Thu, Jan 15 · 08:05", scheduledWhenLabel(today.toEpochDay(), 8 * 60 + 5, now, Locale.US))
    }

    @Test
    fun aDatedTaskWithATimeIsDueOnlyOnceThatTimePasses() {
        val day = today.toEpochDay()
        val at = 14 * 60 + 30
        assertFalse(isScheduledDue(day, at, today.atTime(14, 29)))
        assertTrue(isScheduledDue(day, at, today.atTime(14, 30)))
        assertTrue(isScheduledDue(day, at, today.atTime(18, 0)))
        assertTrue(isScheduledDue(day, at, LocalDateTime.of(today.plusDays(1), java.time.LocalTime.MIDNIGHT)))
        assertFalse(isScheduledDue(today.plusDays(1).toEpochDay(), 0, today.atTime(23, 59)))
    }

    @Test
    fun aDatedTaskWithoutATimeIsDueAllDay() {
        val day = today.toEpochDay()
        assertTrue(isScheduledDue(day, null, today.atStartOfDay()))
        assertFalse(isScheduledDue(day, null, today.minusDays(1).atTime(23, 59)))
    }

    @Test
    fun theWakeUpMomentIsTheLocalTimeOnThatDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val expected = today.atTime(14, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, scheduledMomentEpochMillis(today.toEpochDay(), 14 * 60 + 30, zone))
    }

    @Test
    fun countdownDayCountCoversTodayTomorrowAndDays() {
        assertEquals("Today", countdownDayLabel(countdownDaysLeft(today.toEpochDay(), today)))
        assertEquals("Tomorrow", countdownDayLabel(countdownDaysLeft(today.plusDays(1).toEpochDay(), today)))
        assertEquals("2 days", countdownDayLabel(countdownDaysLeft(today.plusDays(2).toEpochDay(), today)))
        assertEquals("12 days", countdownDayLabel(countdownDaysLeft(today.plusDays(12).toEpochDay(), today)))
        assertEquals("Passed", countdownDayLabel(countdownDaysLeft(today.minusDays(1).toEpochDay(), today)))
    }

    @Test
    fun aCountdownIsDueOnItsDay() {
        assertTrue(isCountdownDue(today.toEpochDay(), today))
        assertTrue(isCountdownDue(today.minusDays(1).toEpochDay(), today))
        assertFalse(isCountdownDue(today.plusDays(1).toEpochDay(), today))
    }

    @Test
    fun countdownIntervalIsDaysFromTodayInRange() {
        assertEquals(12, countdownIntervalDays(today.plusDays(12).toEpochDay(), today))
        assertEquals(1, countdownIntervalDays(today.plusDays(1).toEpochDay(), today))
        assertEquals(999, countdownIntervalDays(today.plusDays(999).toEpochDay(), today))
        assertEquals(null, countdownIntervalDays(today.toEpochDay(), today))
        assertEquals(null, countdownIntervalDays(today.minusDays(1).toEpochDay(), today))
        assertEquals(null, countdownIntervalDays(today.plusDays(1000).toEpochDay(), today))
    }

    @Test
    fun suggestedDaysPrefersTheEventIntervalThenTheLastCount() {
        assertEquals(30, suggestedCountdownDays(30, 12))
        assertEquals(12, suggestedCountdownDays(null, 12))
        assertEquals(12, suggestedCountdownDays(0, 12))
        assertEquals(null, suggestedCountdownDays(null, null))
        assertEquals(null, suggestedCountdownDays(0, 0))
    }
}
