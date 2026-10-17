// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.domain

import java.time.Instant
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
 * Local minute-of-day from [clock] and [zone].
 */
fun currentMinuteOfDay(
    clock: AppClock,
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    val now = Instant.ofEpochMilli(clock.nowEpochMillis()).atZone(zone)
    return now.hour * 60 + now.minute
}

/**
 * Collapsed-header counts for one Home band, as in
 * `"Morning · 2 done · 1 MISSED"`. Copy lives in the UI.
 *
 * @property done Done tasks plus [RepeatDayStatus.DONE] rows.
 * @property missed [RepeatDayStatus.MISSED] rows. Tasks have no missed state.
 * @property left Open tasks plus [RepeatDayStatus.OPEN] rows.
 */
data class HomeBandCounts(
    val done: Int = 0,
    val missed: Int = 0,
    val left: Int = 0,
)

/**
 * One Home band: its tasks, that band's repeat rows, and collapsed counts.
 *
 * @property band Which Home band this group is.
 * @property tasks Tasks assigned here. The current band lists pinned Today
 * tasks first, then any other tasks whose time lands in this window.
 * @property repeats Today's [RepeatRow] values for this band, in the same
 * relative order as [RepeatRowsState.rows] (open, then missed, then done).
 * [HomeBand.ANYTIME] is always empty.
 * @property counts Done, missed, and left for the collapsed line.
 */
data class HomeBandGroup(
    val band: HomeBand,
    val tasks: List<Task> = emptyList(),
    val repeats: List<RepeatRow> = emptyList(),
    val counts: HomeBandCounts = HomeBandCounts(),
)

/**
 * Home tasks and today's repeats grouped by band.
 *
 * @property morning Tasks and repeats in the morning window.
 * @property noon Tasks and repeats in the noon window.
 * @property night Tasks and repeats in the night window.
 * @property anytime Untimed tasks and times outside every window.
 * @property current Timed band that contains now, or [HomeBand.ANYTIME]
 * when no window is current.
 */
data class HomeBands(
    val morning: HomeBandGroup = HomeBandGroup(HomeBand.MORNING),
    val noon: HomeBandGroup = HomeBandGroup(HomeBand.NOON),
    val night: HomeBandGroup = HomeBandGroup(HomeBand.NIGHT),
    val anytime: HomeBandGroup = HomeBandGroup(HomeBand.ANYTIME),
    val current: HomeBand = HomeBand.ANYTIME,
) {
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
 * Tasks whose ids are in [pinnedTaskIds] are moved to the top of the
 * current band, in the order they appear in [tasks], regardless of their
 * own time. When no timed band is current they pin to [HomeBand.ANYTIME].
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
    val current = currentHomeBand(minuteOfDay, times) ?: HomeBand.ANYTIME
    val pinnedIds = pinnedTaskIds.toSet()
    val pinned = tasks.filter { it.id in pinnedIds }
    val restByBand =
        tasks
            .filter { it.id !in pinnedIds }
            .groupBy { homeBandFor(it.scheduledAtMinuteOfDay, times) }
    val repeatsByBand = rows.rows.groupBy { it.repeat.band.toHomeBand() }

    fun group(band: HomeBand): HomeBandGroup {
        val natural = restByBand[band].orEmpty()
        val bandTasks = if (band == current) pinned + natural else natural
        val bandRepeats = repeatsByBand[band].orEmpty()
        return HomeBandGroup(
            band = band,
            tasks = bandTasks,
            repeats = bandRepeats,
            counts = homeBandCounts(bandTasks, bandRepeats),
        )
    }

    return HomeBands(
        morning = group(HomeBand.MORNING),
        noon = group(HomeBand.NOON),
        night = group(HomeBand.NIGHT),
        anytime = group(HomeBand.ANYTIME),
        current = current,
    )
}

/**
 * Home grouping for the Today pile: open Today tasks, pinned to the
 * current band (or Anytime when no band is current), plus today's
 * repeat rows split by band.
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
 */
fun homeBandCounts(
    tasks: List<Task>,
    repeats: List<RepeatRow>,
): HomeBandCounts = HomeBandCounts(
    done = tasks.count { it.status == TaskStatus.DONE } + repeats.count { it.status == RepeatDayStatus.DONE },
    missed = repeats.count { it.status == RepeatDayStatus.MISSED },
    left = tasks.count { it.status == TaskStatus.OPEN } + repeats.count { it.status == RepeatDayStatus.OPEN },
)
