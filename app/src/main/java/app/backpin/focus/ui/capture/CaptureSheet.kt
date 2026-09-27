package app.backpin.focus.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.backpin.focus.R
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.ScheduleOffset
import app.backpin.focus.domain.capturePlaces
import app.backpin.focus.domain.nextDayOfMonth
import app.backpin.focus.domain.scheduleDate
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val scheduleDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

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
    onSchedule: (String, Long) -> Unit,
    recognizer: UtteranceRecognizer? = null,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
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
    var dayText by remember { mutableStateOf("") }
    var speechNote by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var listenField by remember { mutableStateOf<ListenField?>(null) }
    var pendingListen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingListenState = rememberUpdatedState(pendingListen)
    val alive = remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val fillFocusRequester = remember { FocusRequester() }
    val speech = remember(context, recognizer) { recognizer ?: TitleSpeechRecognizer(context) }
    val today = LocalDate.now()

    fun hear(
        field: ListenField,
        spoken: String,
    ) {
        when (field) {
            ListenField.Title -> titleDraft.hear(spoken)
            ListenField.Fill -> fillDraft.hear(spoken)
        }
    }

    fun startUtterance(field: ListenField) {
        when (field) {
            ListenField.Title -> titleDraft.beginUtterance()
            ListenField.Fill -> fillDraft.beginUtterance()
        }
        focusManager.clearFocus(force = true)
        listenField = field
        speechNote = null
        listening = true
        val started =
            speech.listen(
                onPartial = { spoken -> hear(field, spoken) },
                onFinished = { spoken ->
                    hear(field, spoken)
                    listening = false
                },
                onProblem = { problem ->
                    listening = false
                    speechNote = speechProblemNote(problem)
                },
            )
        if (!started) {
            listening = false
            speechNote = speechProblemNote(SpeechProblem.Unavailable)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!alive.value) return@rememberLauncherForActivityResult
            val start = pendingListenState.value
            pendingListen = null
            if (granted) {
                start?.invoke()
            } else {
                speechNote = speechProblemNote(SpeechProblem.Denied)
            }
        }

    fun stopListening() {
        speech.cancel()
        listening = false
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
        dayText = ""
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

    fun chooseDate(date: LocalDate) {
        val resolved = schedulingTitle ?: return
        onSchedule(resolved, date.toEpochDay())
        stopListening()
    }

    fun onMic(field: ListenField) {
        if (listening && listenField == field) {
            stopListening()
            return
        }
        if (!speech.available()) {
            speechNote = speechProblemNote(SpeechProblem.Unavailable)
            return
        }
        val start = { startUtterance(field) }
        val granted =
            ContextCompat.checkSelfPermission(
                context.hostContext(),
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            start()
        } else {
            pendingListen = start
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (_: IllegalStateException) {
                pendingListen = null
                speechNote = speechProblemNote(SpeechProblem.Denied)
            }
        }
    }

    DisposableEffect(speech) {
        onDispose {
            alive.value = false
            speech.cancel()
        }
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
                        today = today,
                        dayText = dayText,
                        onDayText = { dayText = it.filter { char -> char.isDigit() }.take(2) },
                        onDate = ::chooseDate,
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
                                if (listening && listenField == ListenField.Fill) return@OutlinedTextField
                                fillDraft.edit(incoming)
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(fillFocusRequester),
                            label = { Text(prompt) },
                            placeholder = { Text(prompt) },
                            singleLine = true,
                            trailingIcon = {
                                SpeakMic(
                                    listening = listening && listenField == ListenField.Fill,
                                    reducedMotion = reducedMotion,
                                    contentDescription =
                                        if (listening && listenField == ListenField.Fill) {
                                            "Stop listening"
                                        } else {
                                            "Speak the missing word"
                                        },
                                    onClick = { onMic(ListenField.Fill) },
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
                        if (listening && listenField == ListenField.Title) return@OutlinedTextField
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
                            listening = listening && listenField == ListenField.Title,
                            reducedMotion = reducedMotion,
                            contentDescription =
                                if (listening && listenField == ListenField.Title) {
                                    "Stop listening"
                                } else {
                                    "Speak the title"
                                },
                            onClick = { onMic(ListenField.Title) },
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
                speechNote?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
                }
            }
            when {
                scheduling != null -> {
                    ScheduleChoices(
                        today = today,
                        dayText = dayText,
                        onDate = ::chooseDate,
                        onBack = {
                            if (scheduleFromPhrase) {
                                pendingTitle = scheduling
                            }
                            schedulingTitle = null
                            dayText = ""
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
    today: LocalDate,
    dayText: String,
    onDayText: (String) -> Unit,
    onDate: (LocalDate) -> Unit,
) {
    val picked = pickedDay(today, dayText)
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "When should it come back?",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() },
    )
    OutlinedTextField(
        value = dayText,
        onValueChange = onDayText,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Day of the month") },
        placeholder = { Text("1–31") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    if (picked != null) onDate(picked)
                },
            ),
    )
    picked?.let { date ->
        Text(
            "Comes back ${date.format(scheduleDayFormat)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Muted,
        )
    }
}

@Composable
private fun ScheduleChoices(
    today: LocalDate,
    dayText: String,
    onDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val picked = pickedDay(today, dayText)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptureChoice("Tomorrow", onClick = { onDate(scheduleDate(today, ScheduleOffset.TOMORROW)) })
        CaptureChoice(
            "In a couple of days",
            onClick = { onDate(scheduleDate(today, ScheduleOffset.IN_A_COUPLE_OF_DAYS)) },
        )
        CaptureChoice("Next week", onClick = { onDate(scheduleDate(today, ScheduleOffset.NEXT_WEEK)) })
        CaptureChoice(
            "That day",
            onClick = {
                if (picked != null) onDate(picked)
            },
            enabled = picked != null,
        )
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Back")
        }
    }
}

private fun pickedDay(
    today: LocalDate,
    dayText: String,
): LocalDate? {
    val day = dayText.toIntOrNull()
    return if (day != null && day in 1..31) nextDayOfMonth(today, day) else null
}

private enum class ChoiceTone {
    Mint,
    Lavender,
    Amber,
    Quiet,
}

@Composable
private fun CaptureChoice(
    label: String,
    onClick: () -> Unit,
    tone: ChoiceTone = ChoiceTone.Quiet,
    enabled: Boolean = true,
) {
    val container: Color
    val content: Color
    when (tone) {
        ChoiceTone.Mint -> {
            container = FocusColors.Mint
            content = FocusColors.OnMint
        }
        ChoiceTone.Lavender -> {
            container = FocusColors.Lavender
            content = FocusColors.OnLavender
        }
        ChoiceTone.Amber -> {
            container = FocusColors.Amber
            content = FocusColors.OnAmber
        }
        ChoiceTone.Quiet -> {
            container = FocusColors.SurfaceHigh
            content = FocusColors.Text
        }
    }
    // No press scale: a graphics layer that shrinks during the down event
    // cancels the click before onClick. These buttons also refuse focus so
    // the keyboard does not resize the sheet between down and up.
    WithoutRipple {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .focusProperties { canFocus = false },
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = content,
                    disabledContainerColor = container.copy(alpha = 0.38f),
                    disabledContentColor = content.copy(alpha = 0.38f),
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
private fun SpeakMic(
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
