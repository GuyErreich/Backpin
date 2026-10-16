package app.backpin.focus.domain

/**
 * Time of day a repeat belongs to. Times are user-set; these names stay fixed.
 * Morning ends when noon starts. Noon ends when night starts. Night ends at [BandTimes.nightEndMinute].
 */
enum class RepeatBand {
    MORNING,
    NOON,
    NIGHT,
}

/** How a due cycle ended. Written to [repeat_log]; nothing resets at midnight. */
enum class RepeatOutcome {
    DONE,
    MISSED,
}

/** What Home shows for a due repeat on the current local day. */
enum class RepeatDayStatus {
    OPEN,
    DONE,
    MISSED,
}

/**
 * A repeating item that never enters Today, never counts toward the Today
 * limit, and is never focus work. Due on day D when
 * `(D - [anchorEpochDay]) % [everyNDays] == 0`. [everyNDays] of 1 is daily.
 * [archivedAtEpochMillis] is a soft delete.
 */
data class Repeat(
    val id: String,
    val title: String,
    val band: RepeatBand,
    val everyNDays: Int,
    val anchorEpochDay: Long,
    val reminderOn: Boolean,
    val sortOrder: Long,
    val createdAtEpochMillis: Long,
    val archivedAtEpochMillis: Long?,
)

/** One done or missed cycle. The pair ([repeatId], [epochDay]) is unique. */
data class RepeatLog(
    val repeatId: String,
    val epochDay: Long,
    val outcome: RepeatOutcome,
    val atEpochMillis: Long,
)

/** A due repeat on a local day, with the status Home should show. */
data class RepeatOccurrence(
    val repeat: Repeat,
    val epochDay: Long,
    val status: RepeatDayStatus,
)

/**
 * Today's due repeats, split by band, in [Repeat.sortOrder].
 * Archived items are omitted. A miss stays [RepeatDayStatus.MISSED] through that day.
 */
data class TodaysRepeats(
    val morning: List<RepeatOccurrence> = emptyList(),
    val noon: List<RepeatOccurrence> = emptyList(),
    val night: List<RepeatOccurrence> = emptyList(),
) {
    fun inBand(band: RepeatBand): List<RepeatOccurrence> =
        when (band) {
            RepeatBand.MORNING -> morning
            RepeatBand.NOON -> noon
            RepeatBand.NIGHT -> night
        }
}

/**
 * Local minute-of-day starts for each band, plus when night ends.
 * Defaults: Morning 07:00, Noon 12:00, Night 18:00, Night ends 23:00.
 * A valid set is strictly increasing from morning through night end.
 */
data class BandTimes(
    val morningStartMinute: Int = DEFAULT_MORNING_START_MINUTE,
    val noonStartMinute: Int = DEFAULT_NOON_START_MINUTE,
    val nightStartMinute: Int = DEFAULT_NIGHT_START_MINUTE,
    val nightEndMinute: Int = DEFAULT_NIGHT_END_MINUTE,
) {
    fun isValid(): Boolean =
        morningStartMinute in 0 until noonStartMinute &&
            noonStartMinute < nightStartMinute &&
            nightStartMinute < nightEndMinute &&
            nightEndMinute in (nightStartMinute + 1) until MINUTES_PER_DAY

    companion object {
        const val DEFAULT_MORNING_START_MINUTE: Int = 7 * 60
        const val DEFAULT_NOON_START_MINUTE: Int = 12 * 60
        const val DEFAULT_NIGHT_START_MINUTE: Int = 18 * 60
        const val DEFAULT_NIGHT_END_MINUTE: Int = 23 * 60
        val DEFAULT: BandTimes = BandTimes()
    }
}

/** Start inclusive, end exclusive, both as local minutes of the day. */
data class BandWindow(
    val startMinute: Int,
    val endMinute: Int,
)

fun bandWindow(
    band: RepeatBand,
    times: BandTimes,
): BandWindow =
    when (band) {
        RepeatBand.MORNING -> BandWindow(times.morningStartMinute, times.noonStartMinute)
        RepeatBand.NOON -> BandWindow(times.noonStartMinute, times.nightStartMinute)
        RepeatBand.NIGHT -> BandWindow(times.nightStartMinute, times.nightEndMinute)
    }

/** Display name used in notification copy. Morning, Noon, Night. */
fun RepeatBand.label(): String =
    when (this) {
        RepeatBand.MORNING -> "Morning"
        RepeatBand.NOON -> "Noon"
        RepeatBand.NIGHT -> "Night"
    }

/**
 * A repeat is due on [day] when that day is on or after the anchor and
 * `(day - anchor) % everyNDays == 0`. [everyNDays] below 1 is treated as 1.
 */
fun isRepeatDue(
    anchorEpochDay: Long,
    everyNDays: Int,
    day: Long,
): Boolean {
    if (day < anchorEpochDay) return false
    val step = everyNDays.coerceAtLeast(1).toLong()
    return (day - anchorEpochDay) % step == 0L
}

/**
 * Next local day on or after [fromDay] when [repeat] is due.
 */
fun nextDueDay(
    repeat: Repeat,
    fromDay: Long,
): Long {
    val step = repeat.everyNDays.coerceAtLeast(1).toLong()
    if (fromDay <= repeat.anchorEpochDay) return repeat.anchorEpochDay
    val delta = fromDay - repeat.anchorEpochDay
    val rem = delta % step
    return if (rem == 0L) fromDay else fromDay + (step - rem)
}

/** Earliest due day on or after [fromDay] among [repeats], or null when the list is empty. */
fun nextDueDayFor(
    repeats: List<Repeat>,
    fromDay: Long,
): Long? = repeats.minOfOrNull { nextDueDay(it, fromDay) }

/**
 * Status for a due item on [epochDay]. A done log wins. A missed log, or no
 * log after the band has ended that day, is missed. Otherwise it is still open.
 */
fun repeatStatusOnDay(
    log: RepeatLog?,
    bandEndPassed: Boolean,
): RepeatDayStatus =
    when (log?.outcome) {
        RepeatOutcome.DONE -> RepeatDayStatus.DONE
        RepeatOutcome.MISSED -> RepeatDayStatus.MISSED
        null -> if (bandEndPassed) RepeatDayStatus.MISSED else RepeatDayStatus.OPEN
    }
