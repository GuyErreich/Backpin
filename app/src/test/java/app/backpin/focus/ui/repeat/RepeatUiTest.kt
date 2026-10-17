package app.backpin.focus.ui.repeat

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatOccurrence
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.RepeatRowsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepeatUiTest {
    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun leftLabelFormatsCountsFromTheRowState() {
        assertEquals("", repeatsLeftLabel(RepeatRowsState(), resources))
        assertEquals(
            "2 of 4 left",
            repeatsLeftLabel(RepeatRowsState(leftCount = 2, total = 4), resources),
        )
        assertEquals(
            "All done",
            repeatsLeftLabel(RepeatRowsState(leftCount = 0, total = 3), resources),
        )
        assertEquals(
            "",
            repeatsLeftLabel(RepeatRowsState(leftCount = 0, total = 0), resources),
        )
        assertTrue(repeatsLeftLabel(RepeatRowsState(), resources) != "All done")
    }

    @Test
    fun missedCopyIsQuietAndUsesTheBandName() {
        assertEquals(
            "Missed at noon · muted until end of day",
            missedRepeatCopy(RepeatBand.NOON, resources),
        )
        val missed = repeatRowDescription(row("Water plants", RepeatBand.NOON, RepeatDayStatus.MISSED), resources)
        assertTrue(missed.contains("Missed"))
        assertEquals(
            "Water plants. Missed at noon · muted until end of day. Check off.",
            missed,
        )
        assertEquals(
            "Take meds. Morning. Check off.",
            repeatRowDescription(row("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN), resources),
        )
        assertEquals(
            "Take meds. Morning. every 2 days. Check off.",
            repeatRowDescription(
                row("Take meds", RepeatBand.MORNING, RepeatDayStatus.OPEN, cadenceDays = 2),
                resources,
            ),
        )
        assertEquals("MORNING", repeatBandMark(RepeatBand.MORNING))
        assertNull(everyNDaysLabel(1, resources))
        assertEquals("every 3 days", everyNDaysLabel(3, resources))
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
