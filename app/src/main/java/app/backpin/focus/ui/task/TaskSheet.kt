// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.task

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.backpin.focus.R
import app.backpin.focus.ui.TaskSheetRole
import app.backpin.focus.ui.TaskSheetUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.QuietButton

/**
 * Chip-row actions for one open task. Same QuietButton chips as the add-repeat
 * band and cadence rows. Mint is Done and Resume. Lavender is Interrupt.
 */
@Composable
internal fun TaskSheet(
    state: TaskSheetUi,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onEdit: () -> Unit,
    onInterrupt: () -> Unit,
    onToday: () -> Unit,
    onResume: () -> Unit,
) {
    ChipSheet(
        title = stringResource(R.string.task_sheet_title),
        subtitle = state.title,
        onDismiss = onDismiss,
    ) {
        ActionRow {
            when (state.role) {
                TaskSheetRole.TODAY_OPEN -> {
                    QuietButton(label = stringResource(R.string.action_start), onClick = onStart)
                    AccentButton(
                        label = stringResource(R.string.action_done),
                        tone = AccentTone.Mint,
                        onClick = onDone,
                    )
                    QuietButton(label = stringResource(R.string.action_later), onClick = onLater)
                    if (state.canInterrupt) {
                        AccentButton(
                            label = stringResource(R.string.action_interrupt),
                            tone = AccentTone.Lavender,
                            onClick = onInterrupt,
                        )
                    }
                    QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                }
                TaskSheetRole.TODAY_WAITING -> {
                    AccentButton(
                        label = stringResource(R.string.action_resume),
                        tone = AccentTone.Mint,
                        onClick = onResume,
                    )
                    QuietButton(label = stringResource(R.string.action_later), onClick = onLater)
                    QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                }
                TaskSheetRole.PILE -> {
                    QuietButton(label = stringResource(R.string.action_today), onClick = onToday)
                    QuietButton(label = stringResource(R.string.action_start), onClick = onStart)
                    if (state.canInterrupt) {
                        AccentButton(
                            label = stringResource(R.string.action_interrupt),
                            tone = AccentTone.Lavender,
                            onClick = onInterrupt,
                        )
                    }
                    QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                }
            }
        }
    }
}
