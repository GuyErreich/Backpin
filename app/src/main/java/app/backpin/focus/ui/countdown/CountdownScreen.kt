// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.countdown

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.CountdownUi
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.capture.BRING_BACK_DEFAULT_DAYS
import app.backpin.focus.ui.capture.BRING_BACK_MAX_DAYS
import app.backpin.focus.ui.capture.BRING_BACK_MIN_DAYS
import app.backpin.focus.ui.capture.DaysWheel
import app.backpin.focus.ui.capture.SpeakMic
import app.backpin.focus.ui.capture.TitleDraft
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.capture.rememberSpeechInput
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.PrimaryButton
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate

/**
 * Events counted in local days, nearest first. None takes a Today slot.
 * On its day, outside quiet hours, an event interrupts whatever is in focus.
 */
@Composable
internal fun CountdownScreen(
    countdowns: List<CountdownUi>,
    ready: Boolean,
    today: LocalDate,
    onAdd: (String, Long) -> Unit,
    onChangeDate: (String, Long) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    initialDays: Int = BRING_BACK_DEFAULT_DAYS,
    modifier: Modifier = Modifier,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var changingId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Space * 2),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Countdown",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                countdowns.size.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = FocusColors.Live.Amber,
            )
        }
        AccentButton(
            label = "Add countdown",
            tone = AccentTone.Amber,
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            !ready -> Text("Loading your place…", style = MaterialTheme.typography.bodyLarge)
            countdowns.isEmpty() ->
                Text(
                    "Nothing to count down to. Add an event and it will interrupt you on the day.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusColors.Live.Muted,
                )
            else ->
                countdowns.forEach { countdown ->
                    CountdownRow(
                        countdown = countdown,
                        onChangeDate = { changingId = countdown.id },
                        onRemove = { onRemove(countdown.id) },
                        onEdit = { onEdit(countdown.id) },
                    )
                }
        }
    }
    val changing = countdowns.firstOrNull { it.id == changingId }
    if (changing != null) {
        ChangeCountdownSheet(
            epochDay = changing.epochDay,
            today = today,
            onDismiss = { changingId = null },
            onCommit = { day ->
                val id = changing.id
                changingId = null
                onChangeDate(id, day)
            },
        )
    }
    if (adding) {
        AddCountdownSheet(
            onDismiss = { adding = false },
            onAdd = { title, day ->
                adding = false
                onAdd(title, day)
            },
            today = today,
            initialDays = initialDays,
            recognizer = recognizer,
            onSystemSpeech = onSystemSpeech,
        )
    }
}

@Composable
private fun CountdownRow(
    countdown: CountdownUi,
    onChangeDate: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = "Countdown. ${countdown.title}. ${countdown.dayLabel}, ${countdown.dateLabel}."
                customActions =
                    listOf(
                        CustomAccessibilityAction("Edit") {
                            onEdit()
                            true
                        },
                    )
            },
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Live.Surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
            Text(countdown.title, style = MaterialTheme.typography.titleLarge)
            Text(countdown.dayLabel, style = MaterialTheme.typography.bodyMedium)
            Text(countdown.dateLabel, style = MaterialTheme.typography.bodyMedium)
            CardActions {
                QuietButton("Change date", onClick = onChangeDate)
                QuietButton("Remove", onClick = onRemove)
                QuietButton("Edit", onClick = onEdit)
            }
        }
    }
}

private object CountdownField

/**
 * Event name, then the same day wheel as Bring back.
 * Save stores the centered day. Pick a date stores a calendar day.
 * With no name, the sheet stays open and asks for one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddCountdownSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Long) -> Unit,
    today: LocalDate,
    initialDays: Int = BRING_BACK_DEFAULT_DAYS,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = rememberReducedMotion()
    val draft = remember { TitleDraft() }
    var days by rememberSaveable {
        mutableIntStateOf(initialDays.coerceIn(BRING_BACK_MIN_DAYS, BRING_BACK_MAX_DAYS))
    }
    var committedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var askForName by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }
    val speech = rememberSpeechInput<CountdownField>(recognizer, onSystemSpeech) { draft }
    val title = draft.text.trim()
    val shownDay = committedDay ?: today.plusDays(days.toLong()).toEpochDay()
    val saveDays = (shownDay - today.toEpochDay()).toInt().coerceAtLeast(BRING_BACK_MIN_DAYS)
    val pickDate = remember { MutableInteractionSource() }

    fun save(
        resolved: String,
        day: Long,
    ) {
        if (saved[0]) return
        saved[0] = true
        speech.stop()
        onAdd(resolved, day)
    }

    fun commit(day: Long) {
        committedDay = day
        val resolved = draft.text.trim()
        if (resolved.isEmpty()) {
            askForName = true
            return
        }
        save(resolved, day)
    }

    fun commitShown() {
        commit(shownDay)
    }

    fun dismiss() {
        speech.stop()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Live.Surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = ::dismiss)
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(label = CLOSE_LABEL, onClick = ::dismiss, inSheet = true)
                Text(
                    "Count down to",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            OutlinedTextField(
                value = draft.text,
                onValueChange = { incoming ->
                    if (speech.isListening(CountdownField)) return@OutlinedTextField
                    draft.edit(incoming)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Event") },
                placeholder = { Text("Dana's birthday") },
                singleLine = true,
                trailingIcon = {
                    SpeakMic(
                        listening = speech.isListening(CountdownField),
                        reducedMotion = reducedMotion,
                        contentDescription =
                        if (speech.isListening(CountdownField)) "Stop listening" else "Speak the event",
                        onClick = { speech.toggle(CountdownField) },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitShown() }),
            )
            if (askForName && title.isEmpty()) {
                Text(
                    "Name the event.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.Live.Muted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            speech.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.Live.Muted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.bring_back_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = FocusColors.Live.Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            DaysWheel(
                selectedDays = days,
                maxDays = BRING_BACK_MAX_DAYS,
                onSelectedDays = { next ->
                    if (next != days) {
                        days = next
                        committedDay = null
                    }
                },
            )
            Text(
                text = scheduleMomentLabel(shownDay, null),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Live.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.action_pick_a_date),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Live.Text,
                textAlign = TextAlign.Center,
                textDecoration = TextDecoration.Underline,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = pickDate,
                        indication = null,
                        role = Role.Button,
                        onClick = { picking = true },
                    )
                    .padding(vertical = 8.dp),
            )
            PrimaryButton(
                label = pluralStringResource(R.plurals.bring_back_save_in_days, saveDays, saveDays),
                onClick = ::commitShown,
            )
        }
    }
    if (picking) {
        val tomorrow = today.plusDays(1)
        PickDateDialog(
            initial = committedDay?.let(LocalDate::ofEpochDay) ?: today.plusDays(days.toLong()),
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}

/** A new day for a countdown that already has a name. The wheel matches Bring back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangeCountdownSheet(
    epochDay: Long,
    today: LocalDate,
    onDismiss: () -> Unit,
    onCommit: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val span = (epochDay - today.toEpochDay()).toInt()
    var days by rememberSaveable {
        mutableIntStateOf(span.coerceIn(BRING_BACK_MIN_DAYS, BRING_BACK_MAX_DAYS))
    }
    var wheelMoved by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val sent = remember { booleanArrayOf(false) }
    val tomorrow = today.plusDays(1)
    val current = LocalDate.ofEpochDay(epochDay)
    val pickDate = remember { MutableInteractionSource() }
    val shownDay =
        if (!wheelMoved && span !in BRING_BACK_MIN_DAYS..BRING_BACK_MAX_DAYS) {
            epochDay
        } else {
            today.plusDays(days.toLong()).toEpochDay()
        }
    val saveDays = (shownDay - today.toEpochDay()).toInt().coerceAtLeast(BRING_BACK_MIN_DAYS)

    fun commit(day: Long) {
        if (sent[0]) return
        sent[0] = true
        onCommit(day)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Live.Surface,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(label = CLOSE_LABEL, onClick = onDismiss, inSheet = true)
                Text(
                    "Change the day",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(
                text = stringResource(R.string.bring_back_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = FocusColors.Live.Muted,
                modifier = Modifier.fillMaxWidth(),
            )
            DaysWheel(
                selectedDays = days,
                maxDays = BRING_BACK_MAX_DAYS,
                onSelectedDays = { next ->
                    if (next != days) wheelMoved = true
                    days = next
                },
            )
            Text(
                text = scheduleMomentLabel(shownDay, null),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Live.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.action_pick_a_date),
                style = MaterialTheme.typography.bodyLarge,
                color = FocusColors.Live.Text,
                textAlign = TextAlign.Center,
                textDecoration = TextDecoration.Underline,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = pickDate,
                        indication = null,
                        role = Role.Button,
                        onClick = { picking = true },
                    )
                    .padding(vertical = 8.dp),
            )
            PrimaryButton(
                label = pluralStringResource(R.plurals.bring_back_save_in_days, saveDays, saveDays),
                onClick = { commit(shownDay) },
            )
        }
    }
    if (picking) {
        PickDateDialog(
            initial = if (current.isBefore(tomorrow)) tomorrow else current,
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Countdown rows and the add/change sheets, for Coming up.
 */
@Composable
internal fun CountdownPile(
    countdowns: List<CountdownUi>,
    emptyLabel: String,
    adding: Boolean,
    today: LocalDate,
    initialDays: Int = BRING_BACK_DEFAULT_DAYS,
    onAddingChange: (Boolean) -> Unit,
    onAdd: (String, Long) -> Unit,
    onChangeDate: (String, Long) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    var changingId by rememberSaveable { mutableStateOf<String?>(null) }
    if (countdowns.isEmpty()) {
        Text(emptyLabel, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Live.Muted)
    } else {
        countdowns.forEach { countdown ->
            CountdownRow(
                countdown = countdown,
                onChangeDate = { changingId = countdown.id },
                onRemove = { onRemove(countdown.id) },
                onEdit = { onEdit(countdown.id) },
            )
        }
    }
    val changing = countdowns.firstOrNull { it.id == changingId }
    if (changing != null) {
        ChangeCountdownSheet(
            epochDay = changing.epochDay,
            today = today,
            onDismiss = { changingId = null },
            onCommit = { day ->
                val id = changing.id
                changingId = null
                onChangeDate(id, day)
            },
        )
    }
    if (adding) {
        AddCountdownSheet(
            onDismiss = { onAddingChange(false) },
            onAdd = { title, day ->
                onAddingChange(false)
                onAdd(title, day)
            },
            today = today,
            initialDays = initialDays,
            recognizer = recognizer,
            onSystemSpeech = onSystemSpeech,
        )
    }
}
