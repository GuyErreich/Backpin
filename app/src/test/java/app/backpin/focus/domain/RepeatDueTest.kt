package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatDueTest {
    @Test
    fun dailyIsDueOnAndAfterTheAnchor() {
        assertTrue(isRepeatDue(anchorEpochDay = 10, everyNDays = 1, day = 10))
        assertTrue(isRepeatDue(anchorEpochDay = 10, everyNDays = 1, day = 11))
        assertFalse(isRepeatDue(anchorEpochDay = 10, everyNDays = 1, day = 9))
    }

    @Test
    fun everyTwoDaysSkipsTheOffDay() {
        assertTrue(isRepeatDue(20, 2, 20))
        assertFalse(isRepeatDue(20, 2, 21))
        assertTrue(isRepeatDue(20, 2, 22))
        assertFalse(isRepeatDue(20, 2, 23))
    }

    @Test
    fun everyNDaysBelowOneIsTreatedAsDaily() {
        assertTrue(isRepeatDue(5, 0, 6))
        assertTrue(isRepeatDue(5, -3, 8))
    }

    @Test
    fun nextDueDayJumpsToTheFollowingCycle() {
        val repeat = sample(everyNDays = 3, anchor = 10)
        assertEquals(10L, nextDueDay(repeat, 7))
        assertEquals(10L, nextDueDay(repeat, 10))
        assertEquals(13L, nextDueDay(repeat, 11))
        assertEquals(13L, nextDueDay(repeat, 13))
    }

    @Test
    fun nextDueDayForPicksTheEarliestAmongRepeats() {
        val daily = sample(id = "d", everyNDays = 1, anchor = 12)
        val weekly = sample(id = "w", everyNDays = 7, anchor = 10)
        assertEquals(12L, nextDueDayFor(listOf(daily, weekly), 12))
        assertEquals(10L, nextDueDayFor(listOf(weekly), 8))
        assertEquals(null, nextDueDayFor(emptyList(), 8))
    }

    @Test
    fun statusPrefersDoneThenMissedThenOpen() {
        val done = RepeatLog("r", 1, RepeatOutcome.DONE, 1)
        val missed = RepeatLog("r", 1, RepeatOutcome.MISSED, 1)
        assertEquals(RepeatDayStatus.DONE, repeatStatusOnDay(done, bandEndPassed = true))
        assertEquals(RepeatDayStatus.MISSED, repeatStatusOnDay(missed, bandEndPassed = false))
        assertEquals(RepeatDayStatus.MISSED, repeatStatusOnDay(null, bandEndPassed = true))
        assertEquals(RepeatDayStatus.OPEN, repeatStatusOnDay(null, bandEndPassed = false))
    }

    private fun sample(
        id: String = "r",
        everyNDays: Int,
        anchor: Long,
    ) = Repeat(
        id = id,
        title = "Meds",
        band = RepeatBand.MORNING,
        everyNDays = everyNDays,
        anchorEpochDay = anchor,
        reminderOn = true,
        sortOrder = 1,
        createdAtEpochMillis = 1,
        archivedAtEpochMillis = null,
    )
}
