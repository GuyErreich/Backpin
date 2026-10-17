// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.backpin.focus.R
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.FocusColors
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
) {
    QuietButton(stringResource(R.string.action_today), onClick = { onDetach(taskId, DetachDestination.Today) })
    QuietButton(stringResource(R.string.action_later), onClick = { onDetach(taskId, DetachDestination.Later) })
    QuietButton(stringResource(R.string.action_count_down_again), onClick = onCountDownAgain)
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
    onDetach: (String, DetachDestination) -> Unit,
    content: @Composable (onCountDownAgain: () -> Unit) -> Unit,
) {
    var open by rememberSaveable(taskId) { mutableStateOf(false) }
    content { open = true }
    if (open) {
        ResumeCountdownSheet(
            title = title,
            suggestedDays = suggestedDays,
            onDismiss = { open = false },
            onCommit = { day ->
                open = false
                onDetach(taskId, DetachDestination.Countdown(day))
            },
        )
    }
}

/**
 * Again in N days when a count is known, then chips for Tomorrow, In 3 days,
 * Next week, and Pick a date. No typed days and no zero-day default.
 * Back closes without detaching.
 */
@Composable
internal fun ResumeCountdownSheet(
    title: String,
    suggestedDays: Int?,
    onDismiss: () -> Unit,
    onCommit: (Long) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val sent = remember { booleanArrayOf(false) }
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)

    fun commit(day: Long) {
        if (sent[0]) return
        sent[0] = true
        onCommit(day)
    }

    fun commitInDays(days: Int) {
        commit(today.plusDays(days.toLong()).toEpochDay())
    }

    ChipSheet(
        title = stringResource(R.string.resume_sheet_title),
        subtitle = title.takeIf { it.isNotBlank() },
        onDismiss = onDismiss,
    ) {
        if (suggestedDays != null) {
            ActionRow {
                AccentButton(
                    label =
                    stringResource(
                        R.string.resume_again_in_days,
                        suggestedDays,
                        scheduleMomentLabel(today.plusDays(suggestedDays.toLong()).toEpochDay(), null),
                    ),
                    tone = AccentTone.Lavender,
                    onClick = { commitInDays(suggestedDays) },
                )
            }
            Text(
                stringResource(R.string.resume_sheet_or_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = FocusColors.Live.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActionRow {
            QuietButton(
                label = stringResource(R.string.action_tomorrow),
                onClick = { commitInDays(1) },
            )
            QuietButton(
                label = stringResource(R.string.action_in_3_days),
                onClick = { commitInDays(3) },
            )
            QuietButton(
                label = stringResource(R.string.action_next_week),
                onClick = { commitInDays(7) },
            )
            QuietButton(
                label = stringResource(R.string.action_pick_a_date),
                onClick = { picking = true },
            )
        }
    }
    if (picking) {
        PickDateDialog(
            initial = tomorrow,
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}
