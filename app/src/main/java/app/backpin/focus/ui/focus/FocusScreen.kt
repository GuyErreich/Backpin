// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.elapsedMillis
import app.backpin.focus.domain.suggestedCountdownDays
import app.backpin.focus.domain.timeInFocusLabel
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.RecoveryUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.countdown.EventDetachButtons
import app.backpin.focus.ui.countdown.EventResumeHost
import app.backpin.focus.ui.home.nowLabel
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun FocusScreen(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Space * 2),
    ) {
        when {
            state.now != null -> ActiveCard(state, viewModel)
            state.recovery != null -> RecoveryCard(recovery = state.recovery, viewModel = viewModel, showCueEditor = true)
            else -> EmptyFocus(state, viewModel)
        }
    }
}

@Composable
private fun EmptyFocus(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
) {
    Text(
        "FOCUS",
        color = FocusColors.Live.Muted,
        style = MaterialTheme.typography.labelLarge,
        letterSpacing = 1.2.sp,
    )
    Text(
        "Nothing is in focus.",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Live.Surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
            Text(
                "Start a task from Home. This screen stays with that one task until you pause it or mark it done.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Resume never happens on its own.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.paused?.let { paused ->
                Text("Paused: ${paused.title}", style = MaterialTheme.typography.titleLarge)
                CardActions {
                    QuietButton("Start", onClick = { viewModel.start(paused.taskId) })
                }
            }
        }
    }
}

@Composable
private fun ActiveCard(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
) {
    val now = state.now ?: return
    val label = nowLabel(now)
    val accent = FocusColors.Live.Lavender
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Edit") {
                                viewModel.openEdit(now.taskId)
                                true
                            },
                        )
                },
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Live.Surface,
        border = BorderStroke(2.dp, accent),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space * 2)) {
            Text(label, color = accent, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
            Text(
                now.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            TimeInFocus(now.accumulatedMillis, now.startedAtEpochMillis)
            ReturnNoteField(
                taskId = now.taskId,
                cue = now.returnCue.orEmpty(),
                onSave = { viewModel.updateCue(now.taskId, it) },
            )
            EventResumeHost(
                taskId = now.taskId,
                title = now.title,
                suggestedDays =
                    suggestedCountdownDays(
                        now.countdownIntervalDays,
                        state.preferences.lastCountdownDays,
                    ),
                today = viewModel.localDate(),
                onDetach = viewModel::detachEvent,
            ) { onCountDownAgain ->
                CardActions {
                    AccentButton(
                        "Done",
                        AccentTone.Mint,
                        onClick = viewModel::completeActive,
                    )
                    if (now.isEvent) {
                        EventDetachButtons(
                            taskId = now.taskId,
                            onDetach = viewModel::detachEvent,
                            onCountDownAgain = onCountDownAgain,
                        )
                    } else {
                        QuietButton("Pause", onClick = viewModel::pause)
                        AccentButton(
                            "Interrupt",
                            AccentTone.Lavender,
                            onClick = { viewModel.openCapture(interrupts = true) },
                        )
                        QuietButton("Switch", onClick = viewModel::openSwitch)
                    }
                    QuietButton("Edit", onClick = { viewModel.openEdit(now.taskId) })
                }
            }
        }
    }
}

@Composable
internal fun RecoveryCard(
    recovery: RecoveryUi,
    viewModel: FocusStackViewModel,
    showCueEditor: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Live.Surface,
        border = BorderStroke(2.dp, FocusColors.Live.Lavender),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space * 2)) {
            Text(
                "BEFORE THIS",
                color = FocusColors.Live.Lavender,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.2.sp,
            )
            Text(
                recovery.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (showCueEditor) {
                ReturnNoteField(
                    taskId = recovery.taskId,
                    cue = recovery.returnCue.orEmpty(),
                    onSave = { viewModel.updateCue(recovery.taskId, it) },
                )
            } else if (!recovery.returnCue.isNullOrBlank()) {
                Text(
                    "Return note: ${recovery.returnCue}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            CardActions {
                AccentButton(
                    "Resume",
                    AccentTone.Mint,
                    onClick = { viewModel.resume(recovery.frameId) },
                )
                QuietButton("Later", onClick = { viewModel.demote(recovery.taskId) })
            }
        }
    }
}

@Composable
private fun TimeInFocus(
    accumulatedMillis: Long,
    startedAtEpochMillis: Long,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtEpochMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val label =
        timeInFocusLabel(
            elapsedMillis(
                accumulatedMillis = accumulatedMillis,
                startedAtEpochMillis = startedAtEpochMillis,
                nowEpochMillis = now,
                running = true,
            ),
        )
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = FocusColors.Live.Muted,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
@Suppress("ktlint:standard:indent")
private fun ReturnNoteField(
    taskId: String,
    cue: String,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(taskId) { mutableStateOf(cue) }
    var lastSent by rememberSaveable(taskId) { mutableStateOf(cue.trim()) }
    var armed by rememberSaveable(taskId) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(taskId, cue) {
        if (armed) return@LaunchedEffect
        draft = cue
        lastSent = cue.trim()
    }
    fun commit() {
        val next = draft.trim()
        if (draft != next) draft = next
        if (next == lastSent) return
        lastSent = next
        onSave(next)
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier =
            Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        armed = true
                    } else if (armed) {
                        armed = false
                        commit()
                    }
                },
        label = { Text("Return note") },
        placeholder = { Text("What to pick up after this") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
    )
}
