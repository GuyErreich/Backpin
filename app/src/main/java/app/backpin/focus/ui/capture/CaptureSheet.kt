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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import app.backpin.focus.ui.home.MintButton
import app.backpin.focus.ui.home.QuietButton
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PERMISSION_NOTE = "Microphone permission is off. You can still type."
private const val UNAVAILABLE_NOTE = "Speech isn't available on this phone. You can still type."

private val scheduleDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

private enum class ListenField {
    Title,
    Fill,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(
    phrases: List<String>,
    frameActive: Boolean,
    onDismiss: () -> Unit,
    onSaveToInbox: (String) -> Unit,
    onInterruptNow: (String) -> Unit,
    onPlaceToday: (String) -> Unit,
    onPlaceStart: (String) -> Unit,
    onPlaceLater: (String) -> Unit,
    onSchedule: (String, Long) -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = rememberReducedMotion()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var activeBlank by remember { mutableStateOf<String?>(null) }
    var fillWord by remember { mutableStateOf("") }
    var pendingTitle by remember { mutableStateOf<String?>(null) }
    var schedulingTitle by remember { mutableStateOf<String?>(null) }
    var scheduleFromPhrase by remember { mutableStateOf(false) }
    var dayText by remember { mutableStateOf("") }
    var speechNote by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var listenField by remember { mutableStateOf<ListenField?>(null) }
    var utteranceBase by remember { mutableStateOf("") }
    var pendingListen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingListenState = rememberUpdatedState(pendingListen)
    val focusRequester = remember { FocusRequester() }
    val fillFocusRequester = remember { FocusRequester() }
    val recognizer = remember(context) { TitleSpeechRecognizer(context) }
    val today = LocalDate.now()

    fun applySpoken(
        field: ListenField,
        spoken: String,
    ) {
        if (spoken.isBlank()) return
        val next = appendSpokenText(utteranceBase, spoken)
        when (field) {
            ListenField.Title -> title = next
            ListenField.Fill -> fillWord = next
        }
    }

    fun startUtterance(field: ListenField) {
        utteranceBase = if (field == ListenField.Title) title else fillWord
        listenField = field
        speechNote = null
        listening = true
        val started =
            recognizer.listen(
                onPartial = { spoken -> applySpoken(field, spoken) },
                onFinished = { spoken -> applySpoken(field, spoken) },
                onStop = { stop ->
                    listening = false
                    if (stop == SpeechStop.Permission) speechNote = PERMISSION_NOTE
                },
            )
        if (!started) {
            listening = false
            speechNote = UNAVAILABLE_NOTE
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val start = pendingListenState.value
            pendingListen = null
            if (granted) {
                start?.invoke()
            } else {
                speechNote = PERMISSION_NOTE
            }
        }

    fun stopListening() {
        recognizer.cancel()
        listening = false
    }

    fun dismiss() {
        stopListening()
        onDismiss()
    }

    fun commitFill() {
        val template = activeBlank ?: return
        val filled = fillPhraseBlank(template, fillWord) ?: return
        stopListening()
        activeBlank = null
        fillWord = ""
        pendingTitle = filled
    }

    fun beginSchedule(
        resolved: String,
        fromPhrase: Boolean,
    ) {
        if (resolved.isBlank()) return
        stopListening()
        scheduleFromPhrase = fromPhrase
        schedulingTitle = resolved.trim()
        pendingTitle = null
        activeBlank = null
        dayText = ""
    }

    fun placePhrase(place: CapturePlace) {
        val resolved = pendingTitle ?: return
        when (place) {
            CapturePlace.INBOX -> {
                stopListening()
                onSaveToInbox(resolved)
            }
            CapturePlace.TODAY -> {
                stopListening()
                onPlaceToday(resolved)
            }
            CapturePlace.START -> {
                stopListening()
                onPlaceStart(resolved)
            }
            CapturePlace.INTERRUPT -> {
                stopListening()
                onInterruptNow(resolved)
            }
            CapturePlace.LATER -> {
                stopListening()
                onPlaceLater(resolved)
            }
            CapturePlace.SCHEDULE -> beginSchedule(resolved, fromPhrase = true)
        }
    }

    fun chooseDate(date: LocalDate) {
        val resolved = schedulingTitle ?: return
        stopListening()
        onSchedule(resolved, date.toEpochDay())
    }

    fun onMic(field: ListenField) {
        if (listening && listenField == field) {
            stopListening()
            return
        }
        if (!recognizer.isAvailable()) {
            speechNote = UNAVAILABLE_NOTE
            return
        }
        val start = { startUtterance(field) }
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            start()
        } else {
            pendingListen = start
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.cancel() }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
        sheetState = sheetState,
        containerColor = FocusColors.Surface,
    ) {
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
            val scheduling = schedulingTitle
            val placing = pendingTitle
            when {
                scheduling != null -> {
                    ScheduleStep(
                        title = scheduling,
                        today = today,
                        dayText = dayText,
                        onDayText = { dayText = it.filter { char -> char.isDigit() }.take(2) },
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
                    PlaceStep(
                        title = placing,
                        places = capturePlaces(frameActive),
                        onPlace = ::placePhrase,
                        onBack = { pendingTitle = null },
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
                                                    fillWord = ""
                                                    pendingTitle = null
                                                }
                                                is PhraseChipAction.AskPlace -> {
                                                    activeBlank = null
                                                    fillWord = ""
                                                    pendingTitle = action.title
                                                }
                                            }
                                        },
                                        label = { Text(phraseChipLabel(phrase)) },
                                        modifier = Modifier.heightIn(min = 48.dp),
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
                            onValueChange = { fillWord = it },
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
                        fillPhraseBlank(template, fillWord)?.let { preview ->
                            Text(preview, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Text)
                        }
                        TextButton(
                            onClick = { commitFill() },
                            enabled = fillPhraseBlank(template, fillWord) != null,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Next")
                        }
                    }
                    Text(
                        "One line. Choose where it goes. Speaking only fills the title.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
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
                                    stopListening()
                                    onSaveToInbox(title)
                                },
                            ),
                    )
                    speechNote?.let { note ->
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MintButton(
                            "Save to Inbox",
                            onClick = {
                                stopListening()
                                onSaveToInbox(title)
                            },
                        )
                        QuietButton("Today", onClick = {
                            stopListening()
                            onPlaceToday(title)
                        })
                        QuietButton("Start", onClick = {
                            stopListening()
                            onPlaceStart(title)
                        })
                        QuietButton(
                            "Interrupt now",
                            onClick = {
                                stopListening()
                                onInterruptNow(title)
                            },
                        )
                        QuietButton("Later", onClick = {
                            stopListening()
                            onPlaceLater(title)
                        })
                        QuietButton(
                            "Schedule",
                            onClick = { beginSchedule(title, fromPhrase = false) },
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(activeBlank) {
        if (activeBlank != null) fillFocusRequester.requestFocus()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceStep(
    title: String,
    places: List<CapturePlace>,
    onPlace: (CapturePlace) -> Unit,
    onBack: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "Where does this go?",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        places.forEach { place ->
            QuietButton(place.label(), onClick = { onPlace(place) })
        }
    }
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
        Text("Back")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleStep(
    title: String,
    today: LocalDate,
    dayText: String,
    onDayText: (String) -> Unit,
    onDate: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val day = dayText.toIntOrNull()
    val picked = if (day != null && day in 1..31) nextDayOfMonth(today, day) else null
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "When should it come back?",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietButton("Tomorrow", onClick = { onDate(scheduleDate(today, ScheduleOffset.TOMORROW)) })
        QuietButton(
            "In a couple of days",
            onClick = { onDate(scheduleDate(today, ScheduleOffset.IN_A_COUPLE_OF_DAYS)) },
        )
        QuietButton("Next week", onClick = { onDate(scheduleDate(today, ScheduleOffset.NEXT_WEEK)) })
    }
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
    QuietButton(
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

private fun CapturePlace.label(): String =
    when (this) {
        CapturePlace.INBOX -> "Inbox"
        CapturePlace.TODAY -> "Today"
        CapturePlace.START -> "Start"
        CapturePlace.INTERRUPT -> "Interrupt"
        CapturePlace.LATER -> "Later"
        CapturePlace.SCHEDULE -> "Schedule"
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
