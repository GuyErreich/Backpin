package app.backpin.focus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.ScheduledTaskUi
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private val PileRowPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)

/**
 * Inbox on its own screen. Today and Start move the task and leave this screen open.
 */
@Composable
fun InboxScreen(
    tasks: List<TaskUi>,
    ready: Boolean,
    canInterrupt: Boolean,
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPileScreen(
        title = "Inbox",
        emptyLabel = "Inbox is clear.",
        tasks = tasks,
        ready = ready,
        canInterrupt = canInterrupt,
        onBack = onBack,
        onToday = onToday,
        onStart = onStart,
        onInterrupt = onInterrupt,
        modifier = modifier,
    )
}

/**
 * Later on its own screen. Today and Start move the task and leave this screen open.
 */
@Composable
fun LaterScreen(
    tasks: List<TaskUi>,
    ready: Boolean,
    canInterrupt: Boolean,
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPileScreen(
        title = "Later",
        emptyLabel = "Nothing parked for later.",
        tasks = tasks,
        ready = ready,
        canInterrupt = canInterrupt,
        onBack = onBack,
        onToday = onToday,
        onStart = onStart,
        onInterrupt = onInterrupt,
        modifier = modifier,
    )
}

/**
 * Scheduled on its own screen. A task stays here until its local date, then moves onto Today
 * without starting. From here it can move early, or be removed.
 */
@Composable
fun ScheduledScreen(
    tasks: List<ScheduledTaskUi>,
    ready: Boolean,
    canInterrupt: Boolean,
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onInbox: (String) -> Unit,
    onLater: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    PileScaffold(
        title = "Scheduled",
        count = tasks.size,
        ready = ready,
        onBack = onBack,
        modifier = modifier,
    ) {
        if (tasks.isEmpty()) {
            Text("Nothing is waiting on a date.", style = MaterialTheme.typography.bodyLarge, color = FocusColors.Muted)
        }
        tasks.forEach { task ->
            SwipeRevealCard(
                towardStart = todaySwipe { onToday(task.id) },
                towardEnd = startSwipe { onStart(task.id) },
                reducedMotion = reducedMotion,
                cornerRadius = 16.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "Scheduled. ${task.title}. ${task.whenLabel}. Swipe right for today, swipe left for start."
                        },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FocusColors.SurfaceHigh,
                ) {
                    Column(PileRowPadding, verticalArrangement = Arrangement.spacedBy(Space)) {
                        Column {
                            Text(task.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                task.whenLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (task.due) FocusColors.Amber else FocusColors.Muted,
                            )
                        }
                        CardActions {
                            QuietButton(label = "Today", onClick = { onToday(task.id) })
                            QuietButton(label = "Start", onClick = { onStart(task.id) })
                            if (canInterrupt) {
                                AccentButton(
                                    label = "Interrupt",
                                    tone = AccentTone.Lavender,
                                    onClick = { onInterrupt(task.id) },
                                )
                            }
                            QuietButton(label = "Inbox", onClick = { onInbox(task.id) })
                            QuietButton(label = "Later", onClick = { onLater(task.id) })
                            QuietButton(label = "Remove", onClick = { onRemove(task.id) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * Done (archive) on its own screen. Restore returns a task to Inbox with no confirm.
 */
@Composable
fun DoneScreen(
    tasks: List<TaskUi>,
    ready: Boolean,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PileScaffold(
        title = "Done",
        count = tasks.size,
        ready = ready,
        onBack = onBack,
        modifier = modifier,
    ) {
        if (tasks.isEmpty()) {
            Text("Nothing in the archive.", style = MaterialTheme.typography.bodyLarge, color = FocusColors.Muted)
        }
        tasks.forEach { task ->
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Done. ${task.title}. Restore returns it to Inbox." },
                shape = RoundedCornerShape(16.dp),
                color = FocusColors.SurfaceHigh,
            ) {
                Column(PileRowPadding, verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(task.title, style = MaterialTheme.typography.titleLarge)
                    CardActions {
                        QuietButton(label = "Restore", onClick = { onRestore(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPileScreen(
    title: String,
    emptyLabel: String,
    tasks: List<TaskUi>,
    ready: Boolean,
    canInterrupt: Boolean,
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    PileScaffold(
        title = title,
        count = tasks.size,
        ready = ready,
        onBack = onBack,
        modifier = modifier,
    ) {
        if (tasks.isEmpty()) {
            Text(emptyLabel, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Muted)
        }
        tasks.forEach { task ->
            SwipeRevealCard(
                towardStart = todaySwipe { onToday(task.id) },
                towardEnd = startSwipe { onStart(task.id) },
                reducedMotion = reducedMotion,
                cornerRadius = 16.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription =
                                "$title. ${task.title}. Swipe right for today, swipe left for start."
                        },
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = FocusColors.SurfaceHigh,
                ) {
                    Column(PileRowPadding, verticalArrangement = Arrangement.spacedBy(Space)) {
                        Text(task.title, style = MaterialTheme.typography.titleLarge)
                        CardActions {
                            QuietButton(label = "Today", onClick = { onToday(task.id) })
                            QuietButton(label = "Start", onClick = { onStart(task.id) })
                            if (canInterrupt) {
                                AccentButton(
                                    label = "Interrupt",
                                    tone = AccentTone.Lavender,
                                    onClick = { onInterrupt(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PileScaffold(
    title: String,
    count: Int,
    ready: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        PileHeader(title = title, count = count, onBack = onBack)
        if (!ready) {
            Text("Loading your place…", style = MaterialTheme.typography.bodyLarge)
        } else {
            content()
        }
    }
}

@Composable
private fun PileHeader(
    title: String,
    count: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BackArrow(label = BACK_LABEL, onClick = onBack)
        Text(
            title,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = FocusColors.Mint,
        )
    }
}
