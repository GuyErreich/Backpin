// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.content.res.Resources
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.domain.label

internal fun repeatsLeftLabel(
    state: RepeatRowsState,
    resources: Resources,
): String {
    if (state.total == 0) return ""
    if (state.leftCount == 0) return resources.getString(R.string.repeat_all_done)
    return resources.getString(R.string.repeat_left_count, state.leftCount, state.total)
}

internal fun repeatBandMark(band: RepeatBand): String = band.name

internal fun everyNDaysLabel(
    cadenceDays: Int,
    resources: Resources,
): String? = if (cadenceDays <= 1) null else resources.getString(R.string.repeat_cadence, cadenceDays)

internal fun missedRepeatCopy(
    band: RepeatBand,
    resources: Resources,
): String = when (band) {
    RepeatBand.MORNING -> resources.getString(R.string.repeat_missed_morning)
    RepeatBand.NOON -> resources.getString(R.string.repeat_missed_noon)
    RepeatBand.NIGHT -> resources.getString(R.string.repeat_missed_night)
}

internal fun repeatRowDescription(
    row: RepeatRow,
    resources: Resources,
): String {
    val interval = everyNDaysLabel(row.cadenceDays, resources)
    val title = row.repeat.title
    val band = row.repeat.band.label()
    return when (row.status) {
        RepeatDayStatus.OPEN ->
            if (interval == null) {
                resources.getString(R.string.repeat_row_desc_open, title, band)
            } else {
                resources.getString(R.string.repeat_row_desc_open_cadence, title, band, interval)
            }
        RepeatDayStatus.DONE ->
            if (interval == null) {
                resources.getString(R.string.repeat_row_desc_done, title, band)
            } else {
                resources.getString(R.string.repeat_row_desc_done_cadence, title, band, interval)
            }
        RepeatDayStatus.MISSED ->
            resources.getString(
                R.string.repeat_row_desc_missed,
                title,
                missedRepeatCopy(row.repeat.band, resources),
            )
    }
}
