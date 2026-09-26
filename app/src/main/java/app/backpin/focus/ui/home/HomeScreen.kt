package app.backpin.focus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var drag by remember { mutableStateOf<DragSession?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var moveTaskId by remember { mutableStateOf<String?>(null) }
    val filled = state.todaySlots.mapNotNull { slot -> slot.task?.let { slot to it } }
    val filledIds = filled.map { it.second.id }
    val hasEmptySlot = state.todaySlots.any { it.task == null }
    val hitKey =
        drag?.let { session ->
            val point = Offset(session.windowX, session.windowY)
            listOf("empty", "today-0", "today-1", "today-2", "later").firstOrNull { key ->
                bounds[key]?.contains(point) == true
            }
        }
    val dragState = rememberUpdatedState(drag)
    val hitState = rememberUpdatedState(hitKey)
    LaunchedEffect(hitKey, drag?.taskId) {
        val session = drag ?: return@LaunchedEffect
        if (hitKey != null && validDrop(session, hitKey, filledIds)) {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.boundsInWindow().topLeft },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
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
            if (!state.ready) {
                Text("Loading your place…", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            NowCard(state, viewModel, onOpenFocus, reducedMotion)
            SectionLabel("TODAY", "3 slots")
            filled.forEachIndexed { index, (slot, task) ->
                val key = "today-$index"
                TodayRow(
                    index = index,
                    slot = slot,
                    task = task,
                    highlighted = hitKey == key && drag != null,
                    dimmed = drag?.taskId == task.id,
                    reducedMotion = reducedMotion,
                    onOpen = onOpenFocus,
                    onStart = { viewModel.start(task.id) },
                    onDone = { viewModel.markDone(task.id) },
                    onLater = { viewModel.demote(task.id) },
                    onMove = { moveTaskId = task.id },
                    onPosition = { bounds[key] = it },
                    onDragStart = { origin ->
                        drag =
                            DragSession(
                                taskId = task.id,
                                title = task.title,
                                origin = DragOrigin.TODAY,
                                windowX = origin.x,
                                windowY = origin.y,
                            )
                    },
                    onDragDelta = { delta ->
                        drag = drag?.let { it.copy(windowX = it.windowX + delta.x, windowY = it.windowY + delta.y) }
                    },
                    onDragEnd = {
                        val session = dragState.value
                        val keyHit = hitState.value
                        drag = null
                        if (session != null) commitDrop(session, keyHit, filledIds, viewModel)
                    },
                    onDragCancel = { drag = null },
                )
            }
            if (hasEmptySlot) {
                EmptyTodaySlot(
                    highlighted = hitKey == "empty" && drag != null,
                    onPosition = { bounds["empty"] = it },
                )
            }
            CountSection(
                title = "LATER",
                countLabel = state.laterCount.toString(),
                description = "Later, ${state.laterCount}. ${if (state.laterExpanded) "Collapse" else "Review"}",
                expanded = state.laterExpanded,
                tasks = state.laterTasks,
                emptyLabel = "Nothing parked for later.",
                highlighted = hitKey == "later" && drag?.origin == DragOrigin.TODAY,
                draggable = true,
                reducedMotion = reducedMotion,
                draggingId = drag?.taskId,
                onToggle = viewModel::toggleLater,
                onPosition = { bounds["later"] = it },
                onAction = viewModel::promote,
                onDone = viewModel::markDone,
                onLater = null,
                onDragStart = { task, origin ->
                    drag =
                        DragSession(
                            taskId = task.id,
                            title = task.title,
                            origin = DragOrigin.LATER,
                            windowX = origin.x,
                            windowY = origin.y,
                        )
                },
                onDragDelta = { delta ->
                    drag = drag?.let { it.copy(windowX = it.windowX + delta.x, windowY = it.windowY + delta.y) }
                },
                onDragEnd = {
                    val session = dragState.value
                    val keyHit = hitState.value
                    drag = null
                    if (session != null) commitDrop(session, keyHit, filledIds, viewModel)
                },
                onDragCancel = { drag = null },
            )
            CountSection(
                title = "INBOX",
                countLabel = if (state.inboxExpanded) "${state.inboxCount}  ·  Hide" else "${state.inboxCount}  ·  Review",
                description = "Inbox, ${state.inboxCount}. ${if (state.inboxExpanded) "Collapse" else "Review"}",
                expanded = state.inboxExpanded,
                tasks = state.inboxTasks,
                emptyLabel = "Inbox is clear.",
                highlighted = false,
                draggable = false,
                reducedMotion = reducedMotion,
                draggingId = null,
                onToggle = viewModel::toggleInbox,
                onPosition = {},
                onAction = viewModel::promote,
                onDone = viewModel::markDone,
                onLater = viewModel::demote,
                onDragStart = { _, _ -> },
                onDragDelta = {},
                onDragEnd = {},
                onDragCancel = {},
            )
        }
        val session = drag
        if (session != null) {
            val density = LocalDensity.current
            val yOffset =
                with(density) {
                    (session.windowY - rootOrigin.y - 28.dp.toPx()).roundToInt()
                }
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset { IntOffset(0, yOffset) },
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.SurfaceHigh,
                shadowElevation = if (reducedMotion) 0.dp else 8.dp,
                border = BorderStroke(2.dp, FocusColors.Mint),
            ) {
                Text(
                    session.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

private fun commitDrop(
    session: DragSession,
    key: String?,
    filledIds: List<String>,
    viewModel: FocusStackViewModel,
) {
    if (key == null || !validDrop(session, key, filledIds)) return
    when (session.origin) {
        DragOrigin.TODAY ->
            when {
                key == "later" -> viewModel.demote(session.taskId)
                key == "empty" -> viewModel.reorderToday(moveToEnd(filledIds, session.taskId))
                key.startsWith("today-") -> {
                    val index = key.removePrefix("today-").toIntOrNull() ?: return
                    val next = reorder(filledIds, session.taskId, index)
                    if (next != filledIds) viewModel.reorderToday(next)
                }
            }
        DragOrigin.LATER -> viewModel.promote(session.taskId)
    }
}

private fun validDrop(
    session: DragSession,
    key: String,
    filledIds: List<String>,
): Boolean =
    when (session.origin) {
        DragOrigin.TODAY ->
            key == "later" ||
                key == "empty" ||
                (key.startsWith("today-") && key != "today-${filledIds.indexOf(session.taskId)}")
        DragOrigin.LATER -> key == "empty" || key.startsWith("today-")
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

private fun moveToEnd(
    ids: List<String>,
    id: String,
): List<String> {
    val next = ids.toMutableList()
    if (!next.remove(id)) return ids
    next.add(id)
    return next
}

private fun reorder(
    ids: List<String>,
    id: String,
    toVisualIndex: Int,
): List<String> {
    val from = ids.indexOf(id)
    if (from < 0) return ids
    val next = ids.toMutableList()
    next.removeAt(from)
    val insert = if (toVisualIndex > from) toVisualIndex - 1 else toVisualIndex
    next.add(insert.coerceIn(0, next.size), id)
    return next
}

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
                enableDrag = false,
                reducedMotion = reducedMotion,
                onDone = viewModel::completeActive,
                onLater = { viewModel.demote(now.taskId) },
                onDragStart = {},
                onDragDelta = {},
                onDragEnd = {},
                onDragCancel = {},
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
                        Row(horizontalArrangement = Arrangement.spacedBy(Space)) {
                            MintButton("Done", onClick = viewModel::completeActive)
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
                        "Capture a thought, or drag one into Today.",
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
private fun TodayRow(
    index: Int,
    slot: SlotUi,
    task: TaskUi,
    highlighted: Boolean,
    dimmed: Boolean,
    reducedMotion: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onMove: () -> Unit,
    onPosition: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val description =
        "Today position ${index + 1}. ${slot.role.name.lowercase()}. ${task.title}. Swipe right for done, swipe left for later. Long press to drag."
    SwipeRevealCard(
        enableDone = true,
        enableLater = true,
        enableDrag = true,
        reducedMotion = reducedMotion,
        onDone = onDone,
        onLater = onLater,
        onDragStart = onDragStart,
        onDragDelta = onDragDelta,
        onDragEnd = onDragEnd,
        onDragCancel = onDragCancel,
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onPosition(it.boundsInWindow()) },
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
            color = if (dimmed) FocusColors.Surface.copy(alpha = 0.45f) else FocusColors.Surface,
            border = if (highlighted) BorderStroke(2.dp, FocusColors.Mint) else null,
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
private fun EmptyTodaySlot(
    highlighted: Boolean,
    onPosition: (Rect) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .onGloballyPositioned { onPosition(it.boundsInWindow()) }
                .semantics { contentDescription = "Empty Today slot. Drop a Later task here." }
                .dashedSlot(if (highlighted) FocusColors.Mint else FocusColors.Outline),
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
        modifier = Modifier.fillMaxWidth().padding(top = Space),
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

@Composable
private fun CountSection(
    title: String,
    countLabel: String,
    description: String,
    expanded: Boolean,
    tasks: List<TaskUi>,
    emptyLabel: String,
    highlighted: Boolean,
    draggable: Boolean,
    reducedMotion: Boolean,
    draggingId: String?,
    onToggle: () -> Unit,
    onPosition: (Rect) -> Unit,
    onAction: (String) -> Unit,
    onDone: (String) -> Unit,
    onLater: ((String) -> Unit)?,
    onDragStart: (TaskUi, Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .onGloballyPositioned { onPosition(it.boundsInWindow()) }
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = description },
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
            border = if (highlighted) BorderStroke(2.dp, FocusColors.Mint) else null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                Text(countLabel, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Muted)
            }
        }
        if (expanded) {
            if (tasks.isEmpty()) {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 8.dp))
            }
            tasks.forEach { task ->
                SwipeRevealCard(
                    enableDone = true,
                    enableLater = onLater != null,
                    enableDrag = draggable,
                    reducedMotion = reducedMotion,
                    onDone = { onDone(task.id) },
                    onLater = { onLater?.invoke(task.id) },
                    onDragStart = { origin -> onDragStart(task, origin) },
                    onDragDelta = onDragDelta,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CardRadius),
                        color =
                            if (draggingId == task.id) {
                                FocusColors.SurfaceHigh.copy(alpha = 0.45f)
                            } else {
                                FocusColors.SurfaceHigh
                            },
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onAction(task.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("+ Today")
                                }
                                TextButton(onClick = { onDone(task.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Done")
                                }
                                if (onLater != null) {
                                    TextButton(onClick = { onLater(task.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
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
