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
import java.time.LocalDate

/**
 * A due scheduled task that never made Today. Three chips: Done now, Tomorrow,
 * and Pick a day. Amber is the schedule pair. Never red.
 */
@Composable
internal fun MissedTaskSheet(
    state: MissedTaskSheetUi,
    onDismiss: () -> Unit,
    onDoneNow: () -> Unit,
    onTomorrow: () -> Unit,
    onPickDay: (Long) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
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
            AccentButton(
                label = stringResource(R.string.action_tomorrow),
                tone = AccentTone.Amber,
                onClick = onTomorrow,
            )
            AccentButton(
                label = stringResource(R.string.action_pick_a_day),
                tone = AccentTone.Amber,
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
