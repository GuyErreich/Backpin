package app.backpin.focus.ui.countdown

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.CountdownUi
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.capture.SpeakMic
import app.backpin.focus.ui.capture.TitleDraft
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.capture.rememberSpeechInput
import app.backpin.focus.ui.components.AccentButton
import app.backpin.focus.ui.components.AccentTone
import app.backpin.focus.ui.components.CardActions
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CaptureChoice
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
    onAdd: (String, Long) -> Unit,
    onChangeDate: (String, Long) -> Unit,
    onRemove: (String) -> Unit,
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
                color = FocusColors.Amber,
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
                    color = FocusColors.Muted,
                )
            else ->
                countdowns.forEach { countdown ->
                    CountdownRow(
                        countdown = countdown,
                        onChangeDate = { changingId = countdown.id },
                        onRemove = { onRemove(countdown.id) },
                    )
                }
        }
    }
    val changing = countdowns.firstOrNull { it.id == changingId }
    if (changing != null) {
        ChangeCountdownSheet(
            epochDay = changing.epochDay,
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
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) {
                    contentDescription = "Countdown. ${countdown.title}. ${countdown.dayLabel}, ${countdown.dateLabel}."
                },
        shape = RoundedCornerShape(CardRadius),
        color = FocusColors.Surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Space)) {
            Text(countdown.title, style = MaterialTheme.typography.titleLarge)
            Text(countdown.dayLabel, style = MaterialTheme.typography.bodyMedium)
            Text(countdown.dateLabel, style = MaterialTheme.typography.bodyMedium)
            CardActions {
                QuietButton("Change date", onClick = onChangeDate)
                QuietButton("Remove", onClick = onRemove)
            }
        }
    }
}

private object CountdownField

private const val MIN_COUNTDOWN_DAYS: Int = 1
private const val MAX_COUNTDOWN_DAYS: Int = 999

/** Digits only, 0 through 999. A leading zero collapses so 25 stays 25. */
internal fun sanitizeCountdownDays(incoming: String): String {
    val digits = incoming.filter(Char::isDigit).take(3)
    if (digits.isEmpty()) return ""
    val value = digits.toIntOrNull() ?: return ""
    return value.coerceIn(0, MAX_COUNTDOWN_DAYS).toString()
}

private fun countdownDays(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in MIN_COUNTDOWN_DAYS..MAX_COUNTDOWN_DAYS }

/**
 * Title with the mic, then either a number of days or a calendar day.
 * That choice saves and closes the sheet when the event has a name.
 * With no name, the choice stays and the event saves as soon as it is named.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCountdownSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Long) -> Unit,
    recognizer: UtteranceRecognizer?,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = rememberReducedMotion()
    val draft = remember { TitleDraft() }
    var daysText by rememberSaveable { mutableStateOf("") }
    var committedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var askForName by remember { mutableStateOf(false) }
    val saved = remember { booleanArrayOf(false) }
    val speech = rememberSpeechInput<CountdownField>(recognizer, onSystemSpeech) { draft }
    val today = LocalDate.now()
    val title = draft.text.trim()
    val typedDay = countdownDays(daysText)?.let { today.plusDays(it.toLong()).toEpochDay() }
    val shownDay = typedDay ?: committedDay

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

    SideEffect {
        val pending = committedDay
        val resolved = draft.text.trim()
        if (!saved[0] && pending != null && resolved.isNotEmpty()) save(resolved, pending)
    }

    fun commitTypedDays() {
        val days = countdownDays(daysText) ?: return
        commit(today.plusDays(days.toLong()).toEpochDay())
    }

    fun dismiss() {
        speech.stop()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Surface,
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
            )
            if (askForName && title.isEmpty()) {
                Text("Name the event.", style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
            }
            speech.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
            }
            CountdownDayEntry(
                daysText = daysText,
                onDaysChange = { daysText = sanitizeCountdownDays(it) },
                shownDay = shownDay,
                onDone = { commitTypedDays() },
                onPickDate = { picking = true },
            )
        }
    }
    if (picking) {
        val tomorrow = today.plusDays(1)
        PickDateDialog(
            initial = committedDay?.let(LocalDate::ofEpochDay) ?: tomorrow.plusDays(6),
            earliest = tomorrow,
            onPick = { date ->
                picking = false
                daysText = ""
                commit(date.toEpochDay())
            },
            onDismiss = { picking = false },
        )
    }
}

/** A new number of days, or a calendar day, for a countdown that already has a name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeCountdownSheet(
    epochDay: Long,
    onDismiss: () -> Unit,
    onCommit: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var daysText by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val sent = remember { booleanArrayOf(false) }
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val current = LocalDate.ofEpochDay(epochDay)
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
                    "Change the day",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
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

/** Number of days after today. Done on the keyboard is the choice. Pick a date opens the calendar. */
@Composable
private fun CountdownDayEntry(
    daysText: String,
    onDaysChange: (String) -> Unit,
    shownDay: Long?,
    onDone: () -> Unit,
    onPickDate: () -> Unit,
) {
    OutlinedTextField(
        value = daysText,
        onValueChange = onDaysChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Days from today") },
        placeholder = { Text("25") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
    if (shownDay != null) {
        Text(scheduleMomentLabel(shownDay, null), style = MaterialTheme.typography.bodyLarge)
    }
    CaptureChoice("Pick a date", onClick = onPickDate)
}
