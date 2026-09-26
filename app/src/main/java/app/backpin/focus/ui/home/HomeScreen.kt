package app.backpin.focus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.elapsedMillis
import app.backpin.focus.domain.formatElapsed
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.focus.RecoveryCard
import app.backpin.focus.ui.motion.rememberReducedMotion
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
    val reducedMotion = rememberReducedMotion()
    var moveTaskId by remember { mutableStateOf<String?>(null) }
    val filled = state.todaySlots.mapNotNull { slot -> slot.task?.let { slot to it } }
    val filledIds = filled.map { it.second.id }
    val hasEmptySlot = state.todaySlots.any { it.task == null }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space)) {
            Text(
                text = "FOCUS STACK",
                style = MaterialTheme.typography.labelLarge,
                color = FocusColors.Muted,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = "Keep your place.",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!state.ready) {
            Text("Loading your place…", style = MaterialTheme.typography.bodyLarge)
        } else {
            NowCard(state, viewModel, onOpenFocus, reducedMotion)
            TodaySection(
                filled = filled,
                hasEmptySlot = hasEmptySlot,
                reducedMotion = reducedMotion,
                onOpenFocus = onOpenFocus,
                onStart = viewModel::start,
                onDone = viewModel::markDone,
                onLater = viewModel::demote,
                onMove = { moveTaskId = it },
            )
            HorizontalDivider(color = FocusColors.Outline)
            BucketSection(
                title = "LATER",
                count = state.laterCount,
                expanded = state.laterExpanded,
                tasks = state.laterTasks,
                emptyLabel = "Nothing parked for later.",
                reducedMotion = reducedMotion,
                onToggle = viewModel::toggleLater,
                onPromote = viewModel::promote,
                onDone = viewModel::markDone,
                onLater = null,
            )
            BucketSection(
                title = "INBOX",
                count = state.inboxCount,
                expanded = state.inboxExpanded,
                tasks = state.inboxTasks,
                emptyLabel = "Inbox is clear.",
                reducedMotion = reducedMotion,
                onToggle = viewModel::toggleInbox,
                onPromote = viewModel::promote,
                onDone = viewModel::markDone,
                onLater = viewModel::demote,
            )
        }
    }

    moveTaskId?.let { taskId ->
        val index = filledIds.indexOf(taskId)
        val title = filled.getOrNull(index)?.second?.title ?: "This task"
        AlertDialog(
            onDismissRequest = { moveTaskId = null },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.Text,
            textContentColor = FocusColors.Muted,
            title = { Text("Move") },
            text = { Text("Reorder “$title” in Today, or park it in Later.") },
            confirmButton = {
                Column {
                    if (index > 0) {
                        TextButton(
                            onClick = {
                                viewModel.reorderToday(move(filledIds, index, index - 1))
                                moveTaskId = null
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Move up") }
                    }
                    if (index in 0 until filledIds.lastIndex) {
                        TextButton(
                            onClick = {
                                viewModel.reorderToday(move(filledIds, index, index + 1))
                                moveTaskId = null
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("Move down") }
                    }
                    TextButton(
                        onClick = {
                            viewModel.demote(taskId)
                            moveTaskId = null
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("To Later") }
                }
            },
            dismissButton = {
                TextButton(onClick = { moveTaskId = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun move(
    ids: List<String>,
    from: Int,
    to: Int,
): List<String> {
    if (from !in ids.indices || to !in ids.indices) return ids
    val next = ids.toMutableList()
    val id = next.removeAt(from)
    next.add(to, id)
    return next
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowCard(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
    reducedMotion: Boolean,
) {
    val now = state.now
    val recovery = state.recovery
    when {
        now != null -> {
            val accent = if (now.isInterruption) FocusColors.Amber else FocusColors.Mint
            val label = if (now.isInterruption) "INTERRUPTED" else "NOW"
            SwipeRevealCard(
                enableDone = true,
                enableLater = true,
                reducedMotion = reducedMotion,
                onDone = viewModel::completeActive,
                onLater = { viewModel.demote(now.taskId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "$label. ${now.title}. Opens focus." }
                            .clickable(role = Role.Button, onClick = onOpenFocus),
                    shape = RoundedCornerShape(CardRadius),
                    color = FocusColors.Surface,
                    border = BorderStroke(2.dp, accent),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                        Text(label, color = accent, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                        Text(now.title, style = MaterialTheme.typography.headlineMedium)
                        ElapsedText(
                            accumulatedMillis = now.accumulatedMillis,
                            startedAtEpochMillis = now.startedAtEpochMillis,
                            running = true,
                            clock = true,
                        )
                        if (!now.returnCue.isNullOrBlank()) {
                            Text(now.returnCue, style = MaterialTheme.typography.bodyLarge)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space),
                            verticalArrangement = Arrangement.spacedBy(Space),
                        ) {
                            MintButton("Done", onClick = viewModel::completeActive)
                            MintButton(
                                "Interrupt",
                                onClick = viewModel::openCapture,
                                container = FocusColors.Amber,
                                content = FocusColors.OnAmber,
                            )
                            QuietButton("Later", onClick = { viewModel.demote(now.taskId) })
                        }
                    }
                }
            }
        }
        recovery != null -> RecoveryCard(recovery = recovery, viewModel = viewModel, showCueEditor = true)
        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Surface,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text("NOW", color = FocusColors.Muted, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                    Text("Nothing in focus.", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Capture a thought, or add one to Today.",
                        style = MaterialTheme.typography.bodyLarge,
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
private fun TodaySection(
    filled: List<Pair<SlotUi, TaskUi>>,
    hasEmptySlot: Boolean,
    reducedMotion: Boolean,
    onOpenFocus: () -> Unit,
    onStart: (String) -> Unit,
    onDone: (String) -> Unit,
    onLater: (String) -> Unit,
    onMove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        SectionLabel("TODAY", "3 slots")
        filled.forEachIndexed { index, (slot, task) ->
            TodayRow(
                index = index,
                slot = slot,
                task = task,
                reducedMotion = reducedMotion,
                onOpen = onOpenFocus,
                onStart = { onStart(task.id) },
                onDone = { onDone(task.id) },
                onLater = { onLater(task.id) },
                onMove = { onMove(task.id) },
            )
        }
        if (hasEmptySlot) {
            EmptyTodaySlot()
        }
    }
}

@Composable
private fun TodayRow(
    index: Int,
    slot: SlotUi,
    task: TaskUi,
    reducedMotion: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onMove: () -> Unit,
) {
    val description =
        "Today position ${index + 1}. ${slot.role.name.lowercase()}. ${task.title}. Swipe right for done, swipe left for later."
    SwipeRevealCard(
        enableDone = true,
        enableLater = true,
        reducedMotion = reducedMotion,
        onDone = onDone,
        onLater = onLater,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description }
                    .then(
                        if (slot.role == SlotRole.NOW) {
                            Modifier.clickable(role = Role.Button, onClick = onOpen)
                        } else {
                            Modifier
                        },
                    ),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RoleLabel(slot.role)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (slot.role == SlotRole.OPEN) {
                        QuietButton("Start", onClick = onStart)
                    }
                    QuietButton("Done", onClick = onDone)
                    QuietButton("Later", onClick = onLater)
                    QuietButton("Move", onClick = onMove)
                }
            }
        }
    }
}

@Composable
private fun EmptyTodaySlot() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .semantics { contentDescription = "Empty Today slot." }
                .dashedSlot(FocusColors.Outline),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            "Empty slot",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = FocusColors.Muted,
        )
    }
}

private fun Modifier.dashedSlot(color: Color): Modifier =
    this.drawBehind {
        val stroke = 1.5.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(CardRadius.toPx(), CardRadius.toPx()),
            style =
                Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
                ),
        )
    }

@Composable
private fun RoleLabel(role: SlotRole) {
    val (text, color) =
        when (role) {
            SlotRole.NOW -> "NOW" to FocusColors.Mint
            SlotRole.WAITING -> "Waiting" to FocusColors.Lavender
            SlotRole.OPEN -> "" to FocusColors.Muted
            SlotRole.EMPTY -> "" to FocusColors.Muted
        }
    if (text.isNotEmpty()) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.sp)
    }
}

@Composable
private fun SectionLabel(
    title: String,
    trailing: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = FocusColors.Muted,
            letterSpacing = 1.2.sp,
            modifier = Modifier.semantics { heading() },
        )
        Text(trailing, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
    }
}

/**
 * Later and Inbox each own a titled panel. They are not extra Today slots,
 * and nothing in the panel can be dropped into another bucket.
 */
@Composable
private fun BucketSection(
    title: String,
    count: Int,
    expanded: Boolean,
    tasks: List<TaskUi>,
    emptyLabel: String,
    reducedMotion: Boolean,
    onToggle: () -> Unit,
    onPromote: (String) -> Unit,
    onDone: (String) -> Unit,
    onLater: ((String) -> Unit)?,
) {
    val action = if (expanded) "Hide" else "Review"
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        SectionLabel(title, count.toString())
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
            border = BorderStroke(1.dp, FocusColors.Outline),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(Space),
            ) {
                TextButton(
                    onClick = onToggle,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = "$title, $count. ${if (expanded) "Collapse" else "Review"}"
                            },
                ) {
                    Text(action)
                }
                if (expanded) {
                    if (tasks.isEmpty()) {
                        Text(
                            emptyLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    tasks.forEach { task ->
                        SwipeRevealCard(
                            enableDone = true,
                            enableLater = onLater != null,
                            reducedMotion = reducedMotion,
                            onDone = { onDone(task.id) },
                            onLater = { onLater?.invoke(task.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = FocusColors.SurfaceHigh,
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onPromote(task.id) },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text("+ Today")
                                        }
                                        TextButton(
                                            onClick = { onDone(task.id) },
                                            modifier = Modifier.heightIn(min = 48.dp),
                                        ) {
                                            Text("Done")
                                        }
                                        if (onLater != null) {
                                            TextButton(
                                                onClick = { onLater(task.id) },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text("Later")
                                            }
                                        }
                                    }
                                }
                            }
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
    clock: Boolean = false,
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
    val minutes = elapsed.coerceAtLeast(0) / 60_000L
    val hours = minutes / 60L
    val remainder = minutes % 60L
    val shown = if (clock) "${hours}h ${remainder}m" else formatElapsed(elapsed)
    Text(
        shown,
        style = MaterialTheme.typography.bodyLarge,
        color = FocusColors.Muted,
        modifier =
            Modifier.semantics {
                contentDescription = "Elapsed $hours hours $remainder minutes"
            },
    )
}

@Composable
internal fun MintButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = FocusColors.Mint,
    content: Color = FocusColors.OnMint,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
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
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label)
    }
}
