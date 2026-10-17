// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatRowsLeftCountTest {
    @Test
    fun leftCountCountsMixedOpenMissedAndDone() {
        val state =
            repeatRowsState(
                TodaysRepeats(
                    morning = listOf(occ("Make bed", RepeatBand.MORNING, RepeatDayStatus.OPEN, id = "1")),
                    noon = listOf(occ("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED, id = "2")),
                    night = listOf(occ("Brush teeth", RepeatBand.NIGHT, RepeatDayStatus.DONE, id = "3")),
                ),
            )
        assertEquals(2, state.leftCount)
        assertEquals(3, state.total)
        assertEquals(
            listOf(RepeatDayStatus.OPEN, RepeatDayStatus.MISSED, RepeatDayStatus.DONE),
            state.rows.map { it.status },
        )
    }

    @Test
    fun leftCountIsZeroWhenEveryDueRepeatIsDone() {
        val state =
            repeatRowsState(
                TodaysRepeats(
                    morning = listOf(occ("Take meds", RepeatBand.MORNING, RepeatDayStatus.DONE, id = "1")),
                    noon = listOf(occ("Water plants", RepeatBand.NOON, RepeatDayStatus.DONE, id = "2")),
                ),
            )
        assertEquals(0, state.leftCount)
        assertEquals(2, state.total)
        assertTrue(state.rows.all { it.status == RepeatDayStatus.DONE })
    }

    private fun occ(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
        everyNDays: Int = 1,
        id: String = title,
    ): RepeatOccurrence = RepeatOccurrence(
        repeat = Repeat(
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
