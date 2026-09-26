package app.backpin.focus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.theme.FocusColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface Race {
    data object Up : Race

    data class Moved(
        val position: Offset,
    ) : Race
}

/**
 * One horizontal swipe action. [label] is what the finger reveals.
 * A null side means that direction does nothing.
 */
internal data class SwipeSide(
    val label: String,
    val accessibilityLabel: String,
    val tint: Color,
    val onCommit: () -> Unit,
)

internal fun doneSwipe(onDone: () -> Unit): SwipeSide =
    SwipeSide(
        label = "Done ✓",
        accessibilityLabel = "Done",
        tint = FocusColors.Mint,
        onCommit = onDone,
    )

internal fun laterSwipe(onLater: () -> Unit): SwipeSide =
    SwipeSide(
        label = "Later ↩",
        accessibilityLabel = "Move to Later",
        tint = FocusColors.Amber,
        onCommit = onLater,
    )

internal fun todaySwipe(onToday: () -> Unit): SwipeSide =
    SwipeSide(
        label = "Today",
        accessibilityLabel = "Add to Today",
        tint = FocusColors.Mint,
        onCommit = onToday,
    )

internal fun startSwipe(onStart: () -> Unit): SwipeSide =
    SwipeSide(
        label = "Start",
        accessibilityLabel = "Start",
        tint = FocusColors.Lavender,
        onCommit = onStart,
    )

/**
 * Horizontal swipe reveals the two sides. Release past 40% of the width commits.
 * Today rows can also long-press and drag vertically; that callback only reorders
 * within Today and never changes a bucket.
 * Reduced motion keeps the card still and still reveals the labels.
 */
@Composable
internal fun SwipeRevealCard(
    towardStart: SwipeSide?,
    towardEnd: SwipeSide?,
    reducedMotion: Boolean,
    onReorderDragStart: (() -> Unit)? = null,
    onReorderDrag: ((Float) -> Unit)? = null,
    onReorderDragEnd: (() -> Unit)? = null,
    onReorderDragCancel: (() -> Unit)? = null,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var logicalOffset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val startState = rememberUpdatedState(towardStart)
    val endState = rememberUpdatedState(towardEnd)
    val reorderStart = rememberUpdatedState(onReorderDragStart)
    val reorderDrag = rememberUpdatedState(onReorderDrag)
    val reorderEnd = rememberUpdatedState(onReorderDragEnd)
    val reorderCancel = rememberUpdatedState(onReorderDragCancel)
    val threshold = width * 0.4f
    val reveal = if (threshold <= 0f) 0f else (abs(logicalOffset) / threshold).coerceIn(0f, 1f)
    val visualOffset = if (reducedMotion) 0f else logicalOffset
    val reorderEnabled = onReorderDrag != null

    Box(
        modifier
            .onGloballyPositioned { width = it.size.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                customActions =
                    buildList {
                        startState.value?.let { side ->
                            add(CustomAccessibilityAction(side.accessibilityLabel) { side.onCommit(); true })
                        }
                        endState.value?.let { side ->
                            add(CustomAccessibilityAction(side.accessibilityLabel) { side.onCommit(); true })
                        }
                        addAll(extraActions)
                    }
            }
            .pointerInput(towardStart != null, towardEnd != null, reorderEnabled) {
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val start = down.position
                    var crossed = false
                    try {
                        val raced =
                            withTimeoutOrNull(longPressMs) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: return@withTimeoutOrNull null
                                    if (!change.pressed) return@withTimeoutOrNull Race.Up
                                    if ((change.position - start).getDistance() > slop) {
                                        return@withTimeoutOrNull Race.Moved(change.position)
                                    }
                                }
                                @Suppress("UNREACHABLE_CODE")
                                null
                            }
                        when (raced) {
                            Race.Up -> Unit
                            null -> {
                                if (!reorderEnabled) return@awaitEachGesture
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                reorderStart.value?.invoke()
                                var ended = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                        change.consume()
                                        if (!change.pressed) {
                                            ended = true
                                            reorderEnd.value?.invoke()
                                            break
                                        }
                                        val delta = change.position.y - change.previousPosition.y
                                        if (delta != 0f) reorderDrag.value?.invoke(delta)
                                    }
                                } finally {
                                    if (!ended) reorderCancel.value?.invoke()
                                }
                            }
                            is Race.Moved -> {
                                val fromStart = raced.position - start
                                if (abs(fromStart.y) > abs(fromStart.x)) return@awaitEachGesture
                                var total = fromStart.x
                                fun apply(next: Float) {
                                    val allowed =
                                        when {
                                            next > 0f -> startState.value != null
                                            next < 0f -> endState.value != null
                                            else -> true
                                        }
                                    total = if (allowed) next.coerceIn(-width, width) else 0f
                                    logicalOffset = total
                                    val past = abs(total) >= width * 0.4f
                                    if (past && !crossed) {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        crossed = true
                                    }
                                    if (!past) crossed = false
                                }
                                apply(total)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) {
                                        val limit = width * 0.4f
                                        when {
                                            startState.value != null && total >= limit -> startState.value?.onCommit?.invoke()
                                            endState.value != null && total <= -limit -> endState.value?.onCommit?.invoke()
                                        }
                                        break
                                    }
                                    val delta = change.position - change.previousPosition
                                    if (abs(delta.y) > abs(delta.x) && abs(total) < slop) break
                                    change.consume()
                                    apply(total + delta.x)
                                }
                            }
                        }
                    } finally {
                        logicalOffset = 0f
                    }
                }
            },
    ) {
        if (reveal > 0f) {
            val startSide = logicalOffset > 0f
            val tint = if (startSide) startState.value?.tint else endState.value?.tint
            val label = if (startSide) startState.value?.label else endState.value?.label
            if (tint != null && label != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(tint.copy(alpha = 0.22f * reveal))
                        .padding(horizontal = 20.dp),
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.align(if (startSide) Alignment.CenterStart else Alignment.CenterEnd),
                        color = tint,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(visualOffset.roundToInt(), 0) },
        ) {
            content()
        }
    }
}
