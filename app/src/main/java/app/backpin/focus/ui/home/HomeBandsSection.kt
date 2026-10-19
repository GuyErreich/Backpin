// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.HomeLayout
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatDayStatus
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.Task
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.FocusColors

@Composable
internal fun HomeBandsSection(
    bands: HomeBands,
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
    onEditTask: (String) -> Unit,
    onOpenSheet: (String) -> Unit = {},
    onCheckOffRepeat: (RepeatRow) -> Unit,
    onOpenMissedRepeat: (RepeatRow) -> Unit = {},
    onUndoRepeat: (RepeatRow) -> Unit = {},
    onEditRepeat: (Repeat) -> Unit,
) {
    Column {
        bands.inOrder().forEach { group ->
            key(group.band) {
                HomeBandBlock(
                    group = group,
                    filled = filled,
                    hasRoom = hasRoom,
                    todayLimit = todayLimit,
                    reducedMotion = reducedMotion,
                    somethingCurrent = somethingCurrent,
                    onOpenFocus = onOpenFocus,
                    onStart = onStart,
                    onDone = onDone,
                    onBringBack = onBringBack,
                    onInterrupt = onInterrupt,
                    onReorder = onReorder,
                    onEditTask = onEditTask,
                    onOpenSheet = onOpenSheet,
                    onCheckOffRepeat = onCheckOffRepeat,
                    onOpenMissedRepeat = onOpenMissedRepeat,
                    onUndoRepeat = onUndoRepeat,
                    onEditRepeat = onEditRepeat,
                )
            }
        }
    }
}

@Composable
private fun HomeBandBlock(
    group: HomeBandGroup,
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
    onEditTask: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
    onCheckOffRepeat: (RepeatRow) -> Unit,
    onOpenMissedRepeat: (RepeatRow) -> Unit,
    onUndoRepeat: (RepeatRow) -> Unit,
    onEditRepeat: (Repeat) -> Unit,
) {
    val toggleable = group.phase != BandPhase.ALWAYS_OPEN
    val startsExpanded = group.phase == BandPhase.OPEN
    var expanded by rememberSaveable(group.phase.name, key = group.band.name) {
        mutableStateOf(startsExpanded)
    }
    val showRows = if (toggleable) expanded else true
    Column {
        HomeBandHeader(
            group = group,
            expanded = showRows,
            toggleable = toggleable,
            onToggle = { expanded = !expanded },
        )
        if (showRows) {
            TodaySection(
                filled = filled.forBand(group),
                hasRoom = group.phase == BandPhase.OPEN && hasRoom,
                todayLimit = todayLimit,
                reducedMotion = reducedMotion,
                somethingCurrent = somethingCurrent,
                onOpenFocus = onOpenFocus,
                onStart = onStart,
                onDone = onDone,
                onBringBack = onBringBack,
                onInterrupt = onInterrupt,
                onReorder = { order ->
                    if (group.phase == BandPhase.OPEN) onReorder(order)
                },
                onEdit = onEditTask,
                onOpenSheet = onOpenSheet,
                showHeader = false,
                layout = HomeLayout.BANDS,
                todayTaskIds = filled.map { it.second.id }.toSet(),
            )
            if (group.repeats.isNotEmpty()) {
                RepeatRowGroup(
                    rows = group.repeats,
                    reducedMotion = reducedMotion,
                    layout = HomeLayout.BANDS,
                    onCheckOff = onCheckOffRepeat,
                    onOpenMissed = onOpenMissedRepeat,
                    onUndo = onUndoRepeat,
                    onEdit = onEditRepeat,
                )
            }
        }
    }
}

@Composable
private fun HomeBandHeader(
    group: HomeBandGroup,
    expanded: Boolean,
    toggleable: Boolean,
    onToggle: () -> Unit,
) {
    val name = stringResource(group.band.titleRes()).uppercase()
    val current = group.phase == BandPhase.OPEN
    val title =
        if (current) stringResource(R.string.home_band_current, name) else name
    val color = if (current) FocusColors.Live.Lavender else FocusColors.Live.Muted
    val trailing = bandCountCopy(group)
    val interaction = rememberPressSource()
    val clickModifier =
        if (toggleable) {
            val action =
                if (expanded) {
                    stringResource(R.string.home_band_collapse, name)
                } else {
                    stringResource(R.string.home_band_expand, name)
                }
            Modifier
                .pressScale(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = action,
                    onClick = onToggle,
                )
        } else {
            Modifier
        }
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = HomeMetrics.HomeTouchTarget)
            .then(clickModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = HomeMetrics.Eyebrow,
            color = color,
            modifier = Modifier.semantics { heading() },
        )
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                style = HomeMetrics.Eyebrow,
                color = color,
            )
        }
    }
}

/**
 * Current band: "N things". A band with done repeats: "X of Y done".
 * Otherwise a plain remaining count.
 */
@Composable
internal fun bandCountCopy(group: HomeBandGroup): String {
    val counts = group.counts
    val total = counts.done + counts.left
    val hasDoneRepeats = group.repeats.any { it.status == RepeatDayStatus.DONE }
    return when {
        group.phase == BandPhase.OPEN ->
            pluralStringResource(R.plurals.home_band_count_things, counts.left, counts.left)
        hasDoneRepeats -> stringResource(R.string.home_band_count_of_done, counts.done, total)
        else -> stringResource(R.string.home_band_count, counts.left)
    }
}

private fun HomeBand.titleRes(): Int = when (this) {
    HomeBand.MORNING -> R.string.home_band_morning
    HomeBand.NOON -> R.string.home_band_noon
    HomeBand.NIGHT -> R.string.home_band_night
    HomeBand.ANYTIME -> R.string.home_band_anytime
}

private fun List<Pair<SlotUi, TaskUi>>.forBand(group: HomeBandGroup): List<Pair<SlotUi, TaskUi>> {
    val byId = associateBy { it.second.id }
    return group.tasks.map { task ->
        byId[task.id] ?: task.asOpenSlot()
    }
}

private fun Task.asOpenSlot(): Pair<SlotUi, TaskUi> {
    val ui =
        TaskUi(
            id = id,
            title = title,
            returnCue = returnCue,
            scheduledAtMinuteOfDay = scheduledAtMinuteOfDay,
        )
    return SlotUi(SlotRole.OPEN, ui) to ui
}
