package app.backpin.focus.ui.repeat

import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.TodaysRepeats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatUiTest {
    @Test
    fun openThenMissedThenDoneAndTheCounterCountsOpen() {
        val todays =
            TodaysRepeats(
                morning =
                    listOf(
                        occ("Take meds", RepeatBand.MORNING, RepeatDayStatus.DONE, id = "1"),
                        occ("Make bed", RepeatBand.MORNING, RepeatDayStatus.OPEN, id = "2"),
                    ),
                noon =
                    listOf(
                        occ("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED, id = "3"),
                    ),
                night =
                    listOf(
                        occ("Brush teeth", RepeatBand.NIGHT, RepeatDayStatus.OPEN, id = "4"),
                    ),
            )
        val ordered = orderedRepeatRows(todays)
        assertEquals(
            listOf("Make bed", "Brush teeth", "Water plants", "Take meds"),
            ordered.map { it.repeat.title },
        )
        assertEquals("2 of 4 left", repeatsLeftLabel(ordered))
    }

    @Test
    fun checkedRowsKeepRelativeOrderAtTheBottom() {
        val todays =
            TodaysRepeats(
                morning =
                    listOf(
                        occ("Take meds", RepeatBand.MORNING, RepeatDayStatus.DONE, id = "1"),
                        occ("Make bed", RepeatBand.MORNING, RepeatDayStatus.OPEN, id = "2"),
                        occ("Vitamins", RepeatBand.MORNING, RepeatDayStatus.DONE, id = "3"),
                    ),
                noon = listOf(occ("Water plants", RepeatBand.NOON, RepeatDayStatus.OPEN, id = "4")),
            )
        assertEquals(
            listOf("Make bed", "Water plants", "Take meds", "Vitamins"),
            orderedRepeatRows(todays).map { it.repeat.title },
        )
        assertEquals("2 of 4 left", repeatsLeftLabel(orderedRepeatRows(todays)))
    }

    @Test
    fun missedCopyIsQuietAndUsesTheBandName() {
        assertEquals(
            "Missed at noon · muted until end of day",
            missedRepeatCopy(RepeatBand.NOON),
        )
        assertEquals(
            "Water plants. Missed at noon · muted until end of day. Check off.",
            repeatRowDescription(occ("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED)),
        )
        assertEquals(
            "Take meds. Morning. Check off.",
            repeatRowDescription(occ("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN)),
        )
        assertEquals(
            "Take meds. Morning. every 2 days. Check off.",
            repeatRowDescription(
                occ("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN, everyNDays = 2),
            ),
        )
        assertEquals("MORNING", repeatBandMark(RepeatBand.MORNING))
        assertNull(everyNDaysLabel(1))
        assertEquals("every 3 days", everyNDaysLabel(3))
        assertEquals("", repeatsLeftLabel(emptyList()))
        assertEquals("07:00", minuteOfDayClock(7 * 60))
        assertEquals(18 * 60, parseClockToMinute("18:00"))
    }

    private fun occ(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
        everyNDays: Int = 1,
        id: String = title,
    ): RepeatOccurrence =
        RepeatOccurrence(
            repeat =
                Repeat(
                    id = id,
                    title = title,
                    band = band,
                    everyNDays = everyNDays,
                    anchorEpochDay = 0,
                    reminderOn = true,
                    sortOrder = 0,
                    createdAtEpochMillis = 0,
                    archivedAtEpochMillis = null,
                ),
            epochDay = 0,
            status = status,
        )
}
