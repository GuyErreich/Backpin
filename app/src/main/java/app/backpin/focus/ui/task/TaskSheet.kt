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
 * band and cadence rows. Mint is Resume. Now is Interrupt. Done on an unstarted
 * Today task stays quiet; the filled finish lives on the Now card and Focus.
 */
@Composable
internal fun TaskSheet(
    state: TaskSheetUi,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onInterrupt: () -> Unit,
    onToday: () -> Unit,
    onResume: () -> Unit,
    onBringBack: () -> Unit,
    onPause: () -> Unit = {},
    onSwitch: () -> Unit = {},
) {
    ChipSheet(
        title = stringResource(R.string.task_sheet_title),
        subtitle = state.title,
        onDismiss = onDismiss,
    ) {
        when (state.role) {
            TaskSheetRole.TODAY_OPEN -> {
                if (state.canInterrupt) {
                    ActionRow {
                        QuietButton(label = stringResource(R.string.action_start), onClick = onStart)
                        QuietButton(label = stringResource(R.string.action_bring_back), onClick = onBringBack)
                        AccentButton(
                            label = stringResource(R.string.action_interrupt),
                            tone = AccentTone.Lavender,
                            onClick = onInterrupt,
                        )
                        QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                        QuietButton(label = stringResource(R.string.action_done), onClick = onDone)
                    }
                } else {
                    ActionRow {
                        QuietButton(label = stringResource(R.string.action_bring_back), onClick = onBringBack)
                        QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                        QuietButton(label = stringResource(R.string.action_done), onClick = onDone)
                    }
                    ActionRow {
                        QuietButton(label = stringResource(R.string.action_start), onClick = onStart)
                    }
                }
            }
            TaskSheetRole.TODAY_WAITING -> {
                ActionRow {
                    AccentButton(
                        label = stringResource(R.string.action_resume),
                        tone = AccentTone.Mint,
                        onClick = onResume,
                    )
                    QuietButton(label = stringResource(R.string.action_bring_back), onClick = onBringBack)
                    QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                }
            }
            TaskSheetRole.PILE -> {
                ActionRow {
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
            TaskSheetRole.FOCUS -> {
                ActionRow {
                    QuietButton(label = stringResource(R.string.action_pause), onClick = onPause)
                    QuietButton(label = stringResource(R.string.action_switch), onClick = onSwitch)
                    QuietButton(label = stringResource(R.string.action_bring_back), onClick = onBringBack)
                    QuietButton(label = stringResource(R.string.action_edit), onClick = onEdit)
                }
            }
        }
    }
}
