package app.backpin.focus.ui.calendar

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.LocalTime

private const val DAY_MILLIS: Long = 86_400_000L

/** The Material date picker works in UTC midnights. This reads one back as a local date. */
internal fun pickerMillisToDate(utcMillis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(utcMillis, DAY_MILLIS))

internal fun dateToPickerMillis(date: LocalDate): Long = date.toEpochDay() * DAY_MILLIS

/**
 * Calendar for one local date. Days before [earliest] are disabled.
 * OK hands back the selected day. Cancel and outside taps change nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickDateDialog(
    initial: LocalDate,
    earliest: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val earliestDay = earliest.toEpochDay()
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = dateToPickerMillis(maxOf(initial, earliest)),
            yearRange = earliest.year..(earliest.year + 10),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        Math.floorDiv(utcTimeMillis, DAY_MILLIS) >= earliestDay

                    override fun isSelectableYear(year: Int): Boolean = year >= earliest.year
                },
        )
    val selected = state.selectedDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (selected != null) onPick(pickerMillisToDate(selected)) },
                enabled = selected != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
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
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text("OK")
            }
        },
        title = { Text("Pick a time") },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        TimePicker(state = state)
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
