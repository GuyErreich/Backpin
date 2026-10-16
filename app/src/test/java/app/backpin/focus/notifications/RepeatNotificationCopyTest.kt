package app.backpin.focus.notifications

import app.backpin.focus.domain.RepeatBand
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatNotificationCopyTest {
    @Test
    fun summaryMatchesTheApprovedFigmaFrames() {
        assertEquals("Morning · 3 things", bandSummaryTitle(RepeatBand.MORNING, 3))
        assertEquals("Morning · 2 things", bandSummaryTitle(RepeatBand.MORNING, 2))
        assertEquals("Noon · 1 thing", bandSummaryTitle(RepeatBand.NOON, 1))
        assertEquals("Night · 4 things", bandSummaryTitle(RepeatBand.NIGHT, 4))
    }

    @Test
    fun childBodyMatchesTheApprovedFigmaFrames() {
        assertEquals("Morning repeat", bandChildText(RepeatBand.MORNING))
        assertEquals("Noon repeat", bandChildText(RepeatBand.NOON))
        assertEquals("Night repeat", bandChildText(RepeatBand.NIGHT))
    }

    @Test
    fun groupKeyIsOnePerBand() {
        assertEquals("repeats_MORNING", bandGroupKey(RepeatBand.MORNING))
        assertEquals(
            NotificationContract.ID_REPEAT_SUMMARY_MORNING,
            repeatSummaryNotificationId(RepeatBand.MORNING),
        )
        assertEquals(
            NotificationContract.ID_REPEAT_SUMMARY_NOON,
            repeatSummaryNotificationId(RepeatBand.NOON),
        )
    }
}
