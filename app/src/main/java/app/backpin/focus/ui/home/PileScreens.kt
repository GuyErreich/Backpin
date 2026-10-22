// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.TaskUi
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private val PileRowPadding = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
private val ActionPileCardPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

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
    onEdit: (String) -> Unit,
    onOpenSheet: (String) -> Unit = {},
    doneTasks: List<TaskUi> = emptyList(),
    onRestore: (String) -> Unit = {},
    showBack: Boolean = true,
    modifier: Modifier = Modifier,
) {
    PileScaffold(
        title = stringResource(R.string.screen_inbox),
        count = tasks.size,
        ready = ready,
        onBack = onBack,
        showBack = showBack,
        modifier = modifier,
    ) {
        ActionPileCards(
            title = stringResource(R.string.screen_inbox),
            emptyLabel = stringResource(R.string.pile_inbox_empty),
            tasks = tasks,
            canInterrupt = canInterrupt,
            onToday = onToday,
            onStart = onStart,
            onInterrupt = onInterrupt,
            onEdit = onEdit,
            onOpenSheet = onOpenSheet,
        )
        DoneSection(tasks = doneTasks, onRestore = onRestore)
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
        title = stringResource(R.string.screen_done),
        count = tasks.size,
        ready = ready,
        onBack = onBack,
        modifier = modifier,
    ) {
        DoneCards(tasks = tasks, onRestore = onRestore)
    }
}

@Composable
internal fun ActionPileCards(
    title: String,
    emptyLabel: String,
    tasks: List<TaskUi>,
    canInterrupt: Boolean,
    onToday: (String) -> Unit,
    onStart: (String) -> Unit,
    onInterrupt: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenSheet: (String) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    if (tasks.isEmpty()) {
        Text(emptyLabel, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Live.Muted)
    }
    tasks.forEach { task ->
        val left = pileLeftSwipe(canInterrupt)
        SwipeRevealCard(
            towardStart = todaySwipe { onToday(task.id) },
            towardEnd =
            left.side(
                onStart = { onStart(task.id) },
                onInterrupt = { onInterrupt(task.id) },
            ),
            reducedMotion = reducedMotion,
            extraActions =
            listOfNotNull(
                CustomAccessibilityAction("Today") {
                    onToday(task.id)
                    true
                },
                CustomAccessibilityAction("Start") {
                    onStart(task.id)
                    true
                },
                if (canInterrupt) {
                    CustomAccessibilityAction("Interrupt") {
                        onInterrupt(task.id)
                        true
                    }
                } else {
                    null
                },
                CustomAccessibilityAction("Edit") {
                    onEdit(task.id)
                    true
                },
            ),
            cornerRadius = CardRadius,
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "$title. ${task.title}. Opens the task sheet. Swipe right for today, swipe left for ${left.name.lowercase()}."
                },
        ) {
            Surface(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.task_sheet_open),
                        onClick = { onOpenSheet(task.id) },
                    ),
                shape = RoundedCornerShape(CardRadius),
                color = FocusColors.Live.SurfaceHigh,
            ) {
                Column(ActionPileCardPadding, verticalArrangement = Arrangement.spacedBy(Space)) {
                    Text(task.title, style = MaterialTheme.typography.titleLarge)
                    CardActions {
                        QuietButton(label = "Today", onClick = { onToday(task.id) })
                        if (canInterrupt) {
                            AccentButton(
                                label = "Interrupt",
                                tone = AccentTone.Lavender,
                                onClick = { onInterrupt(task.id) },
                            )
                        } else {
                            QuietButton(label = "Start", onClick = { onStart(task.id) })
                        }
                        QuietButton(label = "Edit", onClick = { onEdit(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun DoneCards(
    tasks: List<TaskUi>,
    onRestore: (String) -> Unit,
) {
    if (tasks.isEmpty()) {
        Text(
            stringResource(R.string.pile_done_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = FocusColors.Live.Muted,
        )
    }
    tasks.forEach { task ->
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Done. ${task.title}. Restore returns it to Inbox." },
            shape = RoundedCornerShape(16.dp),
            color = FocusColors.Live.SurfaceHigh,
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

@Composable
private fun DoneSection(
    tasks: List<TaskUi>,
    onRestore: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val name = stringResource(R.string.screen_done)
    val action =
        if (expanded) {
            stringResource(R.string.home_done_collapse)
        } else {
            stringResource(R.string.home_done_expand)
        }
    val interaction = rememberPressSource()
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = HomeMetrics.HomeTouchTarget)
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = action,
                onClick = { expanded = !expanded },
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name.uppercase(),
            style = HomeMetrics.Eyebrow,
            color = FocusColors.Live.Muted,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            tasks.size.toString(),
            style = HomeMetrics.Eyebrow,
            color = FocusColors.Live.Muted,
        )
    }
    if (expanded) {
        DoneCards(tasks = tasks, onRestore = onRestore)
    }
}

@Composable
private fun PileScaffold(
    title: String,
    count: Int,
    ready: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        PileHeader(title = title, count = count, onBack = onBack, showBack = showBack)
        if (!ready) {
            Text(stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyLarge)
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
    showBack: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showBack) {
            BackArrow(label = BACK_LABEL, onClick = onBack)
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
            color = FocusColors.Live.Mint,
        )
    }
}
