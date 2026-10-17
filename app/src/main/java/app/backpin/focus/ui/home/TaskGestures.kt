// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val onTint: Color,
    val onCommit: () -> Unit,
)

@Composable
internal fun doneSwipe(onDone: () -> Unit): SwipeSide = SwipeSide(
    label = "Done",
    accessibilityLabel = "Done",
    tint = FocusColors.Mint,
    onTint = FocusColors.OnMint,
    onCommit = onDone,
)

@Composable
internal fun laterSwipe(onLater: () -> Unit): SwipeSide = SwipeSide(
    label = "Later",
    accessibilityLabel = "Move to Later",
    tint = FocusColors.Danger,
    onTint = FocusColors.OnMint,
    onCommit = onLater,
)

@Composable
internal fun todaySwipe(onToday: () -> Unit): SwipeSide = SwipeSide(
    label = "Today",
    accessibilityLabel = "Add to Today",
    tint = FocusColors.Mint,
    onTint = FocusColors.OnMint,
    onCommit = onToday,
)

@Composable
internal fun startSwipe(onStart: () -> Unit): SwipeSide = SwipeSide(
    label = "Start",
    accessibilityLabel = "Start",
    tint = FocusColors.Mint,
    onTint = FocusColors.OnMint,
    onCommit = onStart,
)

@Composable
internal fun interruptSwipe(onInterrupt: () -> Unit): SwipeSide = SwipeSide(
    label = "Interrupt",
    accessibilityLabel = "Interrupt",
    tint = FocusColors.Lavender,
    onTint = FocusColors.OnLavender,
    onCommit = onInterrupt,
)

/**
 * Horizontal swipe reveals a solid color band. The card tracks the finger.
 * Release past 40% of the width completes with a decelerate; otherwise it settles back.
 * The word is centered in the band and grows slightly once the finger passes that point.
 * Today rows can also long-press and drag vertically; that callback only reorders
 * within Today and never changes a bucket.
 * Inbox, Later, and Scheduled do not use long-press. Edit is a button on the card.
 * Reduced motion keeps the card still. The band and the word still appear with the drag.
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
    onLongPress: (() -> Unit)? = null,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    cornerRadius: Dp = CardRadius,
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
    val longPress = rememberUpdatedState(onLongPress)
    val reducedState = rememberUpdatedState(reducedMotion)
    val threshold = width * 0.4f
    val visualOffset = if (reducedMotion) 0f else logicalOffset
    val reorderEnabled = onReorderDrag != null
    val longPressEnabled = onLongPress != null

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
            .pointerInput(towardStart != null, towardEnd != null, reorderEnabled, longPressEnabled) {
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                coroutineScope {
                val scope = this
                var settleJob: Job? = null

                suspend fun glide(
                    to: Float,
                    millis: Int,
                ) {
                    animate(
                        initialValue = logicalOffset,
                        targetValue = to,
                        animationSpec = tween(durationMillis = millis, easing = MotionSpec.EaseOut),
                    ) { value, _ ->
                        logicalOffset = value
                    }
                }

                suspend fun settle(total: Float) {
                    val limit = width * 0.4f
                    val startSide = startState.value
                    val endSide = endState.value
                    val commit =
                        when {
                            startSide != null && total >= limit -> startSide.onCommit
                            endSide != null && total <= -limit -> endSide.onCommit
                            else -> null
                        }
                    val reduced = reducedState.value
                    if (commit != null && !reduced) {
                        val edge = if (total >= 0f) width else -width
                        glide(edge, MotionSpec.SettleMillis)
                        commit()
                        delay(64)
                        if (abs(logicalOffset) > 1f) glide(0f, MotionSpec.SettleMillis)
                    } else {
                        commit?.invoke()
                        val millis = if (reduced) MotionSpec.ReducedFadeMillis else MotionSpec.SettleMillis
                        if (abs(logicalOffset) > 0.5f) glide(0f, millis) else logicalOffset = 0f
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    settleJob?.cancel()
                    val pointerId = down.id
                    val start = down.position
                    var crossed = false
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
                        Race.Up -> {
                            if (abs(logicalOffset) > 0.5f) {
                                val millis = if (reducedState.value) MotionSpec.ReducedFadeMillis else MotionSpec.SettleMillis
                                settleJob = scope.launch { glide(0f, millis) }
                            }
                        }
                        null -> {
                            when {
                                reorderEnabled -> {
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
                                longPressEnabled -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    longPress.value?.invoke()
                                }
                            }
                        }
                        is Race.Moved -> {
                            val fromStart = raced.position - start
                            if (abs(fromStart.y) > abs(fromStart.x)) return@awaitEachGesture
                            var total = logicalOffset + fromStart.x

                            fun track(next: Float) {
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

                            track(total)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                if (!change.pressed) break
                                val delta = change.position - change.previousPosition
                                if (abs(delta.y) > abs(delta.x) && abs(total) < slop) break
                                change.consume()
                                track(total + delta.x)
                            }
                            val released = total
                            settleJob = scope.launch { settle(released) }
                        }
                    }
                }
                }
            },
    ) {
        val revealed = revealSide(logicalOffset, startState.value, endState.value)
        val committed = threshold > 0f && abs(logicalOffset) >= threshold
        if (revealed != null && !reducedMotion) {
            SwipeBandLayer(cornerRadius, revealed, logicalOffset, committed)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = visualOffset },
        ) {
            content()
        }
        if (revealed != null && reducedMotion) {
            SwipeBandLayer(cornerRadius, revealed, logicalOffset, committed)
        }
    }
}

private data class RevealedSide(
    val side: SwipeSide,
    val alignStart: Boolean,
)

private fun revealSide(
    logicalOffset: Float,
    towardStart: SwipeSide?,
    towardEnd: SwipeSide?,
): RevealedSide? {
    if (logicalOffset == 0f) return null
    val alignStart = logicalOffset > 0f
    val side = if (alignStart) towardStart else towardEnd
    return side?.let { RevealedSide(it, alignStart) }
}

/** Clips the band to the card corners without clipping the card's shadow. */
@Composable
private fun BoxScope.SwipeBandLayer(
    cornerRadius: Dp,
    revealed: RevealedSide,
    logicalOffset: Float,
    committed: Boolean,
) {
    Box(
        Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        SwipeBand(
            side = revealed.side,
            alignStart = revealed.alignStart,
            widthPx = abs(logicalOffset),
            committed = committed,
        )
    }
}

/** Solid band for the revealed side. The word sits in the middle of that band. */
@Composable
private fun BoxScope.SwipeBand(
    side: SwipeSide,
    alignStart: Boolean,
    widthPx: Float,
    committed: Boolean,
) {
    val band = with(LocalDensity.current) { widthPx.toDp() }
    val base = MaterialTheme.typography.titleLarge
    val style =
        if (committed) {
            base.copy(fontSize = base.fontSize * 1.15, lineHeight = base.lineHeight * 1.15)
        } else {
            base
        }
    Box(
        Modifier
            .align(if (alignStart) Alignment.CenterStart else Alignment.CenterEnd)
            .width(band)
            .fillMaxHeight()
            .clipToBounds()
            .background(side.tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = side.label,
            color = side.onTint,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
