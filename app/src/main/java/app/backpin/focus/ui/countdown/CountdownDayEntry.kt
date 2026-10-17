// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.backpin.focus.domain.MAX_COUNTDOWN_DAYS
import app.backpin.focus.domain.MIN_COUNTDOWN_DAYS
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.components.CaptureChoice
import java.time.LocalDate

/** Digits only, 0 through 999. A leading zero collapses so 25 stays 25. */
internal fun sanitizeCountdownDays(incoming: String): String {
    val digits = incoming.filter(Char::isDigit).take(3)
    if (digits.isEmpty()) return ""
    val value = digits.toIntOrNull() ?: return ""
    return value.coerceIn(0, MAX_COUNTDOWN_DAYS).toString()
}

internal fun countdownDays(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS }

/** Resume one-tap label: the stored count and the day that count lands on. */
internal fun againInDaysLabel(
    days: Int,
    today: LocalDate,
): String {
    val day = today.plusDays(days.toLong()).toEpochDay()
    return "Again in $days days · ${scheduleMomentLabel(day, null)}"
}

/** Resume typed-days label. Updates as the field changes. 0 when the field is empty. */
internal fun startInDaysLabel(daysText: String): String {
    val shown = daysText.toIntOrNull() ?: 0
    return "Start in $shown days"
}

/** Number of days after today. Done on the keyboard is the choice. Pick a date opens the calendar. */
@Composable
internal fun CountdownDayEntry(
    daysText: String,
    onDaysChange: (String) -> Unit,
    shownDay: Long?,
    onDone: () -> Unit,
    onPickDate: () -> Unit,
    afterField: @Composable () -> Unit = {},
) {
    OutlinedTextField(
        value = daysText,
        onValueChange = onDaysChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Days from today") },
        placeholder = { Text("25") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
    if (shownDay != null) {
        Text(scheduleMomentLabel(shownDay, null), style = MaterialTheme.typography.bodyLarge)
    }
    afterField()
    CaptureChoice("Pick a date", onClick = onPickDate)
}
