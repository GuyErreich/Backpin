package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ScheduleDateTest {
    private val today: LocalDate = LocalDate.of(2026, 1, 15)

    @Test
    fun offsetsAreLocalDaysFromToday() {
        assertEquals(LocalDate.of(2026, 1, 16), scheduleDate(today, ScheduleOffset.TOMORROW))
        assertEquals(LocalDate.of(2026, 1, 17), scheduleDate(today, ScheduleOffset.IN_A_COUPLE_OF_DAYS))
        assertEquals(LocalDate.of(2026, 1, 22), scheduleDate(today, ScheduleOffset.NEXT_WEEK))
    }

    @Test
    fun offsetsCrossAMonthBoundary() {
        val late = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), scheduleDate(late, ScheduleOffset.TOMORROW))
        assertEquals(LocalDate.of(2026, 2, 2), scheduleDate(late, ScheduleOffset.IN_A_COUPLE_OF_DAYS))
        assertEquals(LocalDate.of(2026, 2, 7), scheduleDate(late, ScheduleOffset.NEXT_WEEK))
    }

    @Test
    fun dayOfMonthUsesThisMonthWhenThatDayIsStillAhead() {
        assertEquals(LocalDate.of(2026, 1, 31), nextDayOfMonth(today, 31))
        assertEquals(LocalDate.of(2026, 1, 20), nextDayOfMonth(today, 20))
    }

    @Test
    fun dayOfMonthMovesToTheNextMonthThatContainsIt() {
        assertEquals(LocalDate.of(2026, 2, 10), nextDayOfMonth(today, 10))
        assertEquals(LocalDate.of(2026, 2, 15), nextDayOfMonth(today, 15))
        assertEquals(LocalDate.of(2026, 3, 31), nextDayOfMonth(LocalDate.of(2026, 1, 31), 31))
        assertEquals(LocalDate.of(2026, 5, 31), nextDayOfMonth(LocalDate.of(2026, 4, 30), 31))
        assertEquals(LocalDate.of(2027, 3, 29), nextDayOfMonth(LocalDate.of(2027, 2, 1), 29))
        assertEquals(LocalDate.of(2028, 2, 29), nextDayOfMonth(LocalDate.of(2028, 2, 1), 29))
        assertEquals(LocalDate.of(2028, 3, 29), nextDayOfMonth(LocalDate.of(2028, 2, 29), 29))
    }

    @Test
    fun dayOfMonthRejectsValuesOutsideTheCalendar() {
        assertThrows(IllegalArgumentException::class.java) { nextDayOfMonth(today, 0) }
        assertThrows(IllegalArgumentException::class.java) { nextDayOfMonth(today, 32) }
    }

    @Test
    fun dueLabelMarksTodayAndEarlier() {
        val due = LocalDate.of(2026, 1, 15)
        val later = LocalDate.of(2026, 1, 20)
        assertEquals("Due · Thu, Jan 15", scheduledWhenLabel(due.toEpochDay(), today, Locale.US))
        assertEquals("Tue, Jan 20", scheduledWhenLabel(later.toEpochDay(), today, Locale.US))
        assertEquals(
            "Due · Wed, Jan 14",
            scheduledWhenLabel(LocalDate.of(2026, 1, 14).toEpochDay(), today, Locale.US),
        )
    }
}
