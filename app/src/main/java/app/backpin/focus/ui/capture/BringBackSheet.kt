// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.backpin.focus.R
import app.backpin.focus.domain.ScheduleOffset
import app.backpin.focus.domain.scheduleDate
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChipSheet
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.theme.ButtonGap
import java.time.LocalDate

/**
 * Capture's days/date picker, hosted as its own sheet. Tomorrow, in 7 days, or a
 * calendar day sends the item to Coming up. [onKeepInInbox] parks it with no date.
 * Back and outside dismiss change nothing.
 */
@Composable
internal fun BringBackSheet(
    title: String,
    today: LocalDate,
    onDismiss: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onKeepInInbox: () -> Unit,
    earliest: LocalDate = today,
) {
    var picking by remember { mutableStateOf(false) }
    ChipSheet(
        title = stringResource(R.string.bring_back_sheet_title),
        subtitle = title.takeIf { it.isNotBlank() },
        lede = stringResource(R.string.bring_back_sheet_lede),
        onDismiss = onDismiss,
    ) {
        BringBackChoices(
            today = today,
            onDate = onDate,
            onPickDate = { picking = true },
            onKeepInInbox = onKeepInInbox,
        )
    }
    if (picking) {
        PickDateDialog(
            initial = maxOf(today.plusDays(1), earliest),
            earliest = earliest,
            onPick = { date ->
                picking = false
                onDate(date)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Tomorrow, in 7 days, or the day chosen on the calendar. Optional Inbox keeps
 * the item with no date. Capture omits Inbox because Inbox is already a place.
 */
@Composable
internal fun BringBackChoices(
    today: LocalDate,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onKeepInInbox: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
        CaptureChoice(
            label = stringResource(R.string.action_tomorrow),
            onClick = { onDate(scheduleDate(today, ScheduleOffset.TOMORROW)) },
        )
        CaptureChoice(
            label = stringResource(R.string.action_in_7_days),
            onClick = { onDate(scheduleDate(today, ScheduleOffset.IN_7_DAYS)) },
        )
        CaptureChoice(
            label = stringResource(R.string.action_pick_a_date),
            onClick = onPickDate,
        )
        if (onKeepInInbox != null) {
            CaptureChoice(
                label = stringResource(R.string.action_keep_in_inbox),
                tone = ChoiceTone.Mint,
                onClick = onKeepInInbox,
            )
        }
    }
}
