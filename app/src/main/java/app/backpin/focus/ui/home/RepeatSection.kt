// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import app.backpin.focus.R
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRow as RepeatRowModel
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.repeat.everyNDaysLabel
import app.backpin.focus.ui.repeat.missedRepeatCopy
import app.backpin.focus.ui.repeat.repeatBandMark
import app.backpin.focus.ui.repeat.repeatRowDescription
import app.backpin.focus.ui.repeat.repeatsLeftLabel
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import kotlinx.coroutines.launch

@Composable
internal fun RepeatSection(
    rows: RepeatRowsState,
    reducedMotion: Boolean,
    onCheckOff: (RepeatRowModel) -> Unit,
    onEdit: (Repeat) -> Unit,
    onCreate: () -> Unit,
) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
        SectionLabel(stringResource(R.string.repeat_section), repeatsLeftLabel(rows, resources))
        if (rows.rows.isEmpty()) {
            Text(
                stringResource(R.string.repeat_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Muted,
            )
            QuietButton(
                label = stringResource(R.string.repeat_new),
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            rows.rows.forEach { row ->
                key(row.repeat.id) {
                    RepeatRow(
                        row = row,
                        reducedMotion = reducedMotion,
                        onCheckOff = { onCheckOff(row) },
                        onEdit = { onEdit(row.repeat) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatRow(
    row: RepeatRowModel,
    reducedMotion: Boolean,
    onCheckOff: () -> Unit,
    onEdit: () -> Unit,
) {
    val open = row.status == RepeatDayStatus.OPEN
    val done = row.status == RepeatDayStatus.DONE
    val missed = row.status == RepeatDayStatus.MISSED
    val checkable = open || missed
    val targetAlpha =
        when (row.status) {
            RepeatDayStatus.DONE -> 0.45f
            RepeatDayStatus.MISSED -> 0.72f
            RepeatDayStatus.OPEN -> 1f
        }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = motionSpec(MotionSpec.SettleMillis, reducedMotion),
        label = "repeatFade",
    )
    val interaction = rememberPressSource()
    val resources = LocalContext.current.resources
    val description = repeatRowDescription(row, resources)
    val interval = everyNDaysLabel(row.cadenceDays, resources)
    val cadence =
        interval?.let { resources.getString(R.string.repeat_title_cadence_gap, it).trim() }
    val shape = RoundedCornerShape(CardRadius)
    val content: @Composable () -> Unit = {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatMark(done = done, missed = missed, color = if (done) FocusColors.Muted else FocusColors.Text)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.repeat.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (missed) FocusColors.Muted else FocusColors.Text,
                )
                if (missed) {
                    Text(
                        missedRepeatCopy(row.repeat.band, resources),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusColors.Amber,
                    )
                }
            }
            if (cadence != null) {
                Text(
                    cadence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.Muted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
            Text(
                repeatBandMark(row.repeat.band),
                style = MaterialTheme.typography.labelLarge,
                color = FocusColors.Muted,
                letterSpacing = 1.sp,
            )
        }
    }
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .animateRepeatPlacement(reducedMotion)
            .graphicsLayer { this.alpha = alpha }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions =
                    listOf(
                        CustomAccessibilityAction(resources.getString(R.string.repeat_row_edit)) {
                            onEdit()
                            true
                        },
                    )
            }
            .then(
                if (checkable) {
                    Modifier
                        .pressScale(interaction)
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = resources.getString(R.string.repeat_row_check_off),
                            onLongClickLabel = resources.getString(R.string.repeat_row_edit),
                            onClick = onCheckOff,
                            onLongClick = onEdit,
                        )
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = {},
                        onLongClick = onEdit,
                        onLongClickLabel = resources.getString(R.string.repeat_row_edit),
                    )
                },
            )
    if (missed) {
        Box(
            modifier = rowModifier.dashedSlot(FocusColors.Outline),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = rowModifier,
            shape = shape,
            color = FocusColors.Surface,
            border = BorderStroke(1.dp, FocusColors.Outline),
        ) {
            content()
        }
    }
}

@Composable
private fun RepeatMark(
    done: Boolean,
    missed: Boolean,
    color: androidx.compose.ui.graphics.Color,
) {
    if (missed) {
        Box(Modifier.size(18.dp))
        return
    }
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        if (done) {
            val path =
                Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.72f)
                    lineTo(size.width * 0.78f, size.height * 0.28f)
                }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        } else {
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * After a check-off, the row's layout index jumps to the bottom.
 * This holds it on the old Y and eases it into the new one. Reduced motion snaps.
 *
 * Animatable starts at the first layout position so the first frame is not
 * offset by `-Y`, which would throw the tap target off the row.
 */
private fun Modifier.animateRepeatPlacement(reducedMotion: Boolean): Modifier {
    if (reducedMotion) return this
    return composed {
        val scope = rememberCoroutineScope()
        var targetOffset by remember { mutableStateOf(IntOffset.Zero) }
        var animatable by remember {
            mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null)
        }
        this
            .onPlaced { coordinates ->
                targetOffset = coordinates.positionInParent().round()
            }
            .offset {
                val anim =
                    animatable
                        ?: Animatable(targetOffset, IntOffset.VectorConverter).also { animatable = it }
                if (anim.targetValue != targetOffset) {
                    scope.launch {
                        anim.animateTo(targetOffset, MotionSpec.easeOut(MotionSpec.ReorderMillis))
                    }
                }
                anim.value - targetOffset
            }
    }
}
