package app.backpin.focus.ui.countdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.backpin.focus.domain.DetachDestination
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.home.QuietButton
import java.time.LocalDate

/**
 * Where an arrived event goes instead of Done: Today, Later, or a new countdown day.
 * Each one closes the event and leaves the paused task waiting for Resume.
 * Emits its buttons into the caller's row, next to Done.
 */
@Composable
internal fun EventDetachButtons(
    taskId: String,
    onDetach: (String, DetachDestination) -> Unit,
) {
    var picking by rememberSaveable(taskId) { mutableStateOf(false) }
    QuietButton("Today", onClick = { onDetach(taskId, DetachDestination.Today) })
    QuietButton("Later", onClick = { onDetach(taskId, DetachDestination.Later) })
    QuietButton("Count down again", onClick = { picking = true })
    if (picking) {
        val tomorrow = LocalDate.now().plusDays(1)
        PickDateDialog(
            initial = tomorrow,
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                onDetach(taskId, DetachDestination.Countdown(date.toEpochDay()))
            },
            onDismiss = { picking = false },
        )
    }
}
