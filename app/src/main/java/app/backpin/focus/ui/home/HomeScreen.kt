// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.backpin.focus.R
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.MAX_TODAY_LIMIT
import app.backpin.focus.domain.MIN_TODAY_LIMIT
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.elapsedMillis
import app.backpin.focus.domain.formatElapsed
import app.backpin.focus.ui.FocusStackViewModel
import app.backpin.focus.ui.FocusUiState
import app.backpin.focus.ui.NowUi
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.focus.RecoveryCard
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    state: FocusUiState,
    viewModel: FocusStackViewModel,
    onOpenFocus: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenInbox: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenScheduled: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDone: () -> Unit,
    modifier: Modifier = Modifier,
    homeLayout: HomeLayout = HomeLayout.FLAT,
) {
    val grouped = homeLayout == HomeLayout.BANDS
    val reducedMotion = rememberReducedMotion()
    val repeatRows by viewModel.repeatRows.collectAsStateWithLifecycle()
    val homeBands by viewModel.homeBands.collectAsStateWithLifecycle()
    val todayLimit = state.preferences.todayLimit.coerceIn(MIN_TODAY_LIMIT, MAX_TODAY_LIMIT)
    val filled = state.todaySlots.mapNotNull { slot -> slot.task?.let { slot to it } }
    val hasRoom = filled.size < todayLimit
    val openBringBack: (String, String) -> Unit = viewModel::openBringBack

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = HomeMetrics.PageHorizontal,
                end = HomeMetrics.PageHorizontal,
                top = HomeMetrics.PageVertical,
                bottom = HomeMetrics.ListBottom,
            ),
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_eyebrow),
                style = HomeMetrics.Eyebrow,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(top = HomeMetrics.EyebrowTop, bottom = HomeMetrics.EyebrowBottom),
            )
            Text(
                text = stringResource(R.string.home_title),
                style = HomeMetrics.ScreenTitle,
                color = FocusColors.Live.Text,
                modifier =
                Modifier
                    .padding(bottom = HomeMetrics.TitleBottom)
                    .semantics { heading() },
            )
        }
        if (!state.ready) {
            Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyLarge)
        } else {
            NowCard(state, viewModel, onOpenFocus, reducedMotion, openBringBack)
            if (grouped) {
                HomeBandsSection(
                    bands = homeBands,
                    filled = filled,
                    hasRoom = hasRoom,
                    todayLimit = todayLimit,
                    reducedMotion = reducedMotion,
                    somethingCurrent = state.now != null,
                    onOpenFocus = onOpenFocus,
                    onStart = viewModel::start,
                    onDone = viewModel::markDone,
                    onBringBack = openBringBack,
                    onInterrupt = viewModel::interruptWith,
                    onReorder = viewModel::reorderToday,
                    onEditTask = viewModel::openEdit,
                    onOpenSheet = viewModel::openTaskSheet,
                    onCheckOffRepeat = { row ->
                        if (row.status != RepeatDayStatus.DONE) {
                            viewModel.markRepeatDone(row.repeat.id, row.epochDay)
                        }
                    },
                    onOpenMissedRepeat = { row ->
                        viewModel.openMissedRepeat(row.repeat.id, row.epochDay)
                    },
                    onUndoRepeat = { row ->
                        viewModel.undoRepeatDone(row.repeat.id, row.epochDay)
                    },
                    onEditRepeat = viewModel::openRepeatEdit,
                )
            } else {
                TodaySection(
                    filled = filled,
                    hasRoom = hasRoom,
                    todayLimit = todayLimit,
                    reducedMotion = reducedMotion,
                    onOpenFocus = onOpenFocus,
                    somethingCurrent = state.now != null,
                    onStart = viewModel::start,
                    onDone = viewModel::markDone,
                    onBringBack = openBringBack,
                    onInterrupt = viewModel::interruptWith,
                    onReorder = viewModel::reorderToday,
                    onEdit = viewModel::openEdit,
                    onOpenSheet = viewModel::openTaskSheet,
                )
                RepeatSection(
                    rows = repeatRows,
                    reducedMotion = reducedMotion,
                    onCheckOff = { row ->
                        if (row.status != RepeatDayStatus.DONE) {
                            viewModel.markRepeatDone(row.repeat.id, row.epochDay)
                        }
                    },
                    onOpenMissed = { row ->
                        viewModel.openMissedRepeat(row.repeat.id, row.epochDay)
                    },
                    onUndo = { row ->
                        viewModel.undoRepeatDone(row.repeat.id, row.epochDay)
                    },
                    onEdit = viewModel::openRepeatEdit,
                    onCreate = { viewModel.openRepeatCreate() },
                )
            }
            Text(
                text = stringResource(R.string.home_missed_footer),
                style = HomeMetrics.NowTime,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(top = HomeMetrics.FlatHeaderTop),
            )
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
    onBringBack: (String, String) -> Unit,
) {
    val now = state.now
    val recovery = state.recovery
    when {
        now != null -> {
            val accent = FocusColors.Live.Lavender
            val label = nowLabel(now)
            val openInteraction = rememberPressSource()
            val bringBackLabel = stringResource(R.string.action_bring_back)
            SwipeRevealCard(
                towardStart = doneSwipe(viewModel::completeActive),
                towardEnd =
                bringBackSwipe {
                    onBringBack(now.taskId, now.title)
                },
                reducedMotion = reducedMotion,
                onLongPress = { viewModel.openTaskSheet(now.taskId) },
                extraActions =
                listOf(
                    CustomAccessibilityAction(stringResource(R.string.action_done)) {
                        viewModel.completeActive()
                        true
                    },
                    CustomAccessibilityAction(bringBackLabel) {
                        onBringBack(now.taskId, now.title)
                        true
                    },
                    CustomAccessibilityAction(stringResource(R.string.action_edit)) {
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
                    shape = RoundedCornerShape(HomeMetrics.NowRadius),
                    color = FocusColors.Live.Surface,
                    border = BorderStroke(HomeMetrics.NowBorder, accent),
                ) {
                    Column(
                        Modifier.padding(
                            horizontal = HomeMetrics.NowPaddingHorizontal,
                            vertical = HomeMetrics.NowPaddingVertical,
                        ),
                    ) {
                        Text(
                            label,
                            color = accent,
                            style = HomeMetrics.NowLabel,
                            modifier = Modifier.padding(bottom = HomeMetrics.NowLabelBottom),
                        )
                        Text(
                            now.title,
                            style = HomeMetrics.NowTitle,
                            color = FocusColors.Live.Text,
                            modifier = Modifier.padding(bottom = HomeMetrics.NowTitleBottom),
                        )
                        ElapsedText(
                            accumulatedMillis = now.accumulatedMillis,
                            startedAtEpochMillis = now.startedAtEpochMillis,
                            running = true,
                            clock = true,
                        )
                    }
                }
            }
        }
        recovery != null -> RecoveryCard(recovery = recovery, viewModel = viewModel, showCueEditor = true)
        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(HomeMetrics.NowRadius),
                color = FocusColors.Live.Surface,
                border = BorderStroke(HomeMetrics.NowBorder, FocusColors.Live.Muted),
            ) {
                Column(
                    Modifier.padding(
                        horizontal = HomeMetrics.NowPaddingHorizontal,
                        vertical = HomeMetrics.NowPaddingVertical,
                    ),
                ) {
                    Text(
                        stringResource(R.string.home_now),
                        color = FocusColors.Live.Muted,
                        style = HomeMetrics.NowLabel,
                        modifier = Modifier.padding(bottom = HomeMetrics.NowLabelBottom),
                    )
                    Text(
                        stringResource(R.string.home_now_empty_title),
                        style = HomeMetrics.NowTitle,
                        color = FocusColors.Live.Text,
                        modifier = Modifier.padding(bottom = HomeMetrics.NowTitleBottom),
                    )
                    Text(
                        stringResource(R.string.home_now_empty_body),
                        style = HomeMetrics.NowTime,
                        color = FocusColors.Live.Muted,
                    )
                    state.paused?.let { paused ->
                        Text(
                            stringResource(R.string.home_now_paused, paused.title),
                            style = HomeMetrics.NowTitle,
                            color = FocusColors.Live.Text,
                            modifier = Modifier.padding(top = HomeMetrics.NowLabelBottom),
                        )
                    }
                }
            }
        }
    }
}

/** "EVENT" for an arrived countdown, "INTERRUPTED" for a nested task, otherwise "NOW". */
@Composable
internal fun nowLabel(now: NowUi): String = when {
    now.isEvent -> stringResource(R.string.home_now_event)
    now.isInterruption -> stringResource(R.string.home_now_interrupted)
    else -> stringResource(R.string.home_now)
}

@Composable
internal fun TodaySection(
    filled: List<Pair<SlotUi, TaskUi>>,
    hasRoom: Boolean,
    todayLimit: Int,
    reducedMotion: Boolean,
    somethingCurrent: Boolean,
    onOpenFocus: () -> Unit,
    onStart: (String) -> Unit,
    onDone: (String) -> Unit,
    onBringBack: (String, String) -> Unit,
    onInterrupt: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEdit: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
    showHeader: Boolean = true,
    layout: HomeLayout = HomeLayout.FLAT,
    todayTaskIds: Set<String> = filled.map { it.second.id }.toSet(),
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
    Column(verticalArrangement = Arrangement.spacedBy(layout.taskGap())) {
        if (showHeader) {
            SectionLabel(
                stringResource(R.string.home_today).uppercase(),
                slotLabel,
                modifier =
                Modifier.padding(top = HomeMetrics.FlatHeaderTop),
            )
        }
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
                        layout = layout,
                        todayLimit = todayLimit,
                        pinnedToToday = task.id in todayTaskIds,
                        onOpen = onOpenFocus,
                        onStart = { onStart(task.id) },
                        onDone = { onDone(task.id) },
                        onBringBack = { onBringBack(task.id, task.title) },
                        onInterrupt = { onInterrupt(task.id) },
                        onEdit = { onEdit(task.id) },
                        onOpenSheet = { onOpenSheet(task.id) },
                        onDragStart = { drag.begin(task.id) },
                        onDragBy = drag::dragBy,
                        onDragEnd = { settle(commitNearest = true) },
                        onDragCancel = { settle(commitNearest = false) },
                    )
                }
            }
        }
        if (hasRoom) {
            EmptyTodaySlot(layout = layout)
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
    layout: HomeLayout,
    todayLimit: Int,
    pinnedToToday: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onBringBack: () -> Unit,
    onInterrupt: () -> Unit,
    onEdit: () -> Unit,
    onOpenSheet: () -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val opensSheet = slot.role == SlotRole.OPEN || slot.role == SlotRole.WAITING
    val roleName = slot.role.name.lowercase()
    val description =
        if (opensSheet) {
            stringResource(R.string.home_row_desc_opens_sheet, index + 1, roleName, task.title)
        } else {
            stringResource(R.string.home_row_desc, index + 1, roleName, task.title)
        }
    val openInteraction = rememberPressSource()
    val bringBackLabel = stringResource(R.string.action_bring_back)
    SwipeRevealCard(
        towardStart = doneSwipe(onDone),
        towardEnd = bringBackSwipe(onBringBack),
        reducedMotion = reducedMotion,
        onReorderDragStart = onDragStart,
        onReorderDrag = onDragBy,
        onReorderDragEnd = onDragEnd,
        onReorderDragCancel = onDragCancel,
        onLongPress = onOpenSheet,
        extraActions =
        reorderActions +
            CustomAccessibilityAction(stringResource(R.string.action_done)) {
                onDone()
                true
            } +
            listOfNotNull(
                if (slot.role == SlotRole.OPEN) {
                    CustomAccessibilityAction(stringResource(R.string.action_start)) {
                        onStart()
                        true
                    }
                } else {
                    null
                },
                if (slot.role == SlotRole.OPEN && somethingCurrent) {
                    CustomAccessibilityAction(stringResource(R.string.action_interrupt)) {
                        onInterrupt()
                        true
                    }
                } else {
                    null
                },
            ) +
            CustomAccessibilityAction(bringBackLabel) {
                onBringBack()
                true
            } +
            CustomAccessibilityAction(stringResource(R.string.action_edit)) {
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
                    if (slot.role == SlotRole.NOW || opensSheet) {
                        Modifier
                            .pressScale(openInteraction)
                            .combinedClickable(
                                interactionSource = openInteraction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel =
                                if (opensSheet) {
                                    stringResource(R.string.task_sheet_open)
                                } else {
                                    null
                                },
                                onClick = if (opensSheet) onOpenSheet else onOpen,
                                onLongClick = {},
                            )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(layout.taskRadius()),
            color = FocusColors.Live.Surface,
            shadowElevation = 8.dp * lift,
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = HomeMetrics.HomeTouchTarget)
                    .padding(
                        horizontal = layout.taskPaddingHorizontal(),
                        vertical = layout.taskPaddingVertical(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    task.title,
                    style = layout.taskTitleStyle(),
                    color = FocusColors.Live.Text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val trailing = compactTrailing(layout, task, todayLimit, pinnedToToday)
                Text(
                    trailing.text,
                    style = if (layout == HomeLayout.FLAT) HomeMetrics.SlotChevron else HomeMetrics.RowTag,
                    color = trailing.color,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun EmptyTodaySlot(layout: HomeLayout) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = HomeMetrics.HomeTouchTarget)
            .semantics { contentDescription = "Empty Today slot." }
            .dashedSlot(FocusColors.Live.Outline, layout.taskRadius()),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(R.string.home_empty_slot),
            modifier =
            Modifier.padding(
                horizontal = layout.taskPaddingHorizontal(),
                vertical = layout.taskPaddingVertical(),
            ),
            style = layout.taskTitleStyle(),
            color = FocusColors.Live.Muted,
        )
    }
}

internal fun Modifier.dashedSlot(
    color: Color,
    radius: Dp = CardRadius,
): Modifier = this.drawBehind {
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style =
        Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
        ),
    )
}

private data class CompactTrailing(
    val text: String,
    val color: Color,
)

@Composable
private fun compactTrailing(
    layout: HomeLayout,
    task: TaskUi,
    todayLimit: Int,
    pinnedToToday: Boolean,
): CompactTrailing {
    if (layout == HomeLayout.FLAT) {
        return CompactTrailing("›", FocusColors.Live.Lavender)
    }
    if (pinnedToToday) {
        return CompactTrailing(stringResource(R.string.home_task_pin, todayLimit), FocusColors.Live.Lavender)
    }
    val minute = task.scheduledAtMinuteOfDay
    if (minute != null) {
        val hours = minute / 60
        val mins = minute % 60
        return CompactTrailing("%02d:%02d".format(hours, mins), FocusColors.Live.Muted)
    }
    return CompactTrailing("›", FocusColors.Live.Muted)
}

@Composable
internal fun SectionLabel(
    title: String,
    trailing: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HomeMetrics.Eyebrow,
            color = FocusColors.Live.Muted,
            modifier = Modifier.semantics { heading() },
        )
        Text(trailing, style = HomeMetrics.Eyebrow, color = FocusColors.Live.Muted)
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
    val shown = if (clock) "%d:%02d".format(hours, remainder) else formatElapsed(elapsed)
    Text(
        shown,
        style = if (clock) HomeMetrics.NowTime else MaterialTheme.typography.bodyLarge,
        color = FocusColors.Live.Muted,
        modifier =
        Modifier.semantics {
            contentDescription = "Elapsed $hours hours $remainder minutes"
        },
    )
}
