// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.backpin.focus.R
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.MAX_COUNTDOWN_REARM_DAYS
import app.backpin.focus.domain.MIN_COUNTDOWN_DAYS
import app.backpin.focus.domain.isCountdownRearmDay
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.ButtonGap
import java.time.LocalDate

/**
 * Where an arrived event goes instead of Done: Today, Later, or a new countdown day.
 * Each one closes the event and leaves the paused task waiting for Resume.
 * Emits its buttons into the caller's actions, next to Done.
 * [onCountDownAgain] opens the resume sheet; host that sheet outside card actions
 * so its buttons keep sheet height.
 */
@Composable
internal fun EventDetachButtons(
    taskId: String,
    onDetach: (String, DetachDestination) -> Unit,
    onCountDownAgain: () -> Unit,
    showCountDownAgain: Boolean = true,
) {
    QuietButton(stringResource(R.string.action_today), onClick = { onDetach(taskId, DetachDestination.Today) })
    QuietButton(stringResource(R.string.action_later), onClick = { onDetach(taskId, DetachDestination.Later) })
    if (showCountDownAgain) {
        QuietButton(stringResource(R.string.action_count_down_again), onClick = onCountDownAgain)
    }
}

/**
 * Hosts the resume sheet beside card actions so the sheet does not inherit card-action size.
 * [content] receives the click that opens the sheet.
 */
@Composable
internal fun EventResumeHost(
    taskId: String,
    title: String,
    suggestedDays: Int?,
    today: LocalDate,
    onDetach: (String, DetachDestination) -> Unit,
    content: @Composable (onCountDownAgain: () -> Unit, resumeOpen: Boolean) -> Unit,
) {
    var open by rememberSaveable(taskId) { mutableStateOf(false) }
    content({ open = true }, open)
    if (open) {
        ResumeCountdownSheet(
            title = title,
            suggestedDays = suggestedDays,
            today = today,
            onDismiss = { open = false },
            onCommit = { day ->
                open = false
                onDetach(taskId, DetachDestination.Countdown(day))
            },
        )
    }
}

private const val DEFAULT_RESUME_DAYS = 7

/**
 * One mint Again in N days choice, a typed days field, and Pick a date.
 * N is the event's last interval, or 7. The Close arrow dismisses without saving.
 */
@Composable
internal fun ResumeCountdownSheet(
    title: String,
    suggestedDays: Int?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onCommit: (Long) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    var daysText by rememberSaveable { mutableStateOf("") }
    var daysInvalid by rememberSaveable { mutableStateOf(false) }
    val sent = remember { booleanArrayOf(false) }
    val interval =
        suggestedDays
            ?.takeIf { it >= MIN_COUNTDOWN_DAYS }
            ?.let { minOf(it, MAX_COUNTDOWN_REARM_DAYS) }
            ?: DEFAULT_RESUME_DAYS
    val landing = today.plusDays(interval.toLong())

    fun commit(day: Long) {
        if (sent[0] || !isCountdownRearmDay(day, today)) return
        sent[0] = true
        onCommit(day)
    }

    fun commitInDays(days: Int) {
        commit(today.plusDays(days.toLong()).toEpochDay())
    }

    fun commitTyped() {
        val days = daysText.toIntOrNull()
        if (days == null || days !in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_REARM_DAYS) {
            daysInvalid = true
            return
        }
        commitInDays(days)
    }

    ChipSheet(
        title = title,
        onDismiss = onDismiss,
        gesturesEnabled = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ButtonGap),
        ) {
            CaptureChoice(
                label = againInDaysLabel(interval, today),
                onClick = { commitInDays(interval) },
                tone = ChoiceTone.Mint,
                filled = true,
            )
            OutlinedTextField(
                value = daysText,
                onValueChange = {
                    daysText = it.filter(Char::isDigit)
                    daysInvalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.resume_sheet_days_label)) },
                placeholder = { Text(stringResource(R.string.resume_sheet_days_placeholder)) },
                supportingText =
                if (daysInvalid) {
                    {
                        Text(
                            stringResource(
                                R.string.resume_sheet_days_error,
                                MIN_COUNTDOWN_DAYS,
                                MAX_COUNTDOWN_REARM_DAYS,
                            ),
                        )
                    }
                } else {
                    null
                },
                isError = daysInvalid,
                singleLine = true,
                keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitTyped() }),
            )
            CaptureChoice(
                label = stringResource(R.string.action_pick_a_date),
                onClick = { picking = true },
            )
        }
    }
    if (picking) {
        PickDateDialog(
            initial = landing,
            earliest = today.plusDays(MIN_COUNTDOWN_DAYS.toLong()),
            latest = today.plusDays(MAX_COUNTDOWN_REARM_DAYS.toLong()),
            onPick = { date ->
                picking = false
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}
