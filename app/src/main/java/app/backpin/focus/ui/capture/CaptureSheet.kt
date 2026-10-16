package app.backpin.focus.ui.capture

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import app.backpin.focus.domain.editPlaces
import app.backpin.focus.domain.scheduleDate
import app.backpin.focus.ui.calendar.PickDateDialog
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate

private enum class ListenField {
    Title,
    Fill,
}

/** Prefill for the same sheet in edit mode. [place] is null for a nested interruption. */
internal data class CaptureEdit(
    val title: String,
    val place: CapturePlace?,
    val scheduledEpochDay: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    edit: CaptureEdit? = null,
    onCommitEdit: ((String, CapturePlace?, Long?) -> Unit)? = null,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleDraft =
        remember(edit?.title) {
            TitleDraft().apply { if (edit != null) edit(edit.title) }
        }
    val fillDraft = remember { TitleDraft() }
    val title = titleDraft.text
    val fillWord = fillDraft.text
    var activeBlank by remember { mutableStateOf<String?>(null) }
    var pendingTitle by remember { mutableStateOf<String?>(null) }
    var schedulingTitle by remember { mutableStateOf<String?>(null) }
    var scheduleFromPhrase by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var selectedPlace by remember(edit) { mutableStateOf(edit?.place) }
    var selectedDay by remember(edit) { mutableStateOf(edit?.scheduledEpochDay) }
    var editCommitted by remember { mutableStateOf(false) }
    val editing = edit != null
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

    fun commitEditAndClose() {
        val current = edit ?: return
        if (editCommitted) return
        editCommitted = true
        stopListening()
        val resolved = titleDraft.text.trim().ifEmpty { current.title }
        val place = selectedPlace ?: current.place
        val day = if (place == CapturePlace.SCHEDULE) selectedDay ?: current.scheduledEpochDay else null
        onCommitEdit?.invoke(resolved, place, day)
        onDismiss()
    }

    fun leaveSheet() {
        if (editing) commitEditAndClose() else dismiss()
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
        datePickerOpen = false
    }

    fun beginSchedule(
        resolved: String,
        fromPhrase: Boolean,
    ) {
        val trimmed = resolved.trim().ifEmpty { edit?.title.orEmpty() }
        if (trimmed.isEmpty()) return
        scheduleFromPhrase = fromPhrase
        schedulingTitle = trimmed
        pendingTitle = null
        activeBlank = null
        if (editing) selectedPlace = CapturePlace.SCHEDULE
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
        if (editing) {
            if (place == CapturePlace.SCHEDULE) {
                beginSchedule(titleDraft.text, fromPhrase = false)
                return
            }
            selectedPlace = place
            return
        }
        // The blank and the destination buttons are on screen together. The words
        // live in the blank; the title field is still empty. Read the blank before
        // giving up, or every destination returns without writing.
        val typed = titleDraft.text.trim()
        val fromBlank = activeBlank?.let { fillPhraseBlank(it, fillDraft.text) }?.trim().orEmpty()
        val resolved = typed.ifEmpty { fromBlank }
        placeResolved(place, resolved, fromPhrase = typed.isEmpty() && fromBlank.isNotEmpty())
    }

    fun chooseDate(date: LocalDate) {
        val resolved = schedulingTitle ?: return
        if (editing) {
            selectedPlace = CapturePlace.SCHEDULE
            selectedDay = date.toEpochDay()
            commitEditAndClose()
            return
        }
        onSchedule(resolved, date.toEpochDay(), null)
        dismiss()
    }

    val atFirstStep = schedulingTitle == null && pendingTitle == null && activeBlank == null

    // One step back: When → Where (for a phrase) or the title, Where → the title,
    // the phrase blank → the title. From the title it closes the sheet.
    fun stepBack() {
        val scheduled = schedulingTitle
        when {
            scheduled != null -> {
                if (scheduleFromPhrase) pendingTitle = scheduled
                schedulingTitle = null
                clearSchedulePick()
            }
            pendingTitle != null -> pendingTitle = null
            activeBlank != null -> {
                stopListening()
                activeBlank = null
                fillDraft.edit("")
            }
            else -> leaveSheet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { leaveSheet() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Surface,
        // Navigation bars only. The sheet's own ime padding already lifts it. A
        // second ime inset here would shrink the actions twice.
        contentWindowInsets = { WindowInsets.navigationBars },
        // System back is handled below, so it steps back exactly like the arrow.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = ::stepBack)
        val scheduling = schedulingTitle
        val placing = pendingTitle
        val sheetGap = if (editing) ButtonGap else Space
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (editing) Modifier else Modifier.fillMaxHeight())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(sheetGap),
        ) {
            // One scrolling column, not a fixed stack under the title. A finite-height
            // column measures buttons in order and gives every button after Save to
            // Inbox a zero-height hit target once the keyboard has taken the room.
            // Those taps never reach onClick. Inside the scroll each button keeps
            // its height, so the visible center is a real click.
            // Edit wraps the sheet to its fields, like the Figma frame, instead of
            // stretching to the full window.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (editing) {
                                Modifier
                            } else {
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            },
                        ),
                verticalArrangement = Arrangement.spacedBy(sheetGap),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackArrow(
                    label = if (atFirstStep) CLOSE_LABEL else BACK_LABEL,
                    onClick = ::stepBack,
                    inSheet = true,
                )
                Text(
                    if (editing) "Edit task" else "Get it out of your head.",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            when {
                scheduling != null -> {
                    ScheduleFields(title = scheduling)
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
                    if (!editing && phrases.isNotEmpty()) {
                        ActionRow {
                            phrases.forEach { phrase ->
                                key(phrase) {
                                    CaptureChoice(
                                        label = phraseChipLabel(phrase),
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
                        CaptureChoice(
                            label = "Next",
                            onClick = { commitFill() },
                            enabled = fillPhraseBlank(template, fillWord) != null,
                        )
                    }
                    if (!editing) {
                        Text(
                            "One line. Choose where it goes. Speaking only fills the title.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FocusColors.Text,
                        )
                    }
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
                    shape = RoundedCornerShape(ButtonRadius),
                    colors = titleFieldColors(),
                    label = { Text(if (editing) "Task" else "Title") },
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
                                if (editing) {
                                    commitEditAndClose()
                                } else {
                                    val resolved = titleDraft.text.trim()
                                    if (resolved.isNotEmpty()) onSaveToInbox(resolved)
                                    stopListening()
                                }
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
                        onDate = ::chooseDate,
                        onPickDate = { datePickerOpen = true },
                    )
                }
                placing != null -> {
                    PlaceChoices(
                        places = capturePlaces(frameActive),
                        onPlace = ::placePhrase,
                    )
                }
                editing -> {
                    PlaceChoices(
                        places = editPlaces(),
                        labelFor = { it.label() },
                        selected = selectedPlace,
                        onPlace = ::placeTyped,
                    )
                    CaptureChoice(
                        label = "Done",
                        tone = ChoiceTone.Mint,
                        onClick = ::commitEditAndClose,
                    )
                }
                else -> {
                    PlaceChoices(
                        places = capturePlaces(frameActive),
                        labelFor = { it.typedLabel() },
                        onPlace = ::placeTyped,
                    )
                }
            }
            }
        }
    }
    if (datePickerOpen) {
        PickDateDialog(
            initial = today.plusDays(1),
            earliest = today,
            onPick = { date ->
                datePickerOpen = false
                chooseDate(date)
            },
            onDismiss = { datePickerOpen = false },
        )
    }
}

@Composable
private fun PlaceChoices(
    places: List<CapturePlace>,
    onPlace: (CapturePlace) -> Unit,
    labelFor: (CapturePlace) -> String = { it.label() },
    selected: CapturePlace? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
        places.forEach { place ->
            key(place) {
                CaptureChoice(
                    label = labelFor(place),
                    tone = place.tone(),
                    selected = place == selected,
                    onClick = { onPlace(place) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleFields(title: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "When should it come back?",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Without a time, it lands on Today any time that day.",
        style = MaterialTheme.typography.bodyMedium,
        color = FocusColors.Muted,
    )
}

/**
 * Tomorrow, in 7 days, or the day chosen on the calendar schedules at once, with no time,
 * and closes the sheet. The time is set later on the Scheduled row.
 */
@Composable
private fun ScheduleChoices(
    today: LocalDate,
    onDate: (LocalDate) -> Unit,
    onPickDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
        CaptureChoice("Tomorrow", onClick = { onDate(scheduleDate(today, ScheduleOffset.TOMORROW)) })
        CaptureChoice("In 7 days", onClick = { onDate(scheduleDate(today, ScheduleOffset.IN_7_DAYS)) })
        CaptureChoice("Pick a date", onClick = onPickDate)
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
private fun titleFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = FocusColors.Text,
        unfocusedTextColor = FocusColors.Text,
        cursorColor = FocusColors.Mint,
        focusedBorderColor = FocusColors.Outline,
        unfocusedBorderColor = FocusColors.Outline,
        focusedLabelColor = FocusColors.Muted,
        unfocusedLabelColor = FocusColors.Muted,
        focusedPlaceholderColor = FocusColors.Muted,
        unfocusedPlaceholderColor = FocusColors.Muted,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

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
