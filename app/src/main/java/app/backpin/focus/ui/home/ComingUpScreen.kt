// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.ComingUpRemaining
import app.backpin.focus.domain.ComingUpSection
import app.backpin.focus.domain.CountdownUnit
import app.backpin.focus.domain.comingUpRemaining
import app.backpin.focus.domain.comingUpSection
import app.backpin.focus.domain.countdownDaysLeft
import app.backpin.focus.ui.CountdownUi
import app.backpin.focus.ui.ScheduledTaskUi
import app.backpin.focus.ui.components.AddFab
import app.backpin.focus.ui.countdown.AddCountdownSheet
import app.backpin.focus.ui.countdown.ChangeCountdownSheet
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.FocusColors
import java.time.LocalDate

/**
 * Scheduled tasks and countdown events on one date-sorted list.
 * No back arrow — this is a root tab. No Later.
 */
@Composable
fun ComingUpScreen(
    scheduledTasks: List<ScheduledTaskUi>,
    countdowns: List<CountdownUi>,
    ready: Boolean,
    today: LocalDate,
    countdownUnit: CountdownUnit,
    onEdit: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
    onOpenMissed: (String) -> Unit,
    onAddCountdown: (String, Long) -> Unit,
    onChangeCountdownDate: (String, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingCountdown by rememberSaveable { mutableStateOf(false) }
    var changingCountdownId by rememberSaveable { mutableStateOf<String?>(null) }
    val entries =
        remember(scheduledTasks, countdowns, today) {
            comingUpEntries(scheduledTasks, countdowns, today)
        }
    val grouped = remember(entries) { entries.groupBy { it.section } }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = HomeMetrics.PageHorizontal,
                    end = HomeMetrics.PageHorizontal,
                    top = HomeMetrics.PageVertical,
                    bottom = HomeMetrics.ListBottom,
                ),
        ) {
            Text(
                text = stringResource(R.string.coming_up_eyebrow),
                style = HomeMetrics.Eyebrow,
                color = FocusColors.Live.Muted,
                modifier = Modifier.padding(top = HomeMetrics.EyebrowTop, bottom = HomeMetrics.EyebrowBottom),
            )
            Text(
                text = stringResource(R.string.coming_up_title),
                style = HomeMetrics.ScreenTitle.copy(fontWeight = FontWeight.Bold),
                color = FocusColors.Live.Text,
                modifier =
                Modifier
                    .padding(bottom = HomeMetrics.TitleBottom)
                    .semantics { heading() },
            )
            if (!ready) {
                Text(
                    stringResource(R.string.home_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusColors.Live.Text,
                )
            } else if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.coming_up_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusColors.Live.Muted,
                )
            } else {
                ComingUpSection.entries.forEach { section ->
                    val rows = grouped[section].orEmpty()
                    if (rows.isNotEmpty()) {
                        SectionLabel(
                            stringResource(section.labelRes),
                            rows.size.toString(),
                            modifier = Modifier.padding(top = HomeMetrics.FlatHeaderTop),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(HomeMetrics.SlotGap)) {
                            rows.forEach { entry ->
                                val remaining =
                                    entry.epochDay?.let { day ->
                                        comingUpRemaining(day, today, countdownUnit)
                                    }
                                val remainingText = remainingLabel(remaining)
                                ComingUpRow(
                                    title = entry.title,
                                    remaining = remainingText,
                                    onClick = {
                                        when {
                                            entry.isCountdown -> changingCountdownId = entry.id
                                            entry.due -> onOpenMissed(entry.id)
                                            else -> onOpenSheet(entry.id)
                                        }
                                    },
                                    onLongClick = { onEdit(entry.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        AddFab(
            onClick = { addingCountdown = true },
            labelRes = R.string.action_plan_ahead,
            modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }
    val changing = countdowns.firstOrNull { it.id == changingCountdownId }
    if (changing != null) {
        ChangeCountdownSheet(
            epochDay = changing.epochDay,
            today = today,
            onDismiss = { changingCountdownId = null },
            onCommit = { day ->
                val id = changing.id
                changingCountdownId = null
                onChangeCountdownDate(id, day)
            },
        )
    }
    if (addingCountdown) {
        AddCountdownSheet(
            onDismiss = { addingCountdown = false },
            onAdd = { title, day ->
                addingCountdown = false
                onAddCountdown(title, day)
            },
            today = today,
        )
    }
}

@Composable
private fun ComingUpRow(
    title: String,
    remaining: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = rememberPressSource()
    val description =
        if (remaining.isEmpty()) {
            title
        } else {
            stringResource(R.string.coming_up_row_desc, title, remaining)
        }
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(HomeMetrics.SlotRadius),
        color = FocusColors.Live.Surface,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = HomeMetrics.HomeTouchTarget)
                .padding(
                    horizontal = HomeMetrics.SlotPaddingHorizontal,
                    vertical = HomeMetrics.SlotPaddingVertical,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = HomeMetrics.SlotTitle,
                color = FocusColors.Live.Text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (remaining.isNotEmpty()) {
                Text(
                    remaining,
                    style = HomeMetrics.SlotChevron,
                    color = FocusColors.Live.Muted,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun remainingLabel(remaining: ComingUpRemaining?): String = when (remaining) {
    null -> ""
    ComingUpRemaining.Passed -> stringResource(R.string.coming_up_passed)
    ComingUpRemaining.Today -> stringResource(R.string.coming_up_today)
    is ComingUpRemaining.Days ->
        pluralStringResource(R.plurals.coming_up_in_days, remaining.count, remaining.count)
    is ComingUpRemaining.Months ->
        pluralStringResource(R.plurals.coming_up_in_months, remaining.count, remaining.count)
}

private val ComingUpSection.labelRes: Int
    get() =
        when (this) {
            ComingUpSection.TODAY -> R.string.coming_up_group_today
            ComingUpSection.TOMORROW -> R.string.coming_up_group_tomorrow
            ComingUpSection.THIS_WEEK -> R.string.coming_up_group_this_week
            ComingUpSection.FURTHER_OUT -> R.string.coming_up_group_further_out
        }

private data class ComingUpEntry(
    val id: String,
    val title: String,
    val epochDay: Long?,
    val isCountdown: Boolean,
    val due: Boolean,
    val section: ComingUpSection,
)

private fun comingUpEntries(
    scheduledTasks: List<ScheduledTaskUi>,
    countdowns: List<CountdownUi>,
    today: LocalDate,
): List<ComingUpEntry> {
    val scheduled =
        scheduledTasks.map { task ->
            val daysLeft = task.epochDay?.let { countdownDaysLeft(it, today) }
            ComingUpEntry(
                id = task.id,
                title = task.title,
                epochDay = task.epochDay,
                isCountdown = false,
                due = task.due,
                section = daysLeft?.let { comingUpSection(it) } ?: ComingUpSection.FURTHER_OUT,
            )
        }
    val events =
        countdowns.map { countdown ->
            ComingUpEntry(
                id = countdown.id,
                title = countdown.title,
                epochDay = countdown.epochDay,
                isCountdown = true,
                due = false,
                section = comingUpSection(countdownDaysLeft(countdown.epochDay, today)),
            )
        }
    return (scheduled + events).sortedWith(
        compareBy(
            { it.epochDay ?: Long.MAX_VALUE },
            { it.title.lowercase() },
            { it.id },
        ),
    )
}
