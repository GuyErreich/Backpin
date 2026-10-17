// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.RepeatBand
import app.backpin.focus.domain.RepeatDraft
import app.backpin.focus.domain.label
import app.backpin.focus.ui.capture.SpeakMic
import app.backpin.focus.ui.capture.TitleDraft
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.capture.rememberSpeechInput
import app.backpin.focus.ui.components.ActionRow
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private object RepeatTitleField

private val CadencePresets = listOf(1, 2, 3, 7)

/**
 * Create or edit a repeat. Same sheet chrome as capture: back arrow, title, mic.
 * Band, interval, reminder, and archive stay here so CaptureSheet can rebase.
 * Done and Back both [onCommit] a titled draft. Back with a blank title
 * [onDismiss] so the sheet never looks stuck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatSheet(
    draft: RepeatDraft,
    onCommit: (RepeatDraft) -> Unit,
    onDismiss: () -> Unit = {},
    onArchive: (String) -> Unit,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleDraft =
        remember(draft.id, draft.title) {
            TitleDraft().apply { edit(draft.title) }
        }
    var band by remember(draft.id) { mutableStateOf(draft.band) }
    var everyNDays by remember(draft.id) { mutableIntStateOf(draft.everyNDays.coerceAtLeast(1)) }
    var everyText by remember(draft.id) { mutableStateOf(everyNDays.toString()) }
    var otherSelected by remember(draft.id) { mutableStateOf(everyNDays !in CadencePresets) }
    var reminderOn by remember(draft.id) { mutableStateOf(draft.reminderOn) }
    val focusRequester = remember { FocusRequester() }
    val speech = rememberSpeechInput<RepeatTitleField>(recognizer, onSystemSpeech) { titleDraft }
    val title = titleDraft.text.trim()
    val canSave = title.isNotEmpty()

    fun stopListening() {
        speech.stop()
    }

    fun commit(): Boolean {
        stopListening()
        if (!canSave) return false
        onCommit(
            draft.copy(
                title = titleDraft.text.trim(),
                band = band,
                everyNDays = everyNDays.coerceAtLeast(1),
                reminderOn = reminderOn,
            ),
        )
        return true
    }

    fun save() {
        if (!canSave) return
        commit()
    }

    fun back() {
        if (!commit()) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::back,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Live.Surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = ::back)
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
                BackArrow(
                    label = if (draft.editing) BACK_LABEL else CLOSE_LABEL,
                    onClick = ::back,
                    inSheet = true,
                )
                Text(
                    if (draft.editing) {
                        stringResource(R.string.repeat_sheet_edit_title)
                    } else {
                        stringResource(R.string.repeat_sheet_create_title)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(
                stringResource(R.string.repeat_sheet_lede),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = titleDraft.text,
                onValueChange = { incoming ->
                    if (speech.isListening(RepeatTitleField)) return@OutlinedTextField
                    titleDraft.edit(incoming)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.repeat_sheet_title_label)) },
                placeholder = { Text(stringResource(R.string.repeat_sheet_title_placeholder)) },
                singleLine = true,
                trailingIcon = {
                    SpeakMic(
                        listening = speech.isListening(RepeatTitleField),
                        reducedMotion = reducedMotion,
                        contentDescription =
                            if (speech.isListening(RepeatTitleField)) {
                                stringResource(R.string.repeat_sheet_stop_listening)
                            } else {
                                stringResource(R.string.repeat_sheet_speak_title)
                            },
                        onClick = { speech.toggle(RepeatTitleField) },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
            speech.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Live.Muted)
            }
            Text(stringResource(R.string.repeat_sheet_band), style = MaterialTheme.typography.titleMedium)
            ActionRow {
                listOf(RepeatBand.MORNING, RepeatBand.NOON, RepeatBand.NIGHT).forEach { option ->
                    QuietButton(
                        label = option.label(),
                        selected = band == option,
                        onClick = { band = option },
                    )
                }
            }
            Text(stringResource(R.string.repeat_sheet_how_often), style = MaterialTheme.typography.titleMedium)
            ActionRow {
                CadencePresets.forEach { n ->
                    QuietButton(
                        label = cadencePresetLabel(n),
                        selected = !otherSelected && everyNDays == n,
                        onClick = {
                            otherSelected = false
                            everyNDays = n
                            everyText = n.toString()
                        },
                    )
                }
                QuietButton(
                    label = stringResource(R.string.repeat_sheet_other),
                    selected = otherSelected,
                    onClick = { otherSelected = true },
                )
            }
            if (otherSelected) {
                OutlinedTextField(
                    value = everyText,
                    onValueChange = { incoming ->
                        val digits = incoming.filter { it.isDigit() }.take(3)
                        everyText = digits
                        everyNDays = digits.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.repeat_sheet_days)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.repeat_sheet_reminder), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.repeat_sheet_reminder_copy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(
                    checked = reminderOn,
                    onCheckedChange = { reminderOn = it },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            CaptureChoice(
                label = stringResource(R.string.action_done),
                onClick = ::save,
                enabled = canSave,
                tone = ChoiceTone.Mint,
            )
            if (draft.editing) {
                QuietButton(
                    label = stringResource(R.string.repeat_sheet_archive),
                    onClick = {
                        val id = draft.id
                        if (id != null) {
                            stopListening()
                            onArchive(id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun cadencePresetLabel(days: Int): String =
    if (days == 1) {
        stringResource(R.string.repeat_sheet_every_day)
    } else {
        stringResource(R.string.repeat_sheet_every_n_days, days)
    }
