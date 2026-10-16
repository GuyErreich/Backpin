package app.backpin.focus.notifications

import app.backpin.focus.domain.BandTimes
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BandAlarmTimesTest {
    private val newYork: ZoneId = ZoneId.of("America/New_York")
    private val times = BandTimes.DEFAULT
    private val dailyMorning = listOf(repeat(RepeatBand.MORNING, everyNDays = 1, anchor = 0))

    @Test
    fun beforeMorningStartSchedulesTodayStartFollowUpAndEnd() {
        val now = local("2026-10-16", 6, 30)
        val alarms = nextBandAlarms(now, newYork, times, dailyMorning)
        assertEquals(
            listOf(BandAlarmKind.START, BandAlarmKind.FOLLOW_UP, BandAlarmKind.END),
            alarms.map { it.kind },
        )
        assertEquals(local("2026-10-16", 7, 0), alarms.single { it.kind == BandAlarmKind.START }.epochMillis)
        assertEquals(local("2026-10-16", 11, 30), alarms.single { it.kind == BandAlarmKind.FOLLOW_UP }.epochMillis)
        assertEquals(local("2026-10-16", 12, 0), alarms.single { it.kind == BandAlarmKind.END }.epochMillis)
    }

    @Test
    fun afterMorningStartUsesTomorrowForTheNextStart() {
        val now = local("2026-10-16", 8, 0)
        val alarms = nextBandAlarms(now, newYork, times, dailyMorning)
        assertEquals(local("2026-10-17", 7, 0), alarms.single { it.kind == BandAlarmKind.START }.epochMillis)
        assertEquals(local("2026-10-16", 11, 30), alarms.single { it.kind == BandAlarmKind.FOLLOW_UP }.epochMillis)
    }

    @Test
    fun userSetNoonStartMovesTheMorningEnd() {
        val lateNoon = times.copy(noonStartMinute = 13 * 60)
        val now = local("2026-10-16", 6, 0)
        val alarms = nextBandAlarms(now, newYork, lateNoon, dailyMorning)
        assertEquals(local("2026-10-16", 13, 0), alarms.single { it.kind == BandAlarmKind.END }.epochMillis)
        assertEquals(local("2026-10-16", 12, 30), alarms.single { it.kind == BandAlarmKind.FOLLOW_UP }.epochMillis)
    }

    @Test
    fun everyTwoDaysSkipsTheOffDay() {
        val everyTwo = listOf(repeat(RepeatBand.MORNING, everyNDays = 2, anchor = day("2026-10-16")))
        val afterStart = local("2026-10-16", 8, 0)
        val alarms = nextBandAlarms(afterStart, newYork, times, everyTwo)
        assertEquals(local("2026-10-18", 7, 0), alarms.single { it.kind == BandAlarmKind.START }.epochMillis)
    }

    @Test
    fun reminderOffAndArchivedRepeatsDoNotSchedule() {
        val muted = listOf(repeat(RepeatBand.MORNING, reminderOn = false))
        val archived =
            listOf(repeat(RepeatBand.NOON).copy(archivedAtEpochMillis = 1L))
        assertTrue(nextBandAlarms(local("2026-10-16", 6, 0), newYork, times, muted).isEmpty())
        assertTrue(nextBandAlarms(local("2026-10-16", 6, 0), newYork, times, archived).isEmpty())
    }

    @Test
    fun springForwardStillHitsSevenLocal() {
        val now = local("2026-03-08", 6, 0)
        val start = nextAlarm(now, newYork, RepeatBand.MORNING, BandAlarmKind.START, 7 * 60, dailyMorning)
        assertEquals(local("2026-03-08", 7, 0), start?.epochMillis)
        val zoned = java.time.Instant.ofEpochMilli(start!!.epochMillis).atZone(newYork)
        assertEquals(LocalTime.of(7, 0), zoned.toLocalTime())
        assertEquals("2026-03-08", zoned.toLocalDate().toString())
    }

    @Test
    fun fallBackStillHitsSevenLocal() {
        val now = local("2026-11-01", 6, 0)
        val start = nextAlarm(now, newYork, RepeatBand.MORNING, BandAlarmKind.START, 7 * 60, dailyMorning)
        assertEquals(local("2026-11-01", 7, 0), start?.epochMillis)
        val zoned = java.time.Instant.ofEpochMilli(start!!.epochMillis).atZone(newYork)
        assertEquals(LocalTime.of(7, 0), zoned.toLocalTime())
    }

    @Test
    fun followUpIsOmittedWhenTheBandIsShorterThanThirtyMinutes() {
        val tight = BandTimes(morningStartMinute = 7 * 60, noonStartMinute = 7 * 60 + 20, nightStartMinute = 18 * 60)
        val alarms = nextBandAlarms(local("2026-10-16", 6, 0), newYork, tight, dailyMorning)
        assertNull(alarms.firstOrNull { it.kind == BandAlarmKind.FOLLOW_UP })
        assertTrue(alarms.any { it.kind == BandAlarmKind.START })
        assertTrue(alarms.any { it.kind == BandAlarmKind.END })
    }

    @Test
    fun invalidBandTimesScheduleNothing() {
        val invalid = BandTimes(morningStartMinute = 12 * 60, noonStartMinute = 7 * 60)
        assertTrue(nextBandAlarms(local("2026-10-16", 6, 0), newYork, invalid, dailyMorning).isEmpty())
    }

    private fun local(
        date: String,
        hour: Int,
        minute: Int,
    ): Long =
        LocalDate
            .parse(date)
            .atTime(hour, minute)
            .atZone(newYork)
            .toInstant()
            .toEpochMilli()

    private fun day(date: String): Long = LocalDate.parse(date).toEpochDay()

    private fun repeat(
        band: RepeatBand,
        everyNDays: Int = 1,
        anchor: Long = 0,
        reminderOn: Boolean = true,
    ) = Repeat(
        id = band.name,
        title = band.name,
        band = band,
        everyNDays = everyNDays,
        anchorEpochDay = anchor,
        reminderOn = reminderOn,
        sortOrder = 1,
        createdAtEpochMillis = 1,
        archivedAtEpochMillis = null,
    )
}
