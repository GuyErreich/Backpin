// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.backpin.focus.R
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.repeat.everyNDaysLabel
import app.backpin.focus.ui.repeat.repeatBandMark
import app.backpin.focus.ui.repeat.repeatRowDescription
import app.backpin.focus.ui.repeat.repeatsLeftLabel
import app.backpin.focus.ui.theme.FocusColors
import kotlinx.coroutines.launch
import app.backpin.focus.domain.RepeatRow as RepeatRowModel

@Composable
internal fun RepeatSection(
    rows: RepeatRowsState,
    reducedMotion: Boolean,
    onCheckOff: (RepeatRowModel) -> Unit,
    onOpenMissed: (RepeatRowModel) -> Unit = {},
    onEdit: (Repeat) -> Unit,
    onCreate: () -> Unit,
) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(HomeMetrics.RepeatGap)) {
        SectionLabel(
            stringResource(R.string.repeat_section),
            repeatsLeftLabel(rows, resources),
            modifier =
            Modifier.padding(top = HomeMetrics.FlatHeaderTop),
        )
        if (rows.rows.isEmpty()) {
            Text(
                stringResource(R.string.repeat_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Live.Muted,
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
                        onOpenMissed = { onOpenMissed(row) },
                        onEdit = { onEdit(row.repeat) },
                        layout = HomeLayout.FLAT,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RepeatRow(
    row: RepeatRowModel,
    reducedMotion: Boolean,
    onCheckOff: () -> Unit,
    onOpenMissed: () -> Unit,
    onEdit: () -> Unit,
    layout: HomeLayout = HomeLayout.FLAT,
) {
    val open = row.status == RepeatDayStatus.OPEN
    val done = row.status == RepeatDayStatus.DONE
    val missed = row.status == RepeatDayStatus.MISSED
    val checkable = open || missed
    val interaction = rememberPressSource()
    val resources = LocalContext.current.resources
    val description = repeatRowDescription(row, resources)
    val interval = everyNDaysLabel(row.cadenceDays, resources)
    val cadence =
        interval?.let { resources.getString(R.string.repeat_title_cadence_gap, it).trim() }
    val shape = RoundedCornerShape(HomeMetrics.RepeatRadius)
    val titleColor = if (done || missed) FocusColors.Live.Muted else FocusColors.Live.Text
    val borderColor = if (done) FocusColors.Live.SurfaceHigh else FocusColors.Live.Outline
    val tag =
        if (missed) {
            missedRepeatTag(row.repeat.band.name)
        } else if (layout == HomeLayout.BANDS) {
            buildAnnotatedString { append(stringResource(R.string.repeat_section)) }
        } else {
            buildAnnotatedString { append(repeatBandMark(row.repeat.band)) }
        }
    val content: @Composable () -> Unit = {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = HomeMetrics.HomeTouchTarget)
                .padding(
                    horizontal = HomeMetrics.RepeatPaddingHorizontal,
                    vertical = HomeMetrics.RepeatPaddingVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(HomeMetrics.RepeatTagGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(HomeMetrics.RepeatGlyphGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatMark(
                    done = done,
                    color = if (done) FocusColors.Live.Mint else titleColor,
                )
                Text(
                    row.repeat.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = HomeMetrics.RepeatTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                    color = titleColor,
                )
                if (cadence != null) {
                    Text(
                        cadence,
                        style = HomeMetrics.RepeatTitle,
                        color = FocusColors.Live.Muted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
            Text(
                tag,
                style = HomeMetrics.RowTag,
                color = FocusColors.Live.Muted,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = HomeMetrics.HomeTouchTarget)
            .animateRepeatPlacement(reducedMotion)
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
                            onClickLabel =
                            if (missed) {
                                resources.getString(R.string.missed_repeat_sheet_open)
                            } else {
                                resources.getString(R.string.repeat_row_check_off)
                            },
                            onLongClickLabel = resources.getString(R.string.repeat_row_edit),
                            onClick = if (missed) onOpenMissed else onCheckOff,
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
    Surface(
        modifier = rowModifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(HomeMetrics.RepeatBorder, borderColor),
    ) {
        content()
    }
}

@Composable
private fun missedRepeatTag(bandName: String) = buildAnnotatedString {
    withStyle(SpanStyle(color = FocusColors.Live.Amber)) {
        append(stringResource(R.string.repeat_missed_tag))
    }
    withStyle(SpanStyle(color = FocusColors.Live.Muted)) {
        append(" · ")
        append(bandName)
    }
}

@Composable
private fun RepeatMark(
    done: Boolean,
    color: Color,
) {
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
