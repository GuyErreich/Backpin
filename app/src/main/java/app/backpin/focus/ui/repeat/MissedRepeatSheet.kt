// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.backpin.focus.R
import app.backpin.focus.ui.MissedRepeatSheetUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.QuietButton

/**
 * A missed repeat. Two chips: Done now and Skip today. The Home row stays
 * muted and checkable until local midnight. Never red.
 */
@Composable
internal fun MissedRepeatSheet(
    state: MissedRepeatSheetUi,
    onDismiss: () -> Unit,
    onDoneNow: () -> Unit,
    onSkipToday: () -> Unit,
) {
    ChipSheet(
        title = stringResource(R.string.missed_repeat_sheet_title),
        subtitle = state.title,
        lede = stringResource(R.string.missed_repeat_sheet_lede),
        onDismiss = onDismiss,
    ) {
        ActionRow {
            AccentButton(
                label = stringResource(R.string.action_done_now),
                tone = AccentTone.Mint,
                onClick = onDoneNow,
            )
            QuietButton(
                label = stringResource(R.string.action_skip_today),
                onClick = onSkipToday,
            )
        }
    }
}
