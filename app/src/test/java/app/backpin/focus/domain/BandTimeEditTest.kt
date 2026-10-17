// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BandTimeEditTest {
    @Test
    fun validMinuteIsAppliedAndOrderIsKept() {
        val next = applyBandTime(BandTimes.DEFAULT, BandEdge.MORNING, 8 * 60)
        assertEquals(8 * 60, next?.morningStartMinute)
        assertEquals(12 * 60, next?.noonStartMinute)
        val nightEnd = applyBandTime(BandTimes.DEFAULT, BandEdge.NIGHT_END, 23 * 60 + 59)
        assertEquals(23 * 60 + 59, nightEnd?.nightEndMinute)
    }

    @Test
    fun outOfOrderOrPast2359IsRejected() {
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.MORNING, 12 * 60))
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.NOON, 7 * 60))
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.NIGHT, 23 * 60))
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.NIGHT_END, 18 * 60))
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.NIGHT_END, 24 * 60))
        assertNull(applyBandTime(BandTimes.DEFAULT, BandEdge.MORNING, -1))
    }

    @Test
    fun clockHelpersRoundTripHhMm() {
        assertEquals("07:00", minuteOfDayClock(7 * 60))
        assertEquals("23:59", minuteOfDayClock(23 * 60 + 59))
        assertEquals(18 * 60, parseClockToMinute("18:00"))
        assertNull(parseClockToMinute("8:00"))
        assertNull(parseClockToMinute("not-a-time"))
    }
}
