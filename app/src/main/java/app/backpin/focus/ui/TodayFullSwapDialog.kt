// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.backpin.focus.R
import app.backpin.focus.domain.ThemeId
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import app.backpin.focus.ui.theme.Space

/**
 * Full-Today choice. Dismiss, back, and Keep in Inbox leave [incoming] where it is.
 * Swap one out reveals Replace. Interrupt stays available while a Today task is active.
 */
@Composable
fun TodayFullSwapDialog(
    incoming: TaskUi,
    swapChoices: List<TaskUi>,
    canInterrupt: Boolean,
    onKeepInInbox: () -> Unit,
    onInterrupt: () -> Unit,
    onReplace: (String) -> Unit,
) {
    var pickingSwap by remember(incoming.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKeepInInbox,
        containerColor = FocusColors.Live.Surface,
        titleContentColor = FocusColors.Live.Text,
        textContentColor = FocusColors.Live.Muted,
        title = { Text(stringResource(R.string.swap_today_full_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space)) {
                Text(stringResource(R.string.swap_today_full_body, incoming.title))
                Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    if (canInterrupt) {
                        AccentButton(
                            label = "Interrupt current task",
                            tone = AccentTone.Lavender,
                            onClick = onInterrupt,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (pickingSwap) {
                        swapChoices.forEach { choice ->
                            QuietButton(
                                label = "Replace “${choice.title}”",
                                onClick = { onReplace(choice.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pickingSwap) {
                CaptureChoice(
                    label = stringResource(R.string.action_keep_in_inbox),
                    tone = ChoiceTone.Mint,
                    filled = true,
                    onClick = onKeepInInbox,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ActionRow {
                    QuietButton(
                        label = stringResource(R.string.action_swap_one_out),
                        onClick = { pickingSwap = true },
                    )
                    CaptureChoice(
                        label = stringResource(R.string.action_keep_in_inbox),
                        tone = ChoiceTone.Mint,
                        filled = true,
                        onClick = onKeepInInbox,
                    )
                }
            }
        },
    )
}

@Preview(name = "Swap dialog Signal")
@Composable
private fun TodayFullSwapDialogSignalPreview() {
    FocusStackTheme(ThemeId.SIGNAL) {
        TodayFullSwapDialog(
            incoming = TaskUi("new", "Call the school", null),
            swapChoices = previewSwapChoices(),
            canInterrupt = true,
            onKeepInInbox = {},
            onInterrupt = {},
            onReplace = {},
        )
    }
}

@Preview(name = "Swap dialog Ember")
@Composable
private fun TodayFullSwapDialogEmberPreview() {
    FocusStackTheme(ThemeId.EMBER) {
        TodayFullSwapDialog(
            incoming = TaskUi("new", "Call the school", null),
            swapChoices = previewSwapChoices(),
            canInterrupt = true,
            onKeepInInbox = {},
            onInterrupt = {},
            onReplace = {},
        )
    }
}

@Preview(name = "Kept in Inbox snackbar Signal")
@Composable
private fun KeptInInboxSnackbarSignalPreview() {
    KeptInInboxSnackbarPreview(ThemeId.SIGNAL)
}

@Preview(name = "Kept in Inbox snackbar Ember")
@Composable
private fun KeptInInboxSnackbarEmberPreview() {
    KeptInInboxSnackbarPreview(ThemeId.EMBER)
}

@Composable
private fun KeptInInboxSnackbarPreview(themeId: ThemeId) {
    FocusStackTheme(themeId) {
        val hostState = remember { SnackbarHostState() }
        val message = stringResource(R.string.kept_in_inbox)
        val undo = stringResource(R.string.action_undo)
        LaunchedEffect(hostState) {
            hostState.showSnackbar(message = message, actionLabel = undo, withDismissAction = true)
        }
        Scaffold(
            containerColor = FocusColors.Live.Midnight,
            snackbarHost = { FocusSnackbarHost(hostState) },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

private fun previewSwapChoices(): List<TaskUi> = listOf(
    TaskUi("a", "Write the notes", null),
    TaskUi("b", "Review CI", null),
    TaskUi("c", "Buy milk", null),
)
