// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class HomeBandsTest {
    @Test
    fun homeBandForUsesHalfOpenWindowsAndAnytimeOutside() {
        assertEquals(HomeBand.ANYTIME, homeBandFor(null, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(MORNING_START - 1, TIMES))
        assertEquals(HomeBand.MORNING, homeBandFor(MORNING_START, TIMES))
        assertEquals(HomeBand.MORNING, homeBandFor(NOON_START - 1, TIMES))
        assertEquals(HomeBand.NOON, homeBandFor(NOON_START, TIMES))
        assertEquals(HomeBand.NOON, homeBandFor(NIGHT_START - 1, TIMES))
        assertEquals(HomeBand.NIGHT, homeBandFor(NIGHT_START, TIMES))
        assertEquals(HomeBand.NIGHT, homeBandFor(NIGHT_END - 1, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(NIGHT_END, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(NIGHT_END + 1, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(0, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(MINUTES_PER_DAY - 1, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(-1, TIMES))
        assertEquals(HomeBand.ANYTIME, homeBandFor(MINUTES_PER_DAY, TIMES))
    }

    @Test
    fun currentHomeBandIsNullOutsideWindowsAndInclusiveAtStarts() {
        assertNull(currentHomeBand(MORNING_START - 1, TIMES))
        assertEquals(HomeBand.MORNING, currentHomeBand(MORNING_START, TIMES))
        assertEquals(HomeBand.MORNING, currentHomeBand(NOON_START - 1, TIMES))
        assertEquals(HomeBand.NOON, currentHomeBand(NOON_START, TIMES))
        assertEquals(HomeBand.NOON, currentHomeBand(NIGHT_START - 1, TIMES))
        assertEquals(HomeBand.NIGHT, currentHomeBand(NIGHT_START, TIMES))
        assertEquals(HomeBand.NIGHT, currentHomeBand(NIGHT_END - 1, TIMES))
        assertNull(currentHomeBand(NIGHT_END, TIMES))
        assertNull(currentHomeBand(NIGHT_END + 1, TIMES))
        assertNull(currentHomeBand(0, TIMES))
        assertNull(currentHomeBand(-1, TIMES))
        assertNull(currentHomeBand(MINUTES_PER_DAY, TIMES))
    }

    @Test
    fun openHomeBandIsNeverNullAndFallsBackToMorning() {
        assertEquals(HomeBand.MORNING, openHomeBand(MORNING_START, TIMES))
        assertEquals(HomeBand.NOON, openHomeBand(NOON_START, TIMES))
        assertEquals(HomeBand.NIGHT, openHomeBand(NIGHT_START, TIMES))
        assertEquals(HomeBand.MORNING, openHomeBand(MORNING_START - 1, TIMES))
        assertEquals(HomeBand.MORNING, openHomeBand(NIGHT_END, TIMES))
        assertEquals(HomeBand.MORNING, openHomeBand(NIGHT_END + 1, TIMES))
        assertEquals(HomeBand.MORNING, openHomeBand(0, TIMES))
        assertEquals(HomeBand.MORNING, openHomeBand(-1, TIMES))
        assertNotEquals(HomeBand.ANYTIME, openHomeBand(NIGHT_END, TIMES))
        assertNotEquals(HomeBand.ANYTIME, openHomeBand(5 * 60, TIMES))
    }

    @Test
    fun phasesMatchOpenWindowAndAnytimeIsAlwaysOpen() {
        assertPhases(
            minute = MORNING_START,
            morning = BandPhase.OPEN,
            noon = BandPhase.FUTURE,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = NOON_START - 1,
            morning = BandPhase.OPEN,
            noon = BandPhase.FUTURE,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = NOON_START,
            morning = BandPhase.PAST,
            noon = BandPhase.OPEN,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = NIGHT_START - 1,
            morning = BandPhase.PAST,
            noon = BandPhase.OPEN,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = NIGHT_START,
            morning = BandPhase.PAST,
            noon = BandPhase.PAST,
            night = BandPhase.OPEN,
        )
        assertPhases(
            minute = NIGHT_END - 1,
            morning = BandPhase.PAST,
            noon = BandPhase.PAST,
            night = BandPhase.OPEN,
        )
        assertPhases(
            minute = NIGHT_END,
            morning = BandPhase.OPEN,
            noon = BandPhase.FUTURE,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = NIGHT_END + 1,
            morning = BandPhase.OPEN,
            noon = BandPhase.FUTURE,
            night = BandPhase.FUTURE,
        )
        assertPhases(
            minute = MORNING_START - 1,
            morning = BandPhase.OPEN,
            noon = BandPhase.FUTURE,
            night = BandPhase.FUTURE,
        )
    }

    @Test
    fun openTodayTasksPinToOpenBandInSortOrderNeverAnytime() {
        val first = task("a", "First", sortOrder = 0, scheduledAtMinuteOfDay = NIGHT_START)
        val second = task("b", "Second", sortOrder = 1, scheduledAtMinuteOfDay = null)
        val third = task("c", "Third", sortOrder = 2, scheduledAtMinuteOfDay = NOON_START)
        val grouped =
            homeBandsForToday(
                tasks = listOf(third, first, second),
                rows = RepeatRowsState(),
                times = TIMES,
                minuteOfDay = NOON_START,
            )
        assertEquals(HomeBand.NOON, grouped.openBand)
        assertEquals(HomeBand.NOON, grouped.current)
        assertEquals(listOf("First", "Second", "Third"), grouped.noon.tasks.map { it.title })
        assertTrue(grouped.morning.tasks.isEmpty())
        assertTrue(grouped.night.tasks.isEmpty())
        assertTrue(grouped.anytime.tasks.isEmpty())
        assertEquals(BandPhase.OPEN, grouped.noon.phase)
        assertEquals(BandPhase.ALWAYS_OPEN, grouped.anytime.phase)

        val beforeMorning =
            homeBandsForToday(
                tasks = listOf(third, first, second),
                rows = RepeatRowsState(),
                times = TIMES,
                minuteOfDay = MORNING_START - 1,
            )
        assertEquals(HomeBand.MORNING, beforeMorning.openBand)
        assertEquals(listOf("First", "Second", "Third"), beforeMorning.morning.tasks.map { it.title })
        assertTrue(beforeMorning.anytime.tasks.isEmpty())

        val afterNight =
            homeBandsForToday(
                tasks = listOf(third, first, second),
                rows = RepeatRowsState(),
                times = TIMES,
                minuteOfDay = NIGHT_END,
            )
        assertEquals(HomeBand.MORNING, afterNight.openBand)
        assertEquals(listOf("First", "Second", "Third"), afterNight.morning.tasks.map { it.title })
        assertTrue(afterNight.anytime.tasks.isEmpty())
    }

    @Test
    fun doneAndNonMainTasksKeepTheirOwnBand() {
        val pinned = task("p", "Pinned today", sortOrder = 0, scheduledAtMinuteOfDay = NIGHT_START)
        val doneToday =
            task(
                id = "d",
                title = "Done today",
                bucket = TaskBucket.TODAY,
                status = TaskStatus.DONE,
                sortOrder = 1,
                scheduledAtMinuteOfDay = MORNING_START,
            )
        val inbox =
            task(
                id = "i",
                title = "Inbox",
                bucket = TaskBucket.INBOX,
                sortOrder = 2,
                scheduledAtMinuteOfDay = NOON_START,
            )
        val grouped =
            homeBands(
                tasks = listOf(pinned, doneToday, inbox),
                rows = RepeatRowsState(),
                times = TIMES,
                minuteOfDay = NIGHT_START,
                pinnedTaskIds = listOf("p"),
            )
        assertEquals(listOf("Pinned today"), grouped.night.tasks.map { it.title })
        assertEquals(listOf("Done today"), grouped.morning.tasks.map { it.title })
        assertEquals(listOf("Inbox"), grouped.noon.tasks.map { it.title })
        assertTrue(grouped.anytime.tasks.isEmpty())
    }

    @Test
    fun repeatsStayOnTheirBandAndNeverLandInAnytime() {
        RepeatBand.entries.forEach { band ->
            assertNotEquals(HomeBand.ANYTIME, band.toHomeBand())
        }
        val rows =
            RepeatRowsState(
                rows =
                    listOf(
                        repeatRow("meds", RepeatBand.MORNING, RepeatDayStatus.OPEN),
                        repeatRow("plants", RepeatBand.NOON, RepeatDayStatus.MISSED),
                        repeatRow("teeth", RepeatBand.NIGHT, RepeatDayStatus.DONE),
                    ),
            )
        val outside =
            homeBands(
                tasks = emptyList(),
                rows = rows,
                times = TIMES,
                minuteOfDay = NIGHT_END + 30,
            )
        assertEquals(listOf("meds"), outside.morning.repeats.map { it.repeat.title })
        assertEquals(listOf("plants"), outside.noon.repeats.map { it.repeat.title })
        assertEquals(listOf("teeth"), outside.night.repeats.map { it.repeat.title })
        assertTrue(outside.anytime.repeats.isEmpty())
        assertEquals(HomeBand.MORNING, outside.openBand)
    }

    @Test
    fun countsUseDoneMissedAndLeftAsOpenPlusMissed() {
        val openTask = task("o", "Open", status = TaskStatus.OPEN)
        val doneTask = task("d", "Done", status = TaskStatus.DONE)
        val repeats =
            listOf(
                repeatRow("open-repeat", RepeatBand.MORNING, RepeatDayStatus.OPEN),
                repeatRow("missed-repeat", RepeatBand.MORNING, RepeatDayStatus.MISSED),
                repeatRow("done-repeat", RepeatBand.MORNING, RepeatDayStatus.DONE),
            )
        val counts = homeBandCounts(listOf(openTask, doneTask), repeats)
        assertEquals(2, counts.done)
        assertEquals(1, counts.missed)
        assertEquals(3, counts.left)

        val grouped =
            homeBands(
                tasks = listOf(openTask, doneTask),
                rows = RepeatRowsState(rows = repeats),
                times = TIMES,
                minuteOfDay = MORNING_START,
            )
        assertEquals(counts, grouped.morning.counts)
        assertEquals(HomeBandCounts(), grouped.noon.counts)
    }

    @Test
    fun currentMinuteOfDayReadsTheInjectedClock() {
        val clock = AppClock { DAY.atTime(8, 30).atZone(ZONE).toInstant().toEpochMilli() }
        assertEquals(8 * 60 + 30, currentMinuteOfDay(clock, ZONE))
    }

    @Test
    fun nightDoesNotWrapPastMidnightInBandSettings() {
        assertTrue(TIMES.isValid())
        assertTrue(BandTimes(7 * 60, 12 * 60, 18 * 60, 23 * 60 + 59).isValid())
        assertFalse(BandTimes(7 * 60, 12 * 60, 18 * 60, 1).isValid())
        assertFalse(BandTimes(7 * 60, 12 * 60, 22 * 60, 2).isValid())
        assertNull(applyBandTime(TIMES, BandEdge.NIGHT_END, 1))
        assertNull(applyBandTime(TIMES, BandEdge.NIGHT_END, MINUTES_PER_DAY))
        val wrap = BandTimes(7 * 60, 12 * 60, 22 * 60, 1)
        assertNull(currentHomeBand(0, wrap))
        assertEquals(HomeBand.ANYTIME, homeBandFor(0, wrap))
        assertEquals(HomeBand.MORNING, openHomeBand(0, wrap))
    }

    @Test
    fun nextBoundaryPicksEarliestLaterBoundFromUnorderedTimesAndSkipsNow() {
        val unordered =
            BandTimes(
                morningStartMinute = 20 * 60,
                noonStartMinute = 8 * 60,
                nightStartMinute = 22 * 60,
                nightEndMinute = 14 * 60,
            )
        assertFalse(unordered.isValid())
        val ten = at(10, 0)
        val untilFourteen = millisUntilNextBandBoundary(ten, unordered, ZONE)
        assertEquals(at(14, 0), ten + untilFourteen)

        val exactlyFourteen = at(14, 0)
        val untilTwenty = millisUntilNextBandBoundary(exactlyFourteen, unordered, ZONE)
        assertEquals(at(20, 0), exactlyFourteen + untilTwenty)
        assertNotEquals(exactlyFourteen, exactlyFourteen + untilTwenty)

        val atLastBound = at(22, 0)
        val wrap = millisUntilNextBandBoundary(atLastBound, unordered, ZONE)
        assertEquals(at(8, 0, day = DAY.plusDays(1)), atLastBound + wrap)
    }

    @Test
    fun nextBoundaryOnDefaultTimesSkipsTheBoundEqualToNowThenWraps() {
        assertEquals(at(7, 0) - at(6, 59), millisUntilNextBandBoundary(at(6, 59), TIMES, ZONE))
        assertEquals(at(12, 0) - at(7, 0), millisUntilNextBandBoundary(at(7, 0), TIMES, ZONE))
        assertEquals(at(18, 0) - at(12, 0), millisUntilNextBandBoundary(at(12, 0), TIMES, ZONE))
        assertEquals(at(23, 0) - at(18, 0), millisUntilNextBandBoundary(at(18, 0), TIMES, ZONE))
        val nightEnd = at(23, 0)
        val untilTomorrowMorning = millisUntilNextBandBoundary(nightEnd, TIMES, ZONE)
        assertEquals(at(7, 0, day = DAY.plusDays(1)), nightEnd + untilTomorrowMorning)
        assertTrue(millisUntilNextBandBoundary(nightEnd, TIMES, ZONE) >= 1L)
    }

    private fun assertPhases(
        minute: Int,
        morning: BandPhase,
        noon: BandPhase,
        night: BandPhase,
    ) {
        val grouped =
            homeBands(
                tasks = emptyList(),
                rows = RepeatRowsState(),
                times = TIMES,
                minuteOfDay = minute,
            )
        val timed = currentHomeBand(minute, TIMES)
        val open = timed ?: HomeBand.MORNING
        assertEquals(open, grouped.openBand)
        assertEquals(open, grouped.current)
        assertEquals(morning, grouped.morning.phase)
        assertEquals(noon, grouped.noon.phase)
        assertEquals(night, grouped.night.phase)
        assertEquals(BandPhase.ALWAYS_OPEN, grouped.anytime.phase)
        assertEquals(morning, homeBandPhase(HomeBand.MORNING, open, timed))
        assertEquals(noon, homeBandPhase(HomeBand.NOON, open, timed))
        assertEquals(night, homeBandPhase(HomeBand.NIGHT, open, timed))
        assertEquals(BandPhase.ALWAYS_OPEN, homeBandPhase(HomeBand.ANYTIME, open, timed))
    }

    private fun task(
        id: String,
        title: String,
        bucket: TaskBucket = TaskBucket.TODAY,
        status: TaskStatus = TaskStatus.OPEN,
        sortOrder: Long = 0,
        scheduledAtMinuteOfDay: Int? = null,
    ): Task =
        Task(
            id = id,
            title = title,
            bucket = bucket,
            status = status,
            returnCue = null,
            sortOrder = sortOrder,
            createdAtEpochMillis = 0,
            completedAtEpochMillis = if (status == TaskStatus.DONE) 1 else null,
            scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        )

    private fun repeatRow(
        title: String,
        band: RepeatBand,
        status: RepeatDayStatus,
    ): RepeatRow =
        RepeatRow(
            occurrence =
                RepeatOccurrence(
                    repeat =
                        Repeat(
                            id = title,
                            title = title,
                            band = band,
                            everyNDays = 1,
                            anchorEpochDay = 0,
                            reminderOn = true,
                            sortOrder = 0,
                            createdAtEpochMillis = 0,
                            archivedAtEpochMillis = null,
                        ),
                    epochDay = 0,
                    status = status,
                ),
            everyNDays = 1,
        )

    private companion object {
        val TIMES: BandTimes = BandTimes.DEFAULT
        const val MORNING_START: Int = BandTimes.DEFAULT_MORNING_START_MINUTE
        const val NOON_START: Int = BandTimes.DEFAULT_NOON_START_MINUTE
        const val NIGHT_START: Int = BandTimes.DEFAULT_NIGHT_START_MINUTE
        const val NIGHT_END: Int = BandTimes.DEFAULT_NIGHT_END_MINUTE
        val DAY: LocalDate = LocalDate.of(2026, 10, 16)
        val ZONE: ZoneOffset = ZoneOffset.UTC

        fun at(
            hour: Int,
            minute: Int,
            day: LocalDate = DAY,
        ): Long = day.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()
    }
}
