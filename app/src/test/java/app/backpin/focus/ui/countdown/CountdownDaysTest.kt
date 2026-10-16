package app.backpin.focus.ui.countdown

import app.backpin.focus.domain.scheduleMomentLabel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun resumeLabelsNameTheCountAndTheLandingDay() {
        val today = LocalDate.of(2026, 10, 16)
        val inTwelve = today.plusDays(12).toEpochDay()
        assertEquals(
            "Again in 12 days · ${scheduleMomentLabel(inTwelve, null)}",
            againInDaysLabel(12, today),
        )
        assertEquals("Start in 0 days", startInDaysLabel(""))
        assertEquals("Start in 0 days", startInDaysLabel("0"))
        assertEquals("Start in 45 days", startInDaysLabel("45"))
    }
}
