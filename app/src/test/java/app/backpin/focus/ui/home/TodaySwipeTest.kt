package app.backpin.focus.ui.home

import app.backpin.focus.ui.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TodaySwipeTest {
    @Test
    fun rightSwipeIsDoneStartOrInterrupt() {
        assertEquals(TodayRightSwipe.Done, todayRightSwipe(SlotRole.NOW, somethingCurrent = true))
        assertEquals(TodayRightSwipe.Start, todayRightSwipe(SlotRole.OPEN, somethingCurrent = false))
        assertEquals(TodayRightSwipe.Interrupt, todayRightSwipe(SlotRole.OPEN, somethingCurrent = true))
        assertEquals(TodayRightSwipe.Done, todayRightSwipe(SlotRole.WAITING, somethingCurrent = false))
    }
}
