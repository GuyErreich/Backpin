package app.backpin.focus.ui.focus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.UNTITLED_INTERRUPTION
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.home.ElapsedText
import app.backpin.focus.ui.home.MintButton
import app.backpin.focus.ui.home.QuietButton
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

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
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(Space * 2),
    ) {
        when {
            state.recovery != null && state.now == null -> {
                RecoveryCard(state, viewModel)
            }
            state.now != null -> {
                ActiveCard(state, viewModel)
            }
            else -> {
                Text("Focus", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Nothing is in focus.",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "Start a Today task from Home. Resume never happens on its own.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.paused?.let { paused ->
                    Surface(
                        shape = RoundedCornerShape(CardRadius),
                        color = FocusColors.Surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                            Text("Paused", color = FocusColors.Muted, style = MaterialTheme.typography.labelLarge)
                            Text(paused.title, style = MaterialTheme.typography.titleLarge)
                            MintButton("Start", onClick = { viewModel.start(paused.taskId) })
                        }
                    }
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
    val accent = if (now.isInterruption) FocusColors.Amber else FocusColors.Mint
    val label = if (now.isInterruption) "Interrupted" else "Now"
    Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
    Text(
        now.title,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() },
    )
    ElapsedText(now.accumulatedMillis, now.startedAtEpochMillis, running = true)
    if (now.title == UNTITLED_INTERRUPTION) {
        UntitledField(now.taskId, viewModel)
    }
    CueField(
        taskId = now.taskId,
        cue = now.returnCue.orEmpty(),
        buttonLabel = "Save cue",
        onSave = { viewModel.updateCue(now.taskId, it) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        MintButton("Pause", onClick = viewModel::pause)
        MintButton("Done", onClick = viewModel::completeActive)
        QuietButton("Interrupt", onClick = viewModel::openCapture)
        QuietButton("Switch", onClick = viewModel::openSwitch)
    }
}

@Composable
private fun RecoveryCard(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
) {
    val recovery = state.recovery ?: return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Surface,
        border = BorderStroke(2.dp, FocusColors.Lavender),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
            Text(
                "Pick up the thread.",
                color = FocusColors.Lavender,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Before this:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                recovery.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (!recovery.returnCue.isNullOrBlank()) {
                Text(recovery.returnCue, style = MaterialTheme.typography.bodyLarge)
            }
            CueField(
                taskId = recovery.taskId,
                cue = recovery.returnCue.orEmpty(),
                buttonLabel = "Change cue",
                onSave = { viewModel.updateCue(recovery.taskId, it) },
            )
            MintButton(
                "Resume",
                onClick = { viewModel.resume(recovery.frameId) },
                container = FocusColors.Lavender,
                content = FocusColors.OnLavender,
            )
            QuietButton("Later", onClick = { viewModel.demote(recovery.taskId) })
        }
    }
}

@Composable
private fun CueField(
    taskId: String,
    cue: String,
    buttonLabel: String,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable(taskId) { mutableStateOf(cue) }
    LaunchedEffect(taskId, cue) {
        draft = cue
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Return cue") },
        placeholder = { Text("Next: …") },
        singleLine = true,
    )
    QuietButton(buttonLabel, onClick = { onSave(draft) })
}

@Composable
private fun UntitledField(
    taskId: String,
    viewModel: FocusStackViewModel,
) {
    var draft by rememberSaveable(taskId) { mutableStateOf("") }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Name this interruption") },
        singleLine = true,
    )
    QuietButton("Save title", onClick = { viewModel.rename(taskId, draft) })
}
