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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.backpin.focus.domain.Repeat
import app.backpin.focus.domain.RepeatBand
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
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private object RepeatTitleField

/** Prefill for [RepeatSheet]. [id] is null when creating. */
internal data class RepeatDraft(
    val id: String? = null,
    val title: String = "",
    val band: RepeatBand = RepeatBand.MORNING,
    val everyNDays: Int = 1,
    val reminderOn: Boolean = true,
    val sortOrder: Long = 0L,
    val anchorEpochDay: Long = 0L,
) {
    val editing: Boolean
        get() = id != null
}

internal fun Repeat.toDraft(): RepeatDraft =
    RepeatDraft(
        id = id,
        title = title,
        band = band,
        everyNDays = everyNDays,
        reminderOn = reminderOn,
        sortOrder = sortOrder,
        anchorEpochDay = anchorEpochDay,
    )

/**
 * Create or edit a repeat. Same sheet chrome as capture: back arrow, title, mic.
 * Band, interval, reminder, and archive stay here so CaptureSheet can rebase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepeatSheet(
    draft: RepeatDraft,
    onDismiss: () -> Unit,
    onCreate: (title: String, band: RepeatBand, everyNDays: Int, reminderOn: Boolean) -> Unit,
    onUpdate: (
        id: String,
        title: String,
        band: RepeatBand,
        everyNDays: Int,
        reminderOn: Boolean,
        sortOrder: Long,
        anchorEpochDay: Long,
    ) -> Unit,
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
    var reminderOn by remember(draft.id) { mutableStateOf(draft.reminderOn) }
    val focusRequester = remember { FocusRequester() }
    val speech = rememberSpeechInput<RepeatTitleField>(recognizer, onSystemSpeech) { titleDraft }
    val title = titleDraft.text.trim()
    val canSave = title.isNotEmpty()

    fun stopListening() {
        speech.stop()
    }

    fun dismiss() {
        stopListening()
        onDismiss()
    }

    fun save() {
        if (!canSave) return
        val interval = everyNDays.coerceAtLeast(1)
        val id = draft.id
        if (id == null) {
            onCreate(title, band, interval, reminderOn)
        } else {
            onUpdate(id, title, band, interval, reminderOn, draft.sortOrder, draft.anchorEpochDay)
        }
        stopListening()
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
                BackArrow(
                    label = if (draft.editing) BACK_LABEL else CLOSE_LABEL,
                    onClick = ::dismiss,
                    inSheet = true,
                )
                Text(
                    if (draft.editing) "This repeat." else "A small repeating thing.",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            Text(
                "Repeats stay off Today. Checking them off is enough.",
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
                label = { Text("Title") },
                placeholder = { Text("Take meds") },
                singleLine = true,
                trailingIcon = {
                    SpeakMic(
                        listening = speech.isListening(RepeatTitleField),
                        reducedMotion = reducedMotion,
                        contentDescription =
                            if (speech.isListening(RepeatTitleField)) {
                                "Stop listening"
                            } else {
                                "Speak the title"
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
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
            }
            Text("Band", style = MaterialTheme.typography.titleMedium)
            ActionRow {
                listOf(RepeatBand.MORNING, RepeatBand.NOON, RepeatBand.NIGHT).forEach { option ->
                    QuietButton(
                        label = option.label(),
                        selected = band == option,
                        onClick = { band = option },
                    )
                }
            }
            Text("Every N days", style = MaterialTheme.typography.titleMedium)
            Text(
                "1 is every day.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ActionRow {
                listOf(1, 2, 3, 7).forEach { n ->
                    QuietButton(
                        label = if (n == 1) "Every day" else "Every $n days",
                        selected = everyNDays == n,
                        onClick = {
                            everyNDays = n
                            everyText = n.toString()
                        },
                    )
                }
            }
            OutlinedTextField(
                value = everyText,
                onValueChange = { incoming ->
                    val digits = incoming.filter { it.isDigit() }.take(3)
                    everyText = digits
                    everyNDays = digits.toIntOrNull()?.coerceAtLeast(1) ?: 1
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Days") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reminder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A silent reminder at the start of that band.",
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
                label = "Save",
                onClick = ::save,
                enabled = canSave,
            )
            if (draft.editing) {
                QuietButton(
                    label = "Archive",
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
