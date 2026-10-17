// SPDX-License-Identifier: GPL-3.0-or-later
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
    fun childBodyUsesCadenceNotBandName() {
        assertEquals("Every day", bandChildText(1))
        assertEquals("Every day", bandChildText(0))
        assertEquals("Every 2 days", bandChildText(2))
        assertEquals("Every 7 days", bandChildText(7))
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
