package app.backpin.focus.ui.capture

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.ScheduleOffset
import app.backpin.focus.domain.capturePlaces
import app.backpin.focus.domain.scheduleDate
import app.backpin.focus.domain.scheduleMomentLabel
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.calendar.PickTimeDialog
import app.backpin.focus.ui.calendar.suggestedMinuteOfDay
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate
import java.time.LocalTime

private enum class ListenField {
    Title,
    Fill,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CaptureSheet(
    phrases: List<String>,
    frameActive: Boolean,
    onDismiss: () -> Unit,
    onSaveToInbox: (String) -> Unit,
    onInterruptNow: (String) -> Unit,
    onPlaceToday: (String) -> Unit,
    onPlaceStart: (String) -> Unit,
    onPlaceLater: (String) -> Unit,
    onSchedule: (String, Long, Int?) -> Unit,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleDraft = remember { TitleDraft() }
    val fillDraft = remember { TitleDraft() }
    val title = titleDraft.text
    val fillWord = fillDraft.text
    var activeBlank by remember { mutableStateOf<String?>(null) }
    var pendingTitle by remember { mutableStateOf<String?>(null) }
    var schedulingTitle by remember { mutableStateOf<String?>(null) }
    var scheduleFromPhrase by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickedMinute by remember { mutableStateOf<Int?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val fillFocusRequester = remember { FocusRequester() }
    val speech =
        rememberSpeechInput<ListenField>(recognizer, onSystemSpeech) { field ->
            when (field) {
                ListenField.Title -> titleDraft
                ListenField.Fill -> fillDraft
            }
        }
    val today = LocalDate.now()

    fun stopListening() {
        speech.stop()
    }

    fun dismiss() {
        stopListening()
        onDismiss()
    }

    fun commitFill() {
        val template = activeBlank ?: return
        val filled = fillPhraseBlank(template, fillDraft.text) ?: return
        stopListening()
        activeBlank = null
        fillDraft.edit("")
        pendingTitle = filled
    }

    fun clearSchedulePick() {
        pickedDate = null
        pickedMinute = null
        datePickerOpen = false
        timePickerOpen = false
    }

    fun beginSchedule(
        resolved: String,
        fromPhrase: Boolean,
    ) {
        val trimmed = resolved.trim()
        if (trimmed.isEmpty()) return
        scheduleFromPhrase = fromPhrase
        schedulingTitle = trimmed
        pendingTitle = null
        activeBlank = null
        clearSchedulePick()
        stopListening()
    }

    // Read the title at click time, then call the repository path before stopping
    // the mic. Stopping first can dispose the sheet and drop the tap.
    fun placeResolved(
        place: CapturePlace,
        resolved: String,
        fromPhrase: Boolean,
    ) {
        val titleText = resolved.trim()
        if (titleText.isEmpty()) return
        when (place) {
            CapturePlace.INBOX -> onSaveToInbox(titleText)
            CapturePlace.TODAY -> onPlaceToday(titleText)
            CapturePlace.START -> onPlaceStart(titleText)
            CapturePlace.INTERRUPT -> {
                if (!frameActive) return
                onInterruptNow(titleText)
            }
            CapturePlace.LATER -> onPlaceLater(titleText)
            CapturePlace.SCHEDULE -> {
                beginSchedule(titleText, fromPhrase)
                return
            }
        }
        stopListening()
    }

    fun placePhrase(place: CapturePlace) {
        placeResolved(place, pendingTitle ?: return, fromPhrase = true)
    }

    fun placeTyped(place: CapturePlace) {
        // The blank and the destination buttons are on screen together. The words
        // live in the blank; the title field is still empty. Read the blank before
        // giving up, or every destination returns without writing.
        val typed = titleDraft.text.trim()
        val fromBlank = activeBlank?.let { fillPhraseBlank(it, fillDraft.text) }?.trim().orEmpty()
        val resolved = typed.ifEmpty { fromBlank }
        placeResolved(place, resolved, fromPhrase = typed.isEmpty() && fromBlank.isNotEmpty())
    }

    fun chooseMoment(
        date: LocalDate,
        minuteOfDay: Int?,
    ) {
        val resolved = schedulingTitle ?: return
        onSchedule(resolved, date.toEpochDay(), minuteOfDay)
        stopListening()
    }

    fun chooseDate(date: LocalDate) {
        chooseMoment(date, minuteOfDay = null)
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Surface,
        // Navigation bars only. The sheet's own ime padding already lifts it. A
        // second ime inset here would shrink the actions twice.
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        val scheduling = schedulingTitle
        val placing = pendingTitle
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Space),
        ) {
            // One scrolling column, not a fixed stack under the title. A finite-height
            // column measures buttons in order and gives every button after Save to
            // Inbox a zero-height hit target once the keyboard has taken the room.
            // Those taps never reach onClick. Inside the scroll each button keeps
            // its height, so the visible center is a real click.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Get it out of your head.",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = { dismiss() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close capture")
                }
            }
            when {
                scheduling != null -> {
                    ScheduleFields(
                        title = scheduling,
                        pickedDate = pickedDate,
                        pickedMinute = pickedMinute,
                    )
                }
                placing != null -> {
                    Text(placing, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Where does this go?",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                else -> {
                    if (phrases.isNotEmpty()) {
                        WithoutRipple {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                phrases.forEach { phrase ->
                                    SuggestionChip(
                                        onClick = {
                                            stopListening()
                                            when (val action = phraseChipAction(phrase)) {
                                                is PhraseChipAction.AskFill -> {
                                                    activeBlank = phrase
                                                    fillDraft.edit("")
                                                    pendingTitle = null
                                                }
                                                is PhraseChipAction.AskPlace -> {
                                                    activeBlank = null
                                                    fillDraft.edit("")
                                                    pendingTitle = action.title
                                                }
                                            }
                                        },
                                        label = { Text(phraseChipLabel(phrase)) },
                                        modifier =
                                            Modifier
                                                .heightIn(min = 48.dp)
                                                .focusProperties { canFocus = false },
                                        colors =
                                            SuggestionChipDefaults.suggestionChipColors(
                                                containerColor = FocusColors.Surface,
                                                labelColor = FocusColors.Text,
                                            ),
                                        border = BorderStroke(1.dp, FocusColors.Outline),
                                    )
                                }
                            }
                        }
                    }
                    activeBlank?.let { template ->
                        val prompt = phrasePrompt(template) ?: "word"
                        OutlinedTextField(
                            value = fillWord,
                            onValueChange = { incoming ->
                                if (speech.isListening(ListenField.Fill)) return@OutlinedTextField
                                fillDraft.edit(incoming)
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(fillFocusRequester),
                            label = { Text(prompt) },
                            placeholder = { Text(prompt) },
                            singleLine = true,
                            trailingIcon = {
                                SpeakMic(
                                    listening = speech.isListening(ListenField.Fill),
                                    reducedMotion = reducedMotion,
                                    contentDescription =
                                        if (speech.isListening(ListenField.Fill)) {
                                            "Stop listening"
                                        } else {
                                            "Speak the missing word"
                                        },
                                    onClick = { speech.toggle(ListenField.Fill) },
                                )
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitFill() }),
                        )
                        LaunchedEffect(fillFocusRequester) {
                            fillFocusRequester.requestFocus()
                        }
                        fillPhraseBlank(template, fillWord)?.let { preview ->
                            Text(preview, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Text)
                        }
                        TextButton(
                            onClick = { commitFill() },
                            enabled = fillPhraseBlank(template, fillWord) != null,
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .focusProperties { canFocus = false },
                        ) {
                            Text("Next")
                        }
                    }
                    Text(
                        "One line. Choose where it goes. Speaking only fills the title.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (scheduling == null && placing == null) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { incoming ->
                        if (speech.isListening(ListenField.Title)) return@OutlinedTextField
                        titleDraft.edit(incoming)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    label = { Text("Title") },
                    placeholder = { Text("A stray thought") },
                    singleLine = true,
                    trailingIcon = {
                        SpeakMic(
                            listening = speech.isListening(ListenField.Title),
                            reducedMotion = reducedMotion,
                            contentDescription =
                                if (speech.isListening(ListenField.Title)) {
                                    "Stop listening"
                                } else {
                                    "Speak the title"
                                },
                            onClick = { speech.toggle(ListenField.Title) },
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                val resolved = titleDraft.text.trim()
                                if (resolved.isNotEmpty()) onSaveToInbox(resolved)
                                stopListening()
                            },
                        ),
                )
                LaunchedEffect(focusRequester, activeBlank) {
                    if (activeBlank == null) focusRequester.requestFocus()
                }
                speech.note?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
                }
            }
            when {
                scheduling != null -> {
                    ScheduleChoices(
                        today = today,
                        pickedDate = pickedDate,
                        pickedMinute = pickedMinute,
                        onDate = ::chooseDate,
                        onPickDate = { datePickerOpen = true },
                        onAddTime = { timePickerOpen = true },
                        onConfirm = { date -> chooseMoment(date, pickedMinute) },
                        onBack = {
                            if (scheduleFromPhrase) {
                                pendingTitle = scheduling
                            }
                            schedulingTitle = null
                            clearSchedulePick()
                        },
                    )
                }
                placing != null -> {
                    PlaceChoices(
                        places = capturePlaces(frameActive),
                        onPlace = ::placePhrase,
                        onBack = { pendingTitle = null },
                    )
                }
                else -> {
                    PlaceChoices(
                        places = capturePlaces(frameActive),
                        labelFor = { it.typedLabel() },
                        onPlace = ::placeTyped,
                        onBack = null,
                    )
                }
            }
            }
        }
    }
    if (datePickerOpen) {
        PickDateDialog(
            initial = pickedDate ?: today.plusDays(1),
            earliest = today,
            onPick = { date ->
                datePickerOpen = false
                if (date != pickedDate) pickedMinute = null
                pickedDate = date
            },
            onDismiss = { datePickerOpen = false },
        )
    }
    val timeFor = pickedDate
    if (timePickerOpen && timeFor != null) {
        PickTimeDialog(
            initialMinuteOfDay = pickedMinute ?: suggestedMinuteOfDay(timeFor, today, LocalTime.now()),
            onPick = { minute ->
                timePickerOpen = false
                pickedMinute = minute
            },
            onDismiss = { timePickerOpen = false },
        )
    }
}

@Composable
private fun PlaceChoices(
    places: List<CapturePlace>,
    onPlace: (CapturePlace) -> Unit,
    onBack: (() -> Unit)?,
    labelFor: (CapturePlace) -> String = { it.label() },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        places.forEach { place ->
            key(place) {
                CaptureChoice(
                    label = labelFor(place),
                    tone = place.tone(),
                    onClick = { onPlace(place) },
                )
            }
        }
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ScheduleFields(
    title: String,
    pickedDate: LocalDate?,
    pickedMinute: Int?,
) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "When should it come back?",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() },
    )
    if (pickedDate != null) {
        Text(
            "Comes back ${scheduleMomentLabel(pickedDate.toEpochDay(), pickedMinute)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Muted,
        )
    } else {
        Text(
            "Without a time, it lands on Today any time that day.",
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Muted,
        )
    }
}

/**
 * Quick days place the task at once. Pick a date opens the calendar; the picked day
 * can then take an optional time before it is saved.
 */
@Composable
private fun ScheduleChoices(
    today: LocalDate,
    pickedDate: LocalDate?,
    pickedMinute: Int?,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
    onAddTime: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptureChoice("Tomorrow", onClick = { onDate(scheduleDate(today, ScheduleOffset.TOMORROW)) })
        CaptureChoice(
            "In a couple of days",
            onClick = { onDate(scheduleDate(today, ScheduleOffset.IN_A_COUPLE_OF_DAYS)) },
        )
        CaptureChoice("Next week", onClick = { onDate(scheduleDate(today, ScheduleOffset.NEXT_WEEK)) })
        CaptureChoice(if (pickedDate == null) "Pick a date" else "Change date", onClick = onPickDate)
        if (pickedDate != null) {
            CaptureChoice(if (pickedMinute == null) "Add a time" else "Change time", onClick = onAddTime)
            CaptureChoice(
                "Schedule for ${scheduleMomentLabel(pickedDate.toEpochDay(), pickedMinute)}",
                tone = ChoiceTone.Amber,
                onClick = { onConfirm(pickedDate) },
            )
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Back")
        }
    }
}

internal enum class ChoiceTone {
    Mint,
    Lavender,
    Amber,
    Quiet,
}

/**
 * Outlined sheet button. No press scale: a graphics layer that shrinks during the
 * down event cancels the click before onClick. It refuses focus so the keyboard
 * does not resize the sheet between down and up.
 */
@Composable
internal fun CaptureChoice(
    label: String,
    onClick: () -> Unit,
    tone: ChoiceTone = ChoiceTone.Quiet,
    enabled: Boolean = true,
) {
    val accent =
        when (tone) {
            ChoiceTone.Mint -> FocusColors.Mint
            ChoiceTone.Lavender -> FocusColors.Lavender
            ChoiceTone.Amber -> FocusColors.Amber
            ChoiceTone.Quiet -> FocusColors.Text
        }
    val borderColor = if (tone == ChoiceTone.Quiet) FocusColors.Outline else accent
    val shown = if (enabled) 1f else 0.38f
    WithoutRipple {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .focusProperties { canFocus = false },
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, borderColor.copy(alpha = shown)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = accent,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = accent.copy(alpha = 0.38f),
                ),
        ) {
            Text(label)
        }
    }
}

private fun CapturePlace.tone(): ChoiceTone =
    when (this) {
        CapturePlace.INBOX -> ChoiceTone.Mint
        CapturePlace.INTERRUPT -> ChoiceTone.Lavender
        CapturePlace.SCHEDULE -> ChoiceTone.Amber
        CapturePlace.TODAY,
        CapturePlace.START,
        CapturePlace.LATER,
        -> ChoiceTone.Quiet
    }

private fun CapturePlace.label(): String =
    when (this) {
        CapturePlace.INBOX -> "Inbox"
        CapturePlace.TODAY -> "Today"
        CapturePlace.START -> "Start"
        CapturePlace.INTERRUPT -> "Interrupt"
        CapturePlace.LATER -> "Later"
        CapturePlace.SCHEDULE -> "Schedule"
    }

private fun CapturePlace.typedLabel(): String =
    when (this) {
        CapturePlace.INBOX -> "Save to Inbox"
        CapturePlace.INTERRUPT -> "Interrupt now"
        else -> label()
    }

@Composable
internal fun SpeakMic(
    listening: Boolean,
    reducedMotion: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (listening && !reducedMotion) 1.06f else 1f,
        animationSpec = motionSpec(MotionSpec.PressMillis, reducedMotion || !listening),
        label = "micListen",
    )
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(48.dp)
                .focusProperties { canFocus = false }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = contentDescription,
            tint = if (listening) FocusColors.Mint else FocusColors.Muted,
        )
    }
}
