// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.calendar

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.QuietButton
import java.time.LocalDate
import java.time.LocalTime

private const val DAY_MILLIS: Long = 86_400_000L

/** The Material date picker works in UTC midnights. This reads one back as a local date. */
internal fun pickerMillisToDate(utcMillis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(utcMillis, DAY_MILLIS))

internal fun dateToPickerMillis(date: LocalDate): Long = date.toEpochDay() * DAY_MILLIS

/**
 * Calendar for one local date. Days before [earliest] are disabled.
 * Days after [latest] are disabled when it is set. OK hands back the selected
 * day. Cancel and outside taps change nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickDateDialog(
    initial: LocalDate,
    earliest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    latest: LocalDate? = null,
) {
    val earliestDay = earliest.toEpochDay()
    val latestDay = latest?.toEpochDay()
    val first =
        maxOf(initial, earliest).let { date ->
            if (latest != null && date.isAfter(latest)) latest else date
        }
    val lastYear = latest?.year ?: earliest.year + 10
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = dateToPickerMillis(first),
            yearRange = earliest.year..maxOf(earliest.year, lastYear),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val day = Math.floorDiv(utcTimeMillis, DAY_MILLIS)
                        if (day < earliestDay) return false
                        return latestDay == null || day <= latestDay
                    }

                    override fun isSelectableYear(year: Int): Boolean {
                        if (year < earliest.year) return false
                        return latest == null || year <= latest.year
                    }
                },
        )
    val selected = state.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogActions(
                confirmEnabled = selected != null,
                onConfirm = { if (selected != null) onPick(pickerMillisToDate(selected)) },
                onCancel = onDismiss,
            )
        },
    ) {
        DatePicker(state = state)
    }
}

/** Clock for one local time, as minutes after midnight. 24-hour, like the labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickTimeDialog(
    initialMinuteOfDay: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initialMinuteOfDay / 60,
            initialMinute = initialMinuteOfDay % 60,
            is24Hour = true,
        )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogActions(
                onConfirm = { onPick(state.hour * 60 + state.minute) },
                onCancel = onDismiss,
            )
        },
        title = { Text("Pick a time") },
    ) {
        TimePicker(state = state)
    }
}

/** Cancel and OK as one equal-width row, so the gap between them is the button gap. */
@Composable
private fun DialogActions(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    ActionRow {
        QuietButton("Cancel", onClick = onCancel)
        QuietButton("OK", onClick = onConfirm, enabled = confirmEnabled)
    }
}

/** 09:00 on a later day. On today, the next full hour, so the first suggestion is not already past. */
internal fun suggestedMinuteOfDay(
    date: LocalDate,
    today: LocalDate,
    now: LocalTime,
): Int {
    if (date.isAfter(today)) return 9 * 60
    return ((now.hour + 1) * 60).coerceAtMost(23 * 60)
}
