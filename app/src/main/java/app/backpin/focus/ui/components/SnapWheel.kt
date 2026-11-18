// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors
import kotlinx.coroutines.launch
import kotlin.math.abs

private val WHEEL_ITEM_HEIGHT = 48.dp
private const val WHEEL_VISIBLE_COUNT = 5
private const val WHEEL_PAD_COUNT = 2

/**
 * A snapping carousel. The centered row is the current value.
 * Bring-back days and the time wheels both use this.
 */
@Composable
internal fun SnapWheel(
    count: Int,
    selectedIndex: Int,
    onSelectedIndex: (Int) -> Unit,
    itemLabel: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    outlineColor: Color = FocusColors.Live.Text,
    description: String? = null,
) {
    val capped = count.coerceAtLeast(1)
    val initial = selectedIndex.coerceIn(0, capped - 1)
    val reducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val centered by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
            listState.firstVisibleItemScrollOffset
            centeredIndex(listState, capped)
        }
    }
    LaunchedEffect(centered) {
        if (centered != selectedIndex) onSelectedIndex(centered)
    }
    val described =
        if (description == null) {
            Modifier
        } else {
            Modifier.semantics { contentDescription = description }
        }
    Box(
        modifier =
        modifier
            .then(described)
            .fillMaxWidth()
            .height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_COUNT)
            .drawWithContent {
                drawContent()
                val stroke = 1.5.dp.toPx()
                val rectHeight = WHEEL_ITEM_HEIGHT.toPx()
                val top = (size.height - rectHeight) / 2f
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, rectHeight),
                    cornerRadius = CornerRadius(ButtonRadius.toPx()),
                    style = Stroke(width = stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT * WHEEL_PAD_COUNT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(capped) { index ->
                val distance = abs(index - centered)
                val source = remember { MutableInteractionSource() }
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .clickable(
                            enabled = index != centered,
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                scope.launch {
                                    if (reducedMotion) {
                                        listState.scrollToItem(index)
                                    } else {
                                        listState.animateScrollToItem(index)
                                    }
                                }
                            },
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = itemLabel(index),
                        style = wheelTextStyle(distance),
                        color = wheelTextColor(distance),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun wheelTextStyle(distance: Int) = when (distance) {
    0 ->
        MaterialTheme.typography.titleLarge.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    1 ->
        MaterialTheme.typography.titleMedium.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    else ->
        MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
        )
}

@Composable
private fun wheelTextColor(distance: Int) = when (distance) {
    0 -> FocusColors.Live.Text
    1 -> FocusColors.Live.Muted
    else -> FocusColors.Live.Muted.copy(alpha = 0.45f)
}

private fun centeredIndex(
    state: LazyListState,
    count: Int,
): Int {
    val info = state.layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val nearest =
        info.visibleItemsInfo.minByOrNull { item ->
            abs((item.offset + item.size / 2) - viewportCenter)
        }
    val index = nearest?.index ?: state.firstVisibleItemIndex
    return index.coerceIn(0, count - 1)
}
