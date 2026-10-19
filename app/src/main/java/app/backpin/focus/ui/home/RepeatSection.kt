// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import app.backpin.focus.R
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRowsState
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.components.RowDonePill
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.repeat.everyNDaysLabel
import app.backpin.focus.ui.repeat.missedRepeatCopy
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
    onUndo: (RepeatRowModel) -> Unit = {},
    onEdit: (Repeat) -> Unit,
    onCreate: () -> Unit,
) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(HomeLayout.FLAT.taskGap())) {
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
            RepeatRowGroup(
                rows = rows.rows,
                reducedMotion = reducedMotion,
                layout = HomeLayout.FLAT,
                onCheckOff = onCheckOff,
                onOpenMissed = onOpenMissed,
                onUndo = onUndo,
                onEdit = onEdit,
            )
        }
    }
}

@Composable
internal fun RepeatRowGroup(
    rows: List<RepeatRowModel>,
    reducedMotion: Boolean,
    layout: HomeLayout,
    onCheckOff: (RepeatRowModel) -> Unit,
    onOpenMissed: (RepeatRowModel) -> Unit,
    onUndo: (RepeatRowModel) -> Unit,
    onEdit: (Repeat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(layout.taskGap())) {
        rows.forEach { row ->
            key(row.repeat.id) {
                RepeatRow(
                    row = row,
                    reducedMotion = reducedMotion,
                    onCheckOff = { onCheckOff(row) },
                    onOpenMissed = { onOpenMissed(row) },
                    onUndo = { onUndo(row) },
                    onEdit = { onEdit(row.repeat) },
                    layout = layout,
                )
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
    onUndo: () -> Unit = {},
    onEdit: () -> Unit,
    layout: HomeLayout = HomeLayout.FLAT,
) {
    val done = row.status == RepeatDayStatus.DONE
    val missed = row.status == RepeatDayStatus.MISSED
    val interaction = rememberPressSource()
    val resources = LocalContext.current.resources
    val description = repeatRowDescription(row, resources)
    val cadence = everyNDaysLabel(row.cadenceDays, resources)
    val missedCopy = if (missed) missedRepeatCopy(row.repeat.band, resources) else null
    val titleColor = if (done) FocusColors.Live.Muted else FocusColors.Live.Text
    val onClickLabel =
        when {
            done -> resources.getString(R.string.repeat_row_undo)
            missed -> resources.getString(R.string.missed_repeat_sheet_open)
            else -> resources.getString(R.string.repeat_row_check_off)
        }
    Surface(
        modifier =
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
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = resources.getString(R.string.repeat_row_edit),
                onClick =
                when {
                    done -> onUndo
                    missed -> onOpenMissed
                    else -> onCheckOff
                },
                onLongClick = onEdit,
            ),
        shape = RoundedCornerShape(layout.taskRadius()),
        color = FocusColors.Live.Surface,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(HomeMetrics.HomeTouchTarget)
                .padding(horizontal = layout.taskPaddingHorizontal()),
            horizontalArrangement = Arrangement.spacedBy(HomeMetrics.RepeatTagGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                Modifier
                    .weight(1f)
                    .height(HomeMetrics.RowVisualHeight),
                horizontalArrangement = Arrangement.spacedBy(HomeMetrics.RepeatGlyphGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = RepeatIcon,
                    contentDescription = null,
                    modifier = Modifier.size(HomeMetrics.RepeatIconSize),
                    tint = FocusColors.Live.Muted,
                )
                Text(
                    row.repeat.title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = HomeMetrics.RepeatTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                )
                if (missedCopy != null) {
                    Text(
                        missedCopy,
                        style = HomeMetrics.RepeatCadence,
                        color = FocusColors.Live.Amber,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else if (cadence != null) {
                    Text(
                        cadence,
                        style = HomeMetrics.RepeatCadence,
                        color = FocusColors.Live.Muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            Box(
                modifier = Modifier.height(HomeMetrics.HomeTouchTarget),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(HomeMetrics.RepeatIconSize),
                        tint = FocusColors.Live.Mint,
                    )
                } else {
                    RowDonePill(onClick = onCheckOff)
                }
            }
        }
    }
}

private val RepeatIcon: ImageVector =
    ImageVector.Builder(
        name = "Repeat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(7f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(10f)
            lineTo(21f, 6f)
            lineTo(17f, 2f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(11f)
            horizontalLineTo(7f)
            verticalLineTo(7f)
            close()
            moveTo(17f, 17f)
            horizontalLineTo(7f)
            verticalLineTo(14f)
            lineTo(3f, 18f)
            lineTo(7f, 22f)
            verticalLineTo(19f)
            horizontalLineTo(19f)
            verticalLineTo(13f)
            horizontalLineTo(17f)
            verticalLineTo(17f)
            close()
        }
    }.build()

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
