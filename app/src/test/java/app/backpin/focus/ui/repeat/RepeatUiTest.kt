package app.backpin.focus.ui.repeat

import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.RepeatRowsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatUiTest {
    @Test
    fun leftLabelFormatsCountsFromTheRowState() {
        assertEquals("", repeatsLeftLabel(RepeatRowsState()))
        assertEquals(
            "2 of 4 left",
            repeatsLeftLabel(RepeatRowsState(leftCount = 2, total = 4)),
        )
    }

    @Test
    fun missedCopyIsQuietAndUsesTheBandName() {
        assertEquals(
            "Missed at noon · muted until end of day",
            missedRepeatCopy(RepeatBand.NOON),
        )
        assertEquals(
            "Water plants. Missed at noon · muted until end of day. Check off.",
            repeatRowDescription(row("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED)),
        )
        assertEquals(
            "Take meds. Morning. Check off.",
            repeatRowDescription(row("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN)),
        )
        assertEquals(
            "Take meds. Morning. every 2 days. Check off.",
            repeatRowDescription(
                row("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN, cadenceDays = 2),
            ),
        )
        assertEquals("MORNING", repeatBandMark(RepeatBand.MORNING))
        assertNull(everyNDaysLabel(1))
        assertEquals("every 3 days", everyNDaysLabel(3))
    }

    private fun row(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
        cadenceDays: Int = 1,
        id: String = title,
    ): RepeatRow =
        RepeatRow(
            occurrence =
                RepeatOccurrence(
                    repeat =
                        Repeat(
                            id = id,
                            title = title,
                            band = band,
                            everyNDays = cadenceDays,
                            anchorEpochDay = 0,
                            reminderOn = true,
                            sortOrder = 0,
                            createdAtEpochMillis = 0,
                            archivedAtEpochMillis = null,
                        ),
                    epochDay = 0,
                    status = status,
                ),
            everyNDays = cadenceDays,
        )
}
