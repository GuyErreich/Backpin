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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

/**
 * Inbox on its own screen. Today and Start move the task and leave this screen open.
 */
@Composable
fun InboxScreen(
    tasks: List<TaskUi>,
    ready: Boolean,
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPileScreen(
        title = "Inbox",
        emptyLabel = "Inbox is clear.",
        tasks = tasks,
        ready = ready,
        onBack = onBack,
        onToday = onToday,
        onStart = onStart,
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
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ActionPileScreen(
        title = "Later",
        emptyLabel = "Nothing parked for later.",
        tasks = tasks,
        ready = ready,
        onBack = onBack,
        onToday = onToday,
        onStart = onStart,
        modifier = modifier,
    )
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
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    QuietButton(label = "Restore", onClick = { onRestore(task.id) })
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
    onBack: () -> Unit,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
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
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(task.title, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            QuietButton(label = "Today", onClick = { onToday(task.id) })
                            QuietButton(label = "Start", onClick = { onStart(task.id) })
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
        verticalArrangement = Arrangement.spacedBy(Space),
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
        val backInteraction = rememberPressSource()
        WithoutRipple {
            IconButton(
                onClick = onBack,
                interactionSource = backInteraction,
                modifier = Modifier.pressScale(backInteraction),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Today",
                    tint = FocusColors.Mint,
                )
            }
        }
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
