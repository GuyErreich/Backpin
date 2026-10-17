// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.BandPhase
import app.backpin.focus.domain.HomeBand
import app.backpin.focus.domain.HomeBandCounts
import app.backpin.focus.domain.HomeBandGroup
import app.backpin.focus.domain.HomeBands
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatRow
import app.backpin.focus.domain.Task
import app.backpin.focus.ui.SlotRole
import app.backpin.focus.ui.SlotUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

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
    onLater: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEditTask: (String) -> Unit,
    onCheckOffRepeat: (RepeatRow) -> Unit,
    onEditRepeat: (Repeat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                    onLater = onLater,
                    onInterrupt = onInterrupt,
                    onReorder = onReorder,
                    onEditTask = onEditTask,
                    onCheckOffRepeat = onCheckOffRepeat,
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
    onLater: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onEditTask: (String) -> Unit,
    onCheckOffRepeat: (RepeatRow) -> Unit,
    onEditRepeat: (Repeat) -> Unit,
) {
    val toggleable = group.phase != BandPhase.ALWAYS_OPEN
    val startsExpanded = group.phase == BandPhase.OPEN
    var expanded by rememberSaveable(group.phase.name, key = group.band.name) {
        mutableStateOf(startsExpanded)
    }
    val showRows = if (toggleable) expanded else true
    Column(verticalArrangement = Arrangement.spacedBy(Space)) {
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
                onLater = onLater,
                onInterrupt = onInterrupt,
                onReorder = { order ->
                    if (group.phase == BandPhase.OPEN) onReorder(order)
                },
                onEdit = onEditTask,
                showHeader = false,
            )
            group.repeats.forEach { row ->
                key(row.repeat.id) {
                    RepeatRow(
                        row = row,
                        reducedMotion = reducedMotion,
                        onCheckOff = { onCheckOffRepeat(row) },
                        onEdit = { onEditRepeat(row.repeat) },
                    )
                }
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
    val name = stringResource(group.band.titleRes())
    val counts = bandCountsLabel(group.counts)
    val muted = group.phase == BandPhase.PAST || group.phase == BandPhase.FUTURE
    val color = if (muted) FocusColors.Live.Muted else FocusColors.Live.Text
    val summary =
        when {
            !expanded && group.phase == BandPhase.PAST ->
                if (counts.isEmpty()) name else stringResource(R.string.home_band_summary, name, counts)
            else -> null
        }
    val trailing =
        when {
            summary != null -> null
            !expanded && group.phase == BandPhase.FUTURE ->
                stringResource(R.string.home_band_count, group.counts.left)
            counts.isNotEmpty() -> counts
            else -> null
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(clickModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = colorMissedSegment(summary ?: name, group.counts.missed),
            style = if (summary != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = colorMissedSegment(trailing, group.counts.missed),
                style = MaterialTheme.typography.bodyMedium,
                color = FocusColors.Live.Muted,
            )
        }
    }
}

@Composable
private fun colorMissedSegment(
    text: String,
    missedCount: Int,
) = if (missedCount <= 0) {
    buildAnnotatedString { append(text) }
} else {
    val missed = stringResource(R.string.home_band_count_missed, missedCount)
    buildAnnotatedString {
        append(text)
        val start = text.indexOf(missed)
        if (start >= 0) {
            addStyle(SpanStyle(color = FocusColors.Live.Amber), start, start + missed.length)
        }
    }
}

@Composable
private fun bandCountsLabel(counts: HomeBandCounts): String {
    val parts =
        buildList {
            if (counts.done > 0) add(stringResource(R.string.home_band_count_done, counts.done))
            if (counts.missed > 0) add(stringResource(R.string.home_band_count_missed, counts.missed))
            if (counts.left > 0) add(stringResource(R.string.home_band_count_left, counts.left))
        }
    val separator = stringResource(R.string.home_band_count_separator)
    return parts.joinToString(separator)
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
    val ui = TaskUi(id = id, title = title, returnCue = returnCue)
    return SlotUi(SlotRole.OPEN, ui) to ui
}
