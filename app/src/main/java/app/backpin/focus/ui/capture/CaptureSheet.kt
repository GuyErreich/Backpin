// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.editPlaces
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.PrimaryButton
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.ButtonLabel
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private enum class ListenField {
    Title,
    Fill,
}

private val PhraseChipMinHeight = 40.dp

/** Prefill for the same sheet in edit mode. [place] is null for a nested interruption. */
internal data class CaptureEdit(
    val title: String,
    val place: CapturePlace?,
    val scheduledEpochDay: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:indent")
internal fun CaptureSheet(
    phrases: List<String>,
    onDismiss: () -> Unit,
    onSaveToInbox: (String) -> Unit = {},
    onInterruptNow: (String) -> Unit = {},
    onPlaceToday: (String) -> Unit = {},
    onPlaceStart: (String) -> Unit = {},
    edit: CaptureEdit? = null,
    onCommitEdit: ((String, CapturePlace?, Long?) -> Unit)? = null,
    onBringBack: ((String) -> Unit)? = null,
    interrupts: Boolean = false,
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
    var selectedPlace by remember(edit) { mutableStateOf(edit?.place) }
    var editCommitted by remember { mutableStateOf(false) }
    val editing = edit != null
    val interruptNow = interrupts && !editing
    val focusRequester = remember { FocusRequester() }
    val fillFocusRequester = remember { FocusRequester() }
    val speech =
        rememberSpeechInput<ListenField>(recognizer, onSystemSpeech) { field ->
            when (field) {
                ListenField.Title -> titleDraft
                ListenField.Fill -> fillDraft
            }
        }

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
        val day = if (place == CapturePlace.SCHEDULE) current.scheduledEpochDay else null
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
        titleDraft.edit(filled)
    }

    fun typedTitle(): String {
        val typed = titleDraft.text.trim()
        val fromBlank = activeBlank?.let { fillPhraseBlank(it, fillDraft.text) }?.trim().orEmpty()
        return typed.ifEmpty { fromBlank }
    }

    fun runWithTitle(place: (String) -> Unit) {
        val titleText = typedTitle()
        if (titleText.isEmpty()) return
        place(titleText)
        stopListening()
    }

    fun runPrimary() {
        if (interruptNow) {
            runWithTitle(onInterruptNow)
        } else {
            runWithTitle(onSaveToInbox)
        }
    }

    fun placeTyped(place: CapturePlace) {
        if (!editing) return
        if (place == CapturePlace.SCHEDULE) {
            val titleText = titleDraft.text.trim().ifEmpty { edit.title }
            val openBringBack = onBringBack
            if (openBringBack != null) {
                openBringBack(titleText)
                return
            }
            selectedPlace = place
            return
        }
        selectedPlace = place
    }

    val atFirstStep = activeBlank == null

    fun stepBack() {
        if (activeBlank != null) {
            stopListening()
            activeBlank = null
            fillDraft.edit("")
        } else {
            leaveSheet()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { leaveSheet() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Live.Surface,
        // Navigation bars only. The sheet's own ime padding already lifts it. A
        // second ime inset here would shrink the actions twice.
        contentWindowInsets = { WindowInsets.navigationBars },
        // System back is handled below, so it steps back exactly like the arrow.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = ::stepBack)
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
            if (!editing && phrases.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space),
                    verticalArrangement = Arrangement.spacedBy(Space),
                ) {
                    phrases.forEach { phrase ->
                        key(phrase) {
                            PhraseChip(
                                label = phraseChipLabel(phrase),
                                onClick = {
                                    stopListening()
                                    when (val action = phraseChipAction(phrase)) {
                                        is PhraseChipAction.AskFill -> {
                                            activeBlank = phrase
                                            fillDraft.edit("")
                                        }
                                        is PhraseChipAction.AskPlace -> {
                                            activeBlank = null
                                            fillDraft.edit("")
                                            titleDraft.edit(action.title)
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
                    Text(preview, style = MaterialTheme.typography.bodyLarge, color = FocusColors.Live.Text)
                }
                CaptureChoice(
                    label = "Next",
                    onClick = { commitFill() },
                    enabled = fillPhraseBlank(template, fillWord) != null,
                )
            }
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
                                runPrimary()
                            }
                        },
                    ),
            )
            LaunchedEffect(focusRequester, activeBlank) {
                if (activeBlank == null) focusRequester.requestFocus()
            }
            speech.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Live.Muted)
            }
            if (editing) {
                val bringBackLabel = stringResource(R.string.action_bring_back)
                PlaceChoices(
                    places = editPlaces().filter { it != CapturePlace.LATER },
                    labelFor = { place ->
                        if (place == CapturePlace.SCHEDULE) bringBackLabel else place.label()
                    },
                    selected = selectedPlace,
                    onPlace = ::placeTyped,
                )
                PrimaryButton(
                    label = stringResource(R.string.action_save),
                    onClick = ::commitEditAndClose,
                )
            } else {
                if (interruptNow) {
                    CaptureChoice(
                        label = stringResource(R.string.action_interrupt_now),
                        onClick = { runWithTitle(onInterruptNow) },
                        tone = ChoiceTone.Lavender,
                        filled = true,
                    )
                } else {
                    PrimaryButton(
                        label = stringResource(R.string.action_save_to_inbox),
                        onClick = { runWithTitle(onSaveToInbox) },
                    )
                }
                Text(
                    text = stringResource(R.string.capture_or_when),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.Live.Muted,
                )
                ActionRow {
                    CaptureChoice(
                        label = stringResource(R.string.action_today),
                        onClick = { runWithTitle(onPlaceToday) },
                    )
                    CaptureChoice(
                        label = stringResource(R.string.action_start_now),
                        onClick = { runWithTitle(onPlaceStart) },
                    )
                    CaptureChoice(
                        label = stringResource(R.string.action_bring_back),
                        onClick = { onBringBack?.let(::runWithTitle) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun PhraseChip(
    label: String,
    onClick: () -> Unit,
) {
    val interaction = rememberPressSource()
    WithoutRipple {
        Surface(
            modifier =
            Modifier
                .heightIn(min = PhraseChipMinHeight)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            shape = RoundedCornerShape(50),
            color = FocusColors.Live.SurfaceHigh,
        ) {
            Text(
                text = label,
                style = ButtonLabel,
                color = FocusColors.Live.Text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
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

@Suppress("ktlint:standard:function-signature")
private fun CapturePlace.tone(): ChoiceTone =
    when (this) {
        CapturePlace.INBOX -> ChoiceTone.Mint
        CapturePlace.INTERRUPT -> ChoiceTone.Lavender
        CapturePlace.SCHEDULE -> ChoiceTone.Quiet
        CapturePlace.TODAY,
        CapturePlace.START,
        CapturePlace.LATER,
        -> ChoiceTone.Quiet
    }

@Suppress("ktlint:standard:function-signature")
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
@Suppress("ktlint:standard:function-signature")
private fun titleFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = FocusColors.Live.Text,
        unfocusedTextColor = FocusColors.Live.Text,
        cursorColor = FocusColors.Live.Mint,
        focusedBorderColor = FocusColors.Live.Outline,
        unfocusedBorderColor = FocusColors.Live.Outline,
        focusedLabelColor = FocusColors.Live.Muted,
        unfocusedLabelColor = FocusColors.Live.Muted,
        focusedPlaceholderColor = FocusColors.Live.Muted,
        unfocusedPlaceholderColor = FocusColors.Live.Muted,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

@Composable
@Suppress("ktlint:standard:indent")
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
            tint = if (listening) FocusColors.Live.Mint else FocusColors.Live.Muted,
        )
    }
}
