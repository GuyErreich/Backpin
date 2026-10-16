package app.backpin.focus.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.elapsedMillis
import app.backpin.focus.domain.formatElapsed
import app.backpin.focus.domain.suggestedCountdownDays
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.NowUi
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.countdown.EventDetachButtons
import app.backpin.focus.ui.countdown.EventResumeHost
import app.backpin.focus.ui.focus.RecoveryCard
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenLater: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val todaysRepeats by viewModel.todaysRepeats.collectAsState()
    val todayLimit = state.preferences.todayLimit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
    val filled = state.todaySlots.mapNotNull { slot -> slot.task?.let { slot to it } }
    val hasRoom = filled.size < todayLimit

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
                hasRoom = hasRoom,
                todayLimit = todayLimit,
                reducedMotion = reducedMotion,
                onOpenFocus = onOpenFocus,
                somethingCurrent = state.now != null,
                onStart = viewModel::start,
                onDone = viewModel::markDone,
                onLater = viewModel::demote,
                onInterrupt = viewModel::interruptWith,
                onReorder = viewModel::reorderToday,
                onEdit = viewModel::openEdit,
            )
            RepeatSection(
                todays = todaysRepeats,
                reducedMotion = reducedMotion,
                onCheckOff = { occurrence ->
                    if (occurrence.status != RepeatDayStatus.DONE) {
                        viewModel.markRepeatDone(occurrence.repeat.id, occurrence.epochDay)
                    }
                },
                onEdit = viewModel::openRepeatEdit,
                onCreate = { viewModel.openRepeatCreate() },
            )
            HorizontalDivider(color = FocusColors.Outline)
            Column(verticalArrangement = Arrangement.spacedBy(Space)) {
                PileCountRow(title = "Inbox", count = state.inboxCount, onClick = onOpenInbox)
                PileCountRow(title = "Later", count = state.laterCount, onClick = onOpenLater)
                PileCountRow(title = "Scheduled", count = state.scheduledCount, onClick = onOpenScheduled)
                PileCountRow(title = "Done", count = state.archiveCount, onClick = onOpenDone)
            }
        }
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
            val accent = if (now.isInterruption || now.isEvent) FocusColors.Lavender else FocusColors.Mint
            val label = nowLabel(now)
            val openInteraction = rememberPressSource()
            SwipeRevealCard(
                towardStart = doneSwipe(viewModel::completeActive),
                towardEnd =
                    laterSwipe {
                        if (now.isEvent) {
                            viewModel.detachEvent(now.taskId, DetachDestination.Later)
                        } else {
                            viewModel.demote(now.taskId)
                        }
                    },
                reducedMotion = reducedMotion,
                extraActions =
                    listOf(
                        CustomAccessibilityAction("Edit") {
                            viewModel.openEdit(now.taskId)
                            true
                        },
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "$label. ${now.title}. Opens focus." }
                            .pressScale(openInteraction)
                            .clickable(
                                interactionSource = openInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = onOpenFocus,
                            ),
                    shape = RoundedCornerShape(CardRadius),
                    color = FocusColors.Surface,
                    border = BorderStroke(2.dp, accent),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                        Text(label, color = accent, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
                        Text(now.title, style = MaterialTheme.typography.titleLarge)
                        ElapsedText(
                            accumulatedMillis = now.accumulatedMillis,
                            startedAtEpochMillis = now.startedAtEpochMillis,
                            running = true,
                            clock = true,
                        )
                        if (!now.returnCue.isNullOrBlank()) {
                            Text(
                                "Return note",
                                color = FocusColors.Muted,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(now.returnCue, style = MaterialTheme.typography.bodyLarge)
                        }
                        EventResumeHost(
                            taskId = now.taskId,
                            title = now.title,
                            suggestedDays =
                                suggestedCountdownDays(
                                    now.countdownIntervalDays,
                                    state.preferences.lastCountdownDays,
                                ),
                            onDetach = viewModel::detachEvent,
                        ) { onCountDownAgain ->
                            CardActions {
                                AccentButton("Done", AccentTone.Mint, onClick = viewModel::completeActive)
                                if (now.isEvent) {
                                    EventDetachButtons(
                                        taskId = now.taskId,
                                        onDetach = viewModel::detachEvent,
                                        onCountDownAgain = onCountDownAgain,
                                    )
                                } else {
                                    AccentButton(
                                        "Interrupt",
                                        AccentTone.Lavender,
                                        onClick = { viewModel.openCapture(interrupts = true) },
                                    )
                                    QuietButton("Later", onClick = { viewModel.demote(now.taskId) })
                                }
                                QuietButton("Edit", onClick = { viewModel.openEdit(now.taskId) })
                            }
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
                        Text("Paused: ${paused.title}", style = MaterialTheme.typography.titleLarge)
                        CardActions {
                            QuietButton("Start", onClick = { viewModel.start(paused.taskId) })
                        }
                    }
                }
            }
        }
    }
}

/** "EVENT" for an arrived countdown, "INTERRUPTED" for a nested task, otherwise "NOW". */
internal fun nowLabel(now: NowUi): String =
    when {
        now.isEvent -> "EVENT"
        now.isInterruption -> "INTERRUPTED"
        else -> "NOW"
    }

@Composable
private fun TodaySection(
    filled: List<Pair<SlotUi, TaskUi>>,
    hasRoom: Boolean,
    todayLimit: Int,
    reducedMotion: Boolean,
    somethingCurrent: Boolean,
    onOpenFocus: () -> Unit,
    onStart: (String) -> Unit,
    onDone: (String) -> Unit,
    onLater: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEdit: (String) -> Unit,
) {
    val drag = remember { TodayDragState() }
    var rows by remember { mutableStateOf(filled) }
    var snapToken by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(filled) {
        if (!drag.holding) rows = filled
    }
    val ids = rows.map { it.second.id }
    drag.ids = ids
    val draggingId = drag.draggingId
    val offsetY = drag.offsetY
    val gapPx = with(LocalDensity.current) { Space.toPx() }
    val step = drag.rowHeight + gapPx
    drag.step = step
    val fromIndex = ids.indexOf(draggingId)
    val targetIndex =
        if (draggingId == null || fromIndex < 0 || step <= 1f) {
            fromIndex
        } else {
            (fromIndex + (offsetY / step).roundToInt()).coerceIn(0, ids.lastIndex)
        }
    fun settle(commitNearest: Boolean) {
        val release = drag.release(commitNearest) ?: return
        release.order?.let(onReorder)
        if (reducedMotion || !release.animate) {
            release.order?.let { order -> rows = rows.reordered(order) }
            drag.clear()
            snapToken++
            return
        }
        scope.launch {
            delay(MotionSpec.ReorderMillis.toLong())
            release.order?.let { order -> rows = rows.reordered(order) }
            drag.clear()
            snapToken++
        }
    }
    val slotLabel = if (todayLimit == 1) "1 slot" else "$todayLimit slots"
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        SectionLabel("TODAY", slotLabel)
        rows.forEachIndexed { index, (slot, task) ->
            key(task.id) {
                val targetShift =
                    when {
                        draggingId == null || fromIndex < 0 || targetIndex < 0 -> 0f
                        task.id == draggingId -> offsetY
                        fromIndex < targetIndex && index in (fromIndex + 1)..targetIndex -> -step
                        targetIndex < fromIndex && index in targetIndex until fromIndex -> step
                        else -> 0f
                    }
                val followFinger = task.id == draggingId && !drag.settling
                val shift =
                    rememberRowShift(
                        target = targetShift,
                        followFinger = followFinger,
                        reducedMotion = reducedMotion,
                        snapToken = snapToken,
                    )
                val reorderActions =
                    listOfNotNull(
                        if (index > 0) {
                            CustomAccessibilityAction("Move up") {
                                onReorder(move(ids, index, index - 1))
                                true
                            }
                        } else {
                            null
                        },
                        if (index < ids.lastIndex) {
                            CustomAccessibilityAction("Move down") {
                                onReorder(move(ids, index, index + 1))
                                true
                            }
                        } else {
                            null
                        },
                    )
                val lifted = task.id == draggingId
                val lift by animateFloatAsState(
                    targetValue = if (lifted) 1f else 0f,
                    animationSpec = motionSpec(MotionSpec.LiftMillis, reducedMotion),
                    label = "todayLift",
                )
                Box(
                    Modifier
                        .zIndex(if (lifted) 1f else 0f)
                        .onGloballyPositioned { coordinates ->
                            val height = coordinates.size.height.toFloat()
                            if (height > 1f) drag.rowHeight = height
                        }
                        .offset { IntOffset(0, shift.roundToInt()) }
                        .graphicsLayer {
                            val scale = if (reducedMotion) 1f else 1f + (MotionSpec.LiftScale - 1f) * lift
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    TodayRow(
                        index = index,
                        slot = slot,
                        task = task,
                        lift = if (reducedMotion) if (lifted) 1f else 0f else lift,
                        reducedMotion = reducedMotion,
                        somethingCurrent = somethingCurrent,
                        reorderActions = reorderActions,
                        onOpen = onOpenFocus,
                        onStart = { onStart(task.id) },
                        onDone = { onDone(task.id) },
                        onLater = { onLater(task.id) },
                        onInterrupt = { onInterrupt(task.id) },
                        onEdit = { onEdit(task.id) },
                        onDragStart = { drag.begin(task.id) },
                        onDragBy = drag::dragBy,
                        onDragEnd = { settle(commitNearest = true) },
                        onDragCancel = { settle(commitNearest = false) },
                    )
                }
            }
        }
        if (hasRoom) {
            EmptyTodaySlot()
        }
    }
}

@Composable
private fun rememberRowShift(
    target: Float,
    followFinger: Boolean,
    reducedMotion: Boolean,
    snapToken: Int,
): Float {
    var shift by remember { mutableFloatStateOf(target) }
    var seenToken by remember { mutableIntStateOf(snapToken) }
    val snap = followFinger || reducedMotion || seenToken != snapToken
    if (seenToken != snapToken) seenToken = snapToken
    if (snap && shift != target) shift = target
    LaunchedEffect(target, followFinger, reducedMotion, snapToken) {
        if (followFinger || reducedMotion || shift == target) {
            shift = target
        } else {
            androidx.compose.animation.core.animate(
                initialValue = shift,
                targetValue = target,
                animationSpec = MotionSpec.easeOut(MotionSpec.ReorderMillis),
            ) { value, _ ->
                shift = value
            }
        }
    }
    return shift
}

private data class DragRelease(
    val animate: Boolean,
    val order: List<String>?,
)

/**
 * Finger offset for a long-press reorder inside Today.
 * [release] keeps the card in place until the settle animation finishes.
 */
private class TodayDragState {
    var ids: List<String> = emptyList()
    var draggingId: String? by mutableStateOf(null)
    var offsetY: Float by mutableFloatStateOf(0f)
    var rowHeight: Float by mutableFloatStateOf(0f)
    var step: Float = 0f
    var settling by mutableStateOf(false)

    val holding: Boolean
        get() = draggingId != null

    fun begin(id: String) {
        if (settling) return
        draggingId = id
        offsetY = 0f
        settling = false
    }

    fun dragBy(deltaY: Float) {
        if (draggingId == null || settling) return
        offsetY += deltaY
    }

    fun release(commitNearest: Boolean): DragRelease? {
        val id = draggingId ?: return null
        val from = ids.indexOf(id)
        val stride = if (step > 1f) step else rowHeight
        if (from < 0 || rowHeight <= 1f || stride <= 1f) {
            clear()
            return null
        }
        val to =
            if (commitNearest) {
                (from + (offsetY / stride).roundToInt()).coerceIn(0, ids.lastIndex)
            } else {
                from
            }
        val target = (to - from) * stride
        val animate = abs(offsetY - target) > 1f
        offsetY = target
        settling = true
        val order = if (to == from) null else move(ids, from, to)
        return DragRelease(animate = animate, order = order)
    }

    fun clear() {
        draggingId = null
        offsetY = 0f
        settling = false
    }
}

private fun List<Pair<SlotUi, TaskUi>>.reordered(order: List<String>): List<Pair<SlotUi, TaskUi>> {
    val byId = associateBy { it.second.id }
    return order.mapNotNull { byId[it] }
}

@Composable
private fun TodayRow(
    index: Int,
    slot: SlotUi,
    task: TaskUi,
    lift: Float,
    reducedMotion: Boolean,
    somethingCurrent: Boolean,
    reorderActions: List<CustomAccessibilityAction>,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onInterrupt: () -> Unit,
    onEdit: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val rightSwipe = todayRightSwipe(slot.role, somethingCurrent)
    val rightWord =
        when (rightSwipe) {
            TodayRightSwipe.Done -> "done"
            TodayRightSwipe.Start -> "start"
            TodayRightSwipe.Interrupt -> "interrupt"
        }
    val description =
        "Today position ${index + 1}. ${slot.role.name.lowercase()}. ${task.title}. Swipe right for $rightWord, swipe left for later. Long press and drag to reorder within Today."
    val openInteraction = rememberPressSource()
    SwipeRevealCard(
        towardStart =
            when (rightSwipe) {
                TodayRightSwipe.Done -> doneSwipe(onDone)
                TodayRightSwipe.Start -> startSwipe(onStart)
                TodayRightSwipe.Interrupt -> interruptSwipe(onInterrupt)
            },
        towardEnd = laterSwipe(onLater),
        reducedMotion = reducedMotion,
        onReorderDragStart = onDragStart,
        onReorderDrag = onDragBy,
        onReorderDragEnd = onDragEnd,
        onReorderDragCancel = onDragCancel,
        extraActions =
            reorderActions +
                CustomAccessibilityAction("Edit") {
                    onEdit()
                    true
                },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = description }
                    .then(
                        if (slot.role == SlotRole.NOW) {
                            Modifier
                                .pressScale(openInteraction)
                                .combinedClickable(
                                    interactionSource = openInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = onOpen,
                                    onLongClick = {},
                                )
                        } else {
                            Modifier
                        },
                    ),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Surface,
            shadowElevation = 8.dp * lift,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RoleLabel(slot.role)
                }
                CardActions {
                    if (slot.role == SlotRole.OPEN) {
                        QuietButton("Start", onClick = onStart)
                    }
                    AccentButton("Done", AccentTone.Mint, onClick = onDone)
                    QuietButton("Later", onClick = onLater)
                    QuietButton("Edit", onClick = onEdit)
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

internal fun Modifier.dashedSlot(color: Color): Modifier =
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
internal fun SectionLabel(
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
 * A count-only row for a pile that lives on its own screen.
 * The row is not a Today slot and does not expand a list.
 */
@Composable
private fun PileCountRow(
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    val interaction = rememberPressSource()
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val color by animateColorAsState(
        targetValue = if (pressed) FocusColors.SurfaceHigh else FocusColors.Surface,
        animationSpec = motionSpec(MotionSpec.PressMillis, reducedMotion),
        label = "countPress",
    )
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .pressScale(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Open $title",
                    onClick = onClick,
                ),
        shape = RoundedCornerShape(CardRadius),
        color = color,
        border = BorderStroke(1.dp, FocusColors.Outline),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = FocusColors.Muted,
                letterSpacing = 1.2.sp,
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = FocusColors.Mint,
            )
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
