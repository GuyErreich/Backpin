// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatRowsTest {
    @Test
    fun openThenMissedThenDoneAndLeftCountIsOpen() {
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
        val state = repeatRowsState(todays)
        assertEquals(
            listOf("Make bed", "Brush teeth", "Water plants", "Take meds"),
            state.rows.map { it.repeat.title },
        )
        assertEquals(2, state.leftCount)
        assertEquals(4, state.total)
    }

    @Test
    fun doneRowsKeepRelativeOrderAtTheBottom() {
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
        val state = repeatRowsState(todays)
        assertEquals(
            listOf("Make bed", "Water plants", "Take meds", "Vitamins"),
            state.rows.map { it.repeat.title },
        )
        assertEquals(2, state.leftCount)
        assertEquals(4, state.total)
    }

    @Test
    fun emptyTodayHasZeroCounts() {
        val state = repeatRowsState(TodaysRepeats())
        assertEquals(emptyList<RepeatRow>(), state.rows)
        assertEquals(0, state.leftCount)
        assertEquals(0, state.total)
    }

    @Test
    fun rowCarriesCadenceDaysForNonDailyRepeats() {
        val todays =
            TodaysRepeats(
                morning =
                    listOf(
                        occ("Vitamins", RepeatBand.MORNING, RepeatDayStatus.OPEN, everyNDays = 2, id = "1"),
                    ),
            )
        val row = repeatRowsState(todays).rows.single()
        assertEquals(2, row.everyNDays)
        assertEquals(2, row.cadenceDays)
        assertEquals("Vitamins", row.repeat.title)
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
