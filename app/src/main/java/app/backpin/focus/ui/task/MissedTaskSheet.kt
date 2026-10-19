// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.backpin.focus.R
import app.backpin.focus.ui.MissedTaskSheetUi
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.QuietButton
import java.time.LocalDate

/**
 * A due scheduled task that never made Today. Three chips: Done now, Tomorrow,
 * and Pick a day. Tomorrow and Pick a day stay quiet. Never red.
 */
@Composable
internal fun MissedTaskSheet(
    state: MissedTaskSheetUi,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDoneNow: () -> Unit,
    onTomorrow: () -> Unit,
    onPickDay: (Long) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val tomorrow = today.plusDays(1)
    ChipSheet(
        title = stringResource(R.string.missed_task_sheet_title),
        subtitle = state.title,
        lede = stringResource(R.string.missed_task_sheet_lede),
        onDismiss = onDismiss,
    ) {
        ActionRow {
            AccentButton(
                label = stringResource(R.string.action_done_now),
                tone = AccentTone.Mint,
                onClick = onDoneNow,
            )
            QuietButton(
                label = stringResource(R.string.action_tomorrow),
                onClick = onTomorrow,
            )
            QuietButton(
                label = stringResource(R.string.action_pick_a_day),
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
                onPickDay(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}
