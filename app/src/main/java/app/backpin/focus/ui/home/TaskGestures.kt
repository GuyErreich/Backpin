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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.theme.FocusColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class DragOrigin {
    TODAY,
    LATER,
}

data class DragSession(
    val taskId: String,
    val title: String,
    val origin: DragOrigin,
    val windowX: Float,
    val windowY: Float,
)

private sealed interface Race {
    data object Up : Race

    data class Moved(
        val position: Offset,
    ) : Race
}

/**
 * Horizontal swipe reveals Done and Later. Release past 40% of the width commits.
 * Long-press then drag reports window movement for reorder and promotion.
 * Reduced motion keeps the card still and still reveals the labels.
 */
@Composable
internal fun SwipeRevealCard(
    enableDone: Boolean,
    enableLater: Boolean,
    enableDrag: Boolean,
    reducedMotion: Boolean,
    onDone: () -> Unit,
    onLater: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var logicalOffset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val coordinatesState = rememberUpdatedState(coordinates)
    val haptic = LocalHapticFeedback.current
    val doneState = rememberUpdatedState(onDone)
    val laterState = rememberUpdatedState(onLater)
    val dragStartState = rememberUpdatedState(onDragStart)
    val dragDeltaState = rememberUpdatedState(onDragDelta)
    val dragEndState = rememberUpdatedState(onDragEnd)
    val dragCancelState = rememberUpdatedState(onDragCancel)
    val threshold = width * 0.4f
    val reveal = if (threshold <= 0f) 0f else (abs(logicalOffset) / threshold).coerceIn(0f, 1f)
    val visualOffset = if (reducedMotion) 0f else logicalOffset

    Box(
        modifier
            .onGloballyPositioned {
                width = it.size.width.toFloat().coerceAtLeast(1f)
                coordinates = it
            }
            .semantics {
                customActions =
                    buildList {
                        if (enableDone) {
                            add(CustomAccessibilityAction("Done") { doneState.value(); true })
                        }
                        if (enableLater) {
                            add(CustomAccessibilityAction("Move to Later") { laterState.value(); true })
                        }
                    }
            }
            .pointerInput(enableDone, enableLater, enableDrag) {
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
                                if (enableDrag) {
                                    val origin = coordinatesState.value?.positionInWindow()?.plus(start) ?: start
                                    dragStartState.value(origin)
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                        if (change == null) {
                                            dragCancelState.value()
                                            break
                                        }
                                        if (!change.pressed) {
                                            dragEndState.value()
                                            break
                                        }
                                        change.consume()
                                        dragDeltaState.value(change.positionChange())
                                    }
                                }
                            }
                            is Race.Moved -> {
                                val fromStart = raced.position - start
                                if (abs(fromStart.y) > abs(fromStart.x)) return@awaitEachGesture
                                var total = fromStart.x
                                fun apply(next: Float) {
                                    val allowed =
                                        when {
                                            next > 0f -> enableDone
                                            next < 0f -> enableLater
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
                                            enableDone && total >= limit -> doneState.value()
                                            enableLater && total <= -limit -> laterState.value()
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
                    } catch (cancelled: CancellationException) {
                        dragCancelState.value()
                        throw cancelled
                    } finally {
                        logicalOffset = 0f
                    }
                }
            },
    ) {
        if (reveal > 0f) {
            val doneSide = logicalOffset > 0f
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (doneSide) {
                            FocusColors.Mint.copy(alpha = 0.22f * reveal)
                        } else {
                            FocusColors.Amber.copy(alpha = 0.22f * reveal)
                        },
                    )
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = if (doneSide) "Done ✓" else "Later ↩",
                    modifier = Modifier.align(if (doneSide) Alignment.CenterStart else Alignment.CenterEnd),
                    color = if (doneSide) FocusColors.Mint else FocusColors.Amber,
                    style = MaterialTheme.typography.labelLarge,
                )
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
