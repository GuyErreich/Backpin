package app.backpin.focus.ui.countdown

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownDaysTest {
    @Test
    fun sanitizeKeepsDigitsThrough999AndDropsALeadingZero() {
        assertEquals("", sanitizeCountdownDays(""))
        assertEquals("", sanitizeCountdownDays("abc"))
        assertEquals("0", sanitizeCountdownDays("0"))
        assertEquals("25", sanitizeCountdownDays("025"))
        assertEquals("999", sanitizeCountdownDays("9999"))
        assertEquals("12", sanitizeCountdownDays("1a2"))
    }

    @Test
    fun typedDaysNeedAtLeastOne() {
        assertEquals(null, countdownDays(""))
        assertEquals(null, countdownDays("0"))
        assertEquals(1, countdownDays("1"))
        assertEquals(25, countdownDays("25"))
        assertEquals(999, countdownDays("999"))
    }
}
