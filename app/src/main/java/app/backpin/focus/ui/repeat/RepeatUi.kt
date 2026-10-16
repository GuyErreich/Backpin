package app.backpin.focus.ui.repeat

import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.label

internal fun repeatsLeftLabel(state: RepeatRowsState): String {
    if (state.total == 0) return ""
    return "${state.leftCount} of ${state.total} left"
}

internal fun repeatBandMark(band: RepeatBand): String = band.name

internal fun everyNDaysLabel(cadenceDays: Int): String? =
    if (cadenceDays <= 1) null else "every $cadenceDays days"

internal fun missedRepeatCopy(band: RepeatBand): String =
    "Missed at ${band.label().lowercase()} · muted until end of day"

internal fun repeatRowDescription(row: RepeatRow): String {
    val interval = everyNDaysLabel(row.cadenceDays)?.let { ". $it" }.orEmpty()
    val title = row.repeat.title
    val band = row.repeat.band.label()
    return when (row.status) {
        RepeatDayStatus.OPEN -> "$title. $band$interval. Check off."
        RepeatDayStatus.DONE -> "$title. $band$interval. Done."
        RepeatDayStatus.MISSED -> "$title. ${missedRepeatCopy(row.repeat.band)}. Check off."
    }
}
