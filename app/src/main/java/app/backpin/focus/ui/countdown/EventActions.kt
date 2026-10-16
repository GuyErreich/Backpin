package app.backpin.focus.ui.countdown

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate

/**
 * Where an arrived event goes instead of Done: Today, Later, or a new countdown day.
 * Each one closes the event and leaves the paused task waiting for Resume.
 * Emits its buttons into the caller's actions, next to Done.
 * [onCountDownAgain] opens the resume sheet; host that sheet outside card actions
 * so its buttons keep sheet height.
 */
@Composable
internal fun EventDetachButtons(
    taskId: String,
    onDetach: (String, DetachDestination) -> Unit,
    onCountDownAgain: () -> Unit,
) {
    QuietButton("Today", onClick = { onDetach(taskId, DetachDestination.Today) })
    QuietButton("Later", onClick = { onDetach(taskId, DetachDestination.Later) })
    QuietButton("Count down again", onClick = onCountDownAgain)
}

/**
 * Hosts the resume sheet beside card actions so the sheet does not inherit card-action size.
 * [content] receives the click that opens the sheet.
 */
@Composable
internal fun EventResumeHost(
    taskId: String,
    suggestedDays: Int?,
    onDetach: (String, DetachDestination) -> Unit,
    content: @Composable (onCountDownAgain: () -> Unit) -> Unit,
) {
    var open by rememberSaveable(taskId) { mutableStateOf(false) }
    content { open = true }
    if (open) {
        ResumeCountdownSheet(
            suggestedDays = suggestedDays,
            onDismiss = { open = false },
            onCommit = { day ->
                open = false
                onDetach(taskId, DetachDestination.Countdown(day))
            },
        )
    }
}

/**
 * Again in N days when a count is known, then the same days field and calendar as Add and Change.
 * Back closes without detaching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResumeCountdownSheet(
    suggestedDays: Int?,
    onDismiss: () -> Unit,
    onCommit: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var daysText by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val sent = remember { booleanArrayOf(false) }
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val typedDay = countdownDays(daysText)?.let { today.plusDays(it.toLong()).toEpochDay() }

    fun commit(day: Long) {
        if (sent[0]) return
        sent[0] = true
        onCommit(day)
    }

    fun commitTypedDays() {
        val days = countdownDays(daysText) ?: return
        commit(today.plusDays(days.toLong()).toEpochDay())
    }

    fun commitAgain(days: Int) {
        commit(today.plusDays(days.toLong()).toEpochDay())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = onDismiss)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(label = CLOSE_LABEL, onClick = onDismiss, inSheet = true)
                Text(
                    "Count down again",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            if (suggestedDays != null) {
                CaptureChoice(
                    label = "Again in $suggestedDays days",
                    onClick = { commitAgain(suggestedDays) },
                    tone = ChoiceTone.Lavender,
                )
            }
            CountdownDayEntry(
                daysText = daysText,
                onDaysChange = { daysText = sanitizeCountdownDays(it) },
                shownDay = typedDay,
                onDone = { commitTypedDays() },
                onPickDate = { picking = true },
            )
        }
    }
    if (picking) {
        PickDateDialog(
            initial = tomorrow,
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}
