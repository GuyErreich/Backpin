// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.PrimaryButton
import app.backpin.focus.ui.home.HomeMetrics
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private enum class ListenField {
    Title,
    Fill,
}

private val PhraseChipHeight = 32.dp

/**
 * Capture a new task (C1/2) or interrupt now (INT/3). Edit lives in
 * [app.backpin.focus.ui.edit.EditTaskSheet].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CaptureSheet(
    phrases: List<String>,
    onDismiss: () -> Unit,
    onSaveToInbox: (String) -> Unit = {},
    onInterruptNow: (String) -> Unit = {},
    onPlaceToday: (String) -> Unit = {},
    onPlaceStart: (String) -> Unit = {},
    onBringBack: ((String) -> Unit)? = null,
    interrupts: Boolean = false,
    pausedTitle: String? = null,
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
    var needsName by remember { mutableStateOf(false) }
    val interruptNow = interrupts
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
        if (titleText.isEmpty()) {
            needsName = true
            focusRequester.requestFocus()
            return
        }
        needsName = false
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

    val atFirstStep = activeBlank == null

    fun stepBack() {
        if (activeBlank != null) {
            stopListening()
            activeBlank = null
            fillDraft.edit("")
        } else {
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
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
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space),
            ) {
                CaptureSheetHeader(
                    heading =
                    if (interruptNow) {
                        stringResource(R.string.capture_interrupt_heading)
                    } else {
                        stringResource(R.string.capture_heading)
                    },
                    atFirstStep = atFirstStep,
                    onStepBack = ::stepBack,
                )
                if (phrases.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    val prompt = phrasePrompt(template) ?: stringResource(R.string.capture_phrase_word)
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
                                    stringResource(R.string.capture_stop_listening)
                                } else {
                                    stringResource(R.string.capture_speak_missing_word)
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
                        label = stringResource(R.string.capture_phrase_next),
                        onClick = { commitFill() },
                        enabled = fillPhraseBlank(template, fillWord) != null,
                    )
                }
                CaptureTitleField(
                    value = title,
                    onValueChange = { incoming ->
                        if (speech.isListening(ListenField.Title)) return@CaptureTitleField
                        titleDraft.edit(incoming)
                    },
                    label = stringResource(R.string.capture_field_title),
                    listening = speech.isListening(ListenField.Title),
                    reducedMotion = reducedMotion,
                    focusRequester = focusRequester,
                    onSpeakToggle = { speech.toggle(ListenField.Title) },
                    onDone = ::runPrimary,
                )
                LaunchedEffect(focusRequester, activeBlank) {
                    if (activeBlank == null) focusRequester.requestFocus()
                }
                speech.note?.let { note ->
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Live.Muted)
                }
                if (needsName && typedTitle().isEmpty()) {
                    Text(
                        text = stringResource(R.string.capture_name_it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusColors.Live.Muted,
                    )
                }
                if (interruptNow) {
                    CaptureInterruptContent(
                        pausedTitle = pausedTitle,
                        onInterruptNow = { runWithTitle(onInterruptNow) },
                    )
                } else {
                    CaptureCreateContent(
                        onSaveToInbox = { runWithTitle(onSaveToInbox) },
                        onPlaceToday = { runWithTitle(onPlaceToday) },
                        onPlaceStart = { runWithTitle(onPlaceStart) },
                        onBringBack = onBringBack?.let { { runWithTitle(it) } },
                    )
                }
            }
        }
    }
}

/** Create destinations (C1/2): Save to Inbox, Today, Start now, Bring back. */
@Composable
private fun CaptureCreateContent(
    onSaveToInbox: () -> Unit,
    onPlaceToday: () -> Unit,
    onPlaceStart: () -> Unit,
    onBringBack: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PrimaryButton(
            label = stringResource(R.string.action_save_to_inbox),
            onClick = onSaveToInbox,
        )
        Text(
            text = stringResource(R.string.capture_or_when),
            style = HomeMetrics.Eyebrow,
            color = FocusColors.Live.Muted,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            ) {
                CaptureChoice(
                    label = stringResource(R.string.action_today),
                    onClick = onPlaceToday,
                    modifier = Modifier.weight(1f),
                )
                CaptureChoice(
                    label = stringResource(R.string.action_start_now),
                    onClick = onPlaceStart,
                    modifier = Modifier.weight(1f),
                )
            }
            CaptureChoice(
                label = stringResource(R.string.action_bring_back),
                onClick = { onBringBack?.invoke() },
            )
        }
    }
}

/** Interrupt now (INT/3): paused hint and Do this now. */
@Composable
private fun CaptureInterruptContent(
    pausedTitle: String?,
    onInterruptNow: () -> Unit,
) {
    pausedTitle?.let { nowTitle ->
        Text(
            text = stringResource(R.string.capture_interrupt_paused, nowTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Live.Muted,
        )
    }
    PrimaryButton(
        label = stringResource(R.string.action_interrupt_do_this_now),
        onClick = onInterruptNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhraseChip(
    label: String,
    onClick: () -> Unit,
) {
    val interaction = rememberPressSource()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        WithoutRipple {
            Surface(
                modifier =
                Modifier
                    .height(PhraseChipHeight)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    ),
                shape = RoundedCornerShape(percent = 50),
                color = FocusColors.Live.SurfaceHigh,
            ) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusColors.Live.Text,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
