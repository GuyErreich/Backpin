package app.backpin.focus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.elapsedMillis
import app.backpin.focus.domain.formatElapsed
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = Space),
        verticalArrangement = Arrangement.spacedBy(Space),
    ) {
        Text(
            text = "Focus Stack",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Keep your place.",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (!state.ready) {
            Text("Loading your place…", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        NowSection(state, viewModel, onOpenFocus)
        Text(
            text = "Today",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Space).semantics { heading() },
        )
        state.todaySlots.forEachIndexed { index, slot ->
            TodaySlot(
                index = index,
                slot = slot,
                onStart = { id -> viewModel.start(id) },
                onLater = { id -> viewModel.demote(id) },
                onOpen = onOpenFocus,
            )
        }
        CountSection(
            title = "Later",
            count = state.laterCount,
            expanded = state.laterExpanded,
            tasks = state.laterTasks,
            actionLabel = "+ Today",
            emptyLabel = "Nothing parked for later.",
            onToggle = viewModel::toggleLater,
            onAction = viewModel::promote,
        )
        CountSection(
            title = "Inbox",
            count = state.inboxCount,
            expanded = state.inboxExpanded,
            tasks = state.inboxTasks,
            actionLabel = "+ Today",
            emptyLabel = "Inbox is clear.",
            onToggle = viewModel::toggleInbox,
            onAction = viewModel::promote,
        )
    }
}

@Composable
private fun NowSection(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
) {
    val now = state.now
    val recovery = state.recovery
    when {
        now != null -> {
            val accent = if (now.isInterruption) FocusColors.Amber else FocusColors.Mint
            val label = if (now.isInterruption) "Interrupted" else "Now"
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "$label. ${now.title}" }
                        .clickable(role = Role.Button, onClick = onOpenFocus),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Surface,
                border = BorderStroke(2.dp, accent),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
                    Text(now.title, style = MaterialTheme.typography.headlineMedium)
                    ElapsedText(
                        accumulatedMillis = now.accumulatedMillis,
                        startedAtEpochMillis = now.startedAtEpochMillis,
                        running = true,
                    )
                    if (!now.returnCue.isNullOrBlank()) {
                        Text(now.returnCue, style = MaterialTheme.typography.bodyLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space)) {
                        MintButton("Done", onClick = viewModel::completeActive)
                        QuietButton("Later", onClick = { viewModel.demote(now.taskId) })
                        QuietButton("Interrupt", onClick = viewModel::openCapture)
                    }
                }
            }
        }
        recovery != null -> {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onOpenFocus),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Surface,
                border = BorderStroke(2.dp, FocusColors.Lavender),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text("Pick up the thread.", color = FocusColors.Lavender, style = MaterialTheme.typography.labelLarge)
                    Text("Before this: ${recovery.title}", style = MaterialTheme.typography.headlineMedium)
                    if (!recovery.returnCue.isNullOrBlank()) {
                        Text(recovery.returnCue, style = MaterialTheme.typography.bodyLarge)
                    }
                    MintButton("Open recovery", onClick = onOpenFocus, container = FocusColors.Lavender, content = FocusColors.OnLavender)
                }
            }
        }
        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Surface,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text("Now", color = FocusColors.Muted, style = MaterialTheme.typography.labelLarge)
                    Text("Nothing is active.", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Promote a task into Today, then start it. Empty slots stay empty.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.paused?.let { paused ->
                        Text("Paused: ${paused.title}", style = MaterialTheme.typography.bodyLarge)
                        QuietButton("Start", onClick = { viewModel.start(paused.taskId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySlot(
    index: Int,
    slot: SlotUi,
    onStart: (String) -> Unit,
    onLater: (String) -> Unit,
    onOpen: () -> Unit,
) {
    val task = slot.task
    val description =
        if (task == null) {
            "Today slot ${index + 1} of 3, empty"
        } else {
            "Today slot ${index + 1} of 3, ${slot.role.name.lowercase()}, ${task.title}"
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        shape = RoundedCornerShape(CardRadius),
        color = if (slot.role == SlotRole.NOW) FocusColors.SurfaceHigh else FocusColors.Surface,
    ) {
        if (task == null) {
            Text(
                "Empty",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Muted,
            )
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RoleLabel(slot.role)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space)) {
                    if (slot.role == SlotRole.OPEN) {
                        QuietButton("Start", onClick = { onStart(task.id) })
                    }
                    if (slot.role == SlotRole.NOW) {
                        QuietButton("Open", onClick = onOpen)
                    }
                    if (slot.role != SlotRole.EMPTY) {
                        QuietButton("Later", onClick = { onLater(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleLabel(role: SlotRole) {
    val (text, color) =
        when (role) {
            SlotRole.NOW -> "Now" to FocusColors.Mint
            SlotRole.WAITING -> "Waiting" to FocusColors.Lavender
            SlotRole.OPEN -> "Today" to FocusColors.Muted
            SlotRole.EMPTY -> "" to FocusColors.Muted
        }
    if (text.isNotEmpty()) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CountSection(
    title: String,
    count: Int,
    expanded: Boolean,
    tasks: List<TaskUi>,
    actionLabel: String,
    emptyLabel: String,
    onToggle: () -> Unit,
    onAction: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = "$title, $count. ${if (expanded) "Collapse" else "Review"}" },
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (expanded) "$count  ·  Hide" else "$count",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusColors.Muted,
                )
            }
        }
        if (expanded) {
            if (tasks.isEmpty()) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
            }
            tasks.forEach { task ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CardRadius),
                    color = FocusColors.SurfaceHigh,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { onAction(task.id) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ElapsedText(
    accumulatedMillis: Long,
    startedAtEpochMillis: Long,
    running: Boolean,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running, startedAtEpochMillis) {
        if (!running) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsed =
        elapsedMillis(
            accumulatedMillis = accumulatedMillis,
            startedAtEpochMillis = startedAtEpochMillis,
            nowEpochMillis = now,
            running = running,
        )
    Text(
        formatElapsed(elapsed),
        style = MaterialTheme.typography.bodyLarge,
        color = FocusColors.Muted,
        modifier = Modifier.semantics { contentDescription = "Elapsed ${formatElapsed(elapsed)}" },
    )
}

@Composable
internal fun MintButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = FocusColors.Mint,
    content: androidx.compose.ui.graphics.Color = FocusColors.OnMint,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(label)
    }
}

@Composable
internal fun QuietButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label)
    }
}
