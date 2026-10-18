// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Home grouping bands. Morning, noon, and night use the same windows as
 * [bandWindow]. [ANYTIME] is untimed tasks and times outside every window.
 * Repeats stay on [RepeatBand] and never appear under [ANYTIME].
 *
 * Order on Home is Morning, Noon, Night, Anytime.
 *
 * Lory switches Home between this grouping and the flat Today + REPEAT
 * lists using `HomeLayout` from `PreferencesDataStore.homeLayout` once
 * PR #29 (`cursor/theme-setting`) merges. Until then the grouping is
 * always produced alongside the existing flat data.
 */
enum class HomeBand {
    /** [BandTimes.morningStartMinute] inclusive to [BandTimes.noonStartMinute] exclusive. */
    MORNING,

    /** [BandTimes.noonStartMinute] inclusive to [BandTimes.nightStartMinute] exclusive. */
    NOON,

    /** [BandTimes.nightStartMinute] inclusive to [BandTimes.nightEndMinute] exclusive. */
    NIGHT,

    /** No time, or a time before morning or at or after night end. */
    ANYTIME,
}

/**
 * Where a Home band sits relative to now. The UI collapses [PAST],
 * expands [OPEN], shows a header-and-count on [FUTURE], and never
 * collapses [ALWAYS_OPEN] (Anytime).
 */
enum class BandPhase {
    /** Timed band that has already ended. Collapsed summary line. */
    PAST,

    /** The open timed band. Today tasks pin here. */
    OPEN,

    /** Timed band that has not started. Header with count. */
    FUTURE,

    /** Anytime. Always expanded. Never past or collapsed. */
    ALWAYS_OPEN,
}

/**
 * Display name for a Home band header. Morning, Noon, Night, Anytime.
 */
fun HomeBand.label(): String = when (this) {
    HomeBand.MORNING -> "Morning"
    HomeBand.NOON -> "Noon"
    HomeBand.NIGHT -> "Night"
    HomeBand.ANYTIME -> "Anytime"
}

/**
 * Maps a repeat band onto the matching Home band. Repeats have no Anytime.
 */
fun RepeatBand.toHomeBand(): HomeBand = when (this) {
    RepeatBand.MORNING -> HomeBand.MORNING
    RepeatBand.NOON -> HomeBand.NOON
    RepeatBand.NIGHT -> HomeBand.NIGHT
}

/**
 * Which Home band contains [scheduledAtMinuteOfDay] for [times].
 * Null, out of range, or outside every [bandWindow] is [HomeBand.ANYTIME].
 *
 * Windows match [bandWindow]: morning `[morningStart, noonStart)`,
 * noon `[noonStart, nightStart)`, night `[nightStart, nightEnd)`.
 */
fun homeBandFor(
    scheduledAtMinuteOfDay: Int?,
    times: BandTimes,
): HomeBand {
    val minute = scheduledAtMinuteOfDay ?: return HomeBand.ANYTIME
    return currentHomeBand(minute, times) ?: HomeBand.ANYTIME
}

/**
 * Which timed Home band contains [minuteOfDay], or null when that minute
 * is out of range, before morning, or at or after night end.
 */
fun currentHomeBand(
    minuteOfDay: Int,
    times: BandTimes,
): HomeBand? {
    if (minuteOfDay !in 0 until MINUTES_PER_DAY) return null
    return when {
        minuteOfDay in times.morningStartMinute until times.noonStartMinute -> HomeBand.MORNING
        minuteOfDay in times.noonStartMinute until times.nightStartMinute -> HomeBand.NOON
        minuteOfDay in times.nightStartMinute until times.nightEndMinute -> HomeBand.NIGHT
        else -> null
    }
}

/**
 * Open timed band for Home. Same as [currentHomeBand] when a window
 * contains now. When no window contains now (before morning or at or
 * after night end) this is [HomeBand.MORNING] — the next band, which
 * after night end is tomorrow's morning. Never [HomeBand.ANYTIME].
 */
fun openHomeBand(
    minuteOfDay: Int,
    times: BandTimes,
): HomeBand = currentHomeBand(minuteOfDay, times) ?: HomeBand.MORNING

/**
 * Position of [band] relative to now. Anytime is always [BandPhase.ALWAYS_OPEN].
 * When no timed window contains now, Morning is [BandPhase.OPEN] and
 * Noon and Night are [BandPhase.FUTURE].
 */
fun homeBandPhase(
    band: HomeBand,
    openBand: HomeBand,
    timedCurrent: HomeBand?,
): BandPhase {
    if (band == HomeBand.ANYTIME) return BandPhase.ALWAYS_OPEN
    if (timedCurrent == null) {
        return if (band == HomeBand.MORNING) BandPhase.OPEN else BandPhase.FUTURE
    }
    return when {
        band == openBand -> BandPhase.OPEN
        band.ordinal < openBand.ordinal -> BandPhase.PAST
        else -> BandPhase.FUTURE
    }
}

/**
 * Local minute-of-day from [clock] and [zone].
 */
fun currentMinuteOfDay(
    clock: AppClock,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    val now = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone)
    return now.hour * 60 + now.minute
}

/** Local calendar day from [clock] and [zone]. */
fun localDateOf(
    clock: AppClock,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalDate = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone).toLocalDate()

/** Local clock time from [clock] and [zone]. */
fun localTimeOf(
    clock: AppClock,
    zone: ZoneId = ZoneId.systemDefault(),
): LocalTime = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone).toLocalTime()

/**
 * Millis until the next band bound by clock time: the earliest of
 * morning start, noon start, night start, and night end that is still
 * after now. List order does not matter. When none remain today, wraps
 * to tomorrow's earliest bound. Always at least 1.
 */
fun millisUntilNextBandBoundary(
    nowEpochMillis: Long,
    times: BandTimes,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zone)
    val today = now.toLocalDate()
    val nowMinute = now.hour * 60 + now.minute
    val bounds =
        listOf(
            times.morningStartMinute,
            times.noonStartMinute,
            times.nightStartMinute,
            times.nightEndMinute,
        )
    val nextMinute = bounds.filter { it > nowMinute }.minOrNull()
    val day = if (nextMinute != null) today else today.plusDays(1)
    val minute = nextMinute ?: bounds.min()
    val nextMillis =
        day.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    return (nextMillis - nowEpochMillis).coerceAtLeast(1L)
}

/**
 * Collapsed-header counts for one Home band, as in
 * `"Morning · 2 done · 1 MISSED"`. Copy lives in the UI.
 *
 * @property done Done tasks plus [RepeatDayStatus.DONE] rows.
 * @property missed [RepeatDayStatus.MISSED] rows. Tasks have no missed state.
 * @property left Still-unchecked items: open plus missed, for tasks and
 * repeat rows. Same rule as [RepeatRowsState.leftCount].
 */
data class HomeBandCounts(
    val done: Int = 0,
    val missed: Int = 0,
    val left: Int = 0,
)

/**
 * One Home band: its tasks, that band's repeat rows, collapsed counts,
 * and position relative to now.
 *
 * @property band Which Home band this group is.
 * @property tasks Tasks assigned here. The open band lists pinned Today
 * tasks first, then any other tasks whose time lands in this window.
 * @property repeats Today's [RepeatRow] values for this band, in the same
 * relative order as [RepeatRowsState.rows] (open, then missed, then done).
 * [HomeBand.ANYTIME] is always empty.
 * @property counts Done, missed, and left for the collapsed line.
 * @property phase Past, open, future, or always-open (Anytime).
 */
data class HomeBandGroup(
    val band: HomeBand,
    val tasks: List<Task> = emptyList(),
    val repeats: List<RepeatRow> = emptyList(),
    val counts: HomeBandCounts = HomeBandCounts(),
    val phase: BandPhase = BandPhase.FUTURE,
)

/**
 * Home tasks and today's repeats grouped by band.
 *
 * @property morning Tasks and repeats in the morning window.
 * @property noon Tasks and repeats in the noon window.
 * @property night Tasks and repeats in the night window.
 * @property anytime Untimed tasks and times outside every window.
 * @property openBand Timed band that is open now. Never [HomeBand.ANYTIME].
 * When no window contains now this is [HomeBand.MORNING].
 */
data class HomeBands(
    val morning: HomeBandGroup = HomeBandGroup(HomeBand.MORNING, phase = BandPhase.OPEN),
    val noon: HomeBandGroup = HomeBandGroup(HomeBand.NOON, phase = BandPhase.FUTURE),
    val night: HomeBandGroup = HomeBandGroup(HomeBand.NIGHT, phase = BandPhase.FUTURE),
    val anytime: HomeBandGroup = HomeBandGroup(HomeBand.ANYTIME, phase = BandPhase.ALWAYS_OPEN),
    val openBand: HomeBand = HomeBand.MORNING,
) {
    /**
     * Same as [openBand]. Never [HomeBand.ANYTIME].
     */
    val current: HomeBand
        get() = openBand

    /** The group for [band]. */
    fun inBand(band: HomeBand): HomeBandGroup = when (band) {
        HomeBand.MORNING -> morning
        HomeBand.NOON -> noon
        HomeBand.NIGHT -> night
        HomeBand.ANYTIME -> anytime
    }

    /** Morning, Noon, Night, Anytime. */
    fun inOrder(): List<HomeBandGroup> = listOf(morning, noon, night, anytime)
}

/**
 * Groups [tasks] and [rows] into Home bands.
 *
 * Each task lands in [homeBandFor] of its [Task.scheduledAtMinuteOfDay].
 * Tasks whose ids are in [pinnedTaskIds] are moved to the top of
 * [HomeBands.openBand], in the order they appear in [tasks], regardless
 * of their own time. When no timed band contains now they pin to
 * [HomeBand.MORNING], never Anytime.
 *
 * Repeat rows are split by [Repeat.band]. Order inside a band matches
 * [RepeatRowsState.rows].
 */
fun homeBands(
    tasks: List<Task>,
    rows: RepeatRowsState,
    times: BandTimes,
    minuteOfDay: Int,
    pinnedTaskIds: Collection<String> = emptyList(),
): HomeBands {
    val timedCurrent = currentHomeBand(minuteOfDay, times)
    val openBand = timedCurrent ?: HomeBand.MORNING
    val pinnedIds = pinnedTaskIds.toSet()
    val pinned = tasks.filter { it.id in pinnedIds }
    val restByBand =
        tasks
            .filter { it.id !in pinnedIds }
            .groupBy { homeBandFor(it.scheduledAtMinuteOfDay, times) }
    val repeatsByBand = rows.rows.groupBy { it.repeat.band.toHomeBand() }

    fun group(band: HomeBand): HomeBandGroup {
        val natural = restByBand[band].orEmpty()
        val bandTasks = if (band == openBand) pinned + natural else natural
        val bandRepeats = repeatsByBand[band].orEmpty()
        return HomeBandGroup(
            band = band,
            tasks = bandTasks,
            repeats = bandRepeats,
            counts = homeBandCounts(bandTasks, bandRepeats),
            phase = homeBandPhase(band, openBand, timedCurrent),
        )
    }

    return HomeBands(
        morning = group(HomeBand.MORNING),
        noon = group(HomeBand.NOON),
        night = group(HomeBand.NIGHT),
        anytime = group(HomeBand.ANYTIME),
        openBand = openBand,
    )
}

/**
 * Home grouping for the Today pile: open Today tasks, pinned to
 * [HomeBands.openBand] (Morning when no timed window contains now),
 * plus today's repeat rows split by band.
 */
fun homeBandsForToday(
    tasks: List<Task>,
    rows: RepeatRowsState,
    times: BandTimes,
    minuteOfDay: Int,
): HomeBands {
    val today =
        tasks
            .filter { it.status == TaskStatus.OPEN && it.bucket == TaskBucket.TODAY }
            .sortedBy { it.sortOrder }
    return homeBands(
        tasks = today,
        rows = rows,
        times = times,
        minuteOfDay = minuteOfDay,
        pinnedTaskIds = today.map { it.id },
    )
}

/**
 * Done, missed, and left for one band's tasks and repeat rows.
 * [HomeBandCounts.left] is open plus missed, matching [RepeatRowsState.leftCount].
 */
fun homeBandCounts(
    tasks: List<Task>,
    repeats: List<RepeatRow>,
): HomeBandCounts = HomeBandCounts(
    done = tasks.count { it.status == TaskStatus.DONE } + repeats.count { it.status == RepeatDayStatus.DONE },
    missed = repeats.count { it.status == RepeatDayStatus.MISSED },
    left = tasks.count { it.status != TaskStatus.DONE } + repeats.count { it.status != RepeatDayStatus.DONE },
)
