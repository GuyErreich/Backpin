package app.backpin.focus.ui.edit

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.capture.SpeakMic
import app.backpin.focus.ui.capture.TitleDraft
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.capture.rememberSpeechInput
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private object EditTitleField

/**
 * Renames one open task. Back closes without writing. Save writes a non-blank
 * title and closes. A blank title leaves the sheet open.
 *
 * Repeating-tasks can pass [extraContent] under the title without replacing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTaskSheet(
    initialTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    heading: String = "Edit",
    extraContent: @Composable (() -> Unit)? = null,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val reducedMotion = rememberReducedMotion()
    val draft =
        remember(initialTitle) {
            TitleDraft().apply { edit(initialTitle) }
        }
    val speech = rememberSpeechInput<EditTitleField>(recognizer, onSystemSpeech) { draft }

    fun dismiss() {
        speech.stop()
        onDismiss()
    }

    fun save() {
        val trimmed = draft.text.trim()
        if (trimmed.isEmpty()) return
        speech.stop()
        onSave(trimmed)
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
                    heading,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
            OutlinedTextField(
                value = draft.text,
                onValueChange = { incoming ->
                    if (speech.isListening(EditTitleField)) return@OutlinedTextField
                    draft.edit(incoming)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                placeholder = { Text("A stray thought") },
                singleLine = true,
                trailingIcon = {
                    SpeakMic(
                        listening = speech.isListening(EditTitleField),
                        reducedMotion = reducedMotion,
                        contentDescription =
                            if (speech.isListening(EditTitleField)) "Stop listening" else "Speak the title",
                        onClick = { speech.toggle(EditTitleField) },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            speech.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Muted)
            }
            extraContent?.invoke()
            CaptureChoice("Save", onClick = ::save)
        }
    }
}
