// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.CountdownUi
import app.backpin.focus.ui.ScheduledTaskUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.countdown.CountdownPile
import app.backpin.focus.ui.theme.ButtonGap

/**
 * Later, Scheduled, and Countdown on one tab. No back arrow — this is a root tab.
 */
@Composable
fun ComingUpScreen(
    laterTasks: List<TaskUi>,
    scheduledTasks: List<ScheduledTaskUi>,
    countdowns: List<CountdownUi>,
    ready: Boolean,
    canInterrupt: Boolean,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
    onInbox: (String) -> Unit,
    onLater: (String) -> Unit,
    onRemoveScheduled: (String) -> Unit,
    onChangeDate: (String, Long) -> Unit,
    onChangeTime: (String, Int?) -> Unit,
    onOpenMissed: (String) -> Unit,
    onAddCountdown: (String, Long) -> Unit,
    onChangeCountdownDate: (String, Long) -> Unit,
    onRemoveCountdown: (String) -> Unit,
    onEditCountdown: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingCountdown by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = HomeMetrics.ListBottom),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        Text(
            stringResource(R.string.screen_coming_up),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (!ready) {
            Text(
                stringResource(R.string.home_loading),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            SectionLabel(
                stringResource(R.string.screen_scheduled).uppercase(),
                scheduledTasks.size.toString(),
            )
            ScheduledPile(
                tasks = scheduledTasks,
                canInterrupt = canInterrupt,
                emptyLabel = stringResource(R.string.pile_scheduled_empty),
                onToday = onToday,
                onStart = onStart,
                onInterrupt = onInterrupt,
                onInbox = onInbox,
                onLater = onLater,
                onRemove = onRemoveScheduled,
                onChangeDate = onChangeDate,
                onChangeTime = onChangeTime,
                onEdit = onEdit,
                onOpenMissed = onOpenMissed,
            )
            SectionLabel(
                stringResource(R.string.screen_later).uppercase(),
                laterTasks.size.toString(),
            )
            ActionPileCards(
                title = stringResource(R.string.screen_later),
                emptyLabel = stringResource(R.string.pile_later_empty),
                tasks = laterTasks,
                canInterrupt = canInterrupt,
                onToday = onToday,
                onStart = onStart,
                onInterrupt = onInterrupt,
                onEdit = onEdit,
                onOpenSheet = onOpenSheet,
            )
            SectionLabel(
                stringResource(R.string.screen_countdown).uppercase(),
                countdowns.size.toString(),
            )
            AccentButton(
                label = stringResource(R.string.action_add_countdown),
                tone = AccentTone.Amber,
                onClick = { addingCountdown = true },
                modifier = Modifier.fillMaxWidth(),
            )
            CountdownPile(
                countdowns = countdowns,
                emptyLabel = stringResource(R.string.pile_countdown_empty),
                adding = addingCountdown,
                onAddingChange = { addingCountdown = it },
                onAdd = onAddCountdown,
                onChangeDate = onChangeCountdownDate,
                onRemove = onRemoveCountdown,
                onEdit = onEditCountdown,
            )
        }
    }
}
