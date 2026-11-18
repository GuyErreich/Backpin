// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.edit

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.domain.CapturePlace
import app.backpin.focus.domain.editPlaces
import app.backpin.focus.ui.capture.CaptureSheetHeader
import app.backpin.focus.ui.capture.CaptureTitleField
import app.backpin.focus.ui.capture.TitleDraft
import app.backpin.focus.ui.capture.UtteranceRecognizer
import app.backpin.focus.ui.capture.rememberSpeechInput
import app.backpin.focus.ui.components.CaptureChoice
import app.backpin.focus.ui.components.ChoiceTone
import app.backpin.focus.ui.components.PrimaryButton
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.motion.rememberReducedMotion
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.ButtonMinHeight
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors

private object EditTitleField

/** Prefill for the edit sheet. [place] is null for a nested interruption. */
internal data class CaptureEdit(
    val taskId: String,
    val title: String,
    val place: CapturePlace?,
    val scheduledEpochDay: Long? = null,
)

/**
 * Edit an existing task (REP/3). Same header and title field as capture;
 * place choices, optional repeat row, and Save live here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditTaskSheet(
    edit: CaptureEdit,
    onDismiss: () -> Unit,
    onCommitEdit: (String, CapturePlace?, Long?) -> Unit,
    onBringBack: ((String) -> Unit)? = null,
    onRepeat: ((String, String) -> Unit)? = null,
    recognizer: UtteranceRecognizer? = null,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleDraft =
        remember(edit.title) {
            TitleDraft().apply { edit(edit.title) }
        }
    val title = titleDraft.text
    var selectedPlace by remember(edit) { mutableStateOf(edit.place) }
    var editCommitted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val speech =
        rememberSpeechInput<EditTitleField>(recognizer, onSystemSpeech) { titleDraft }

    fun stopListening() {
        speech.stop()
    }

    fun commitEditAndClose() {
        if (editCommitted) return
        editCommitted = true
        stopListening()
        val resolved = titleDraft.text.trim().ifEmpty { edit.title }
        val place = selectedPlace ?: edit.place
        val day = if (place == CapturePlace.SCHEDULE) edit.scheduledEpochDay else null
        onCommitEdit(resolved, place, day)
        onDismiss()
    }

    fun placeTyped(place: CapturePlace) {
        if (place == CapturePlace.SCHEDULE) {
            val titleText = titleDraft.text.trim().ifEmpty { edit.title }
            val openBringBack = onBringBack ?: return
            openBringBack(titleText)
            return
        }
        selectedPlace = place
    }

    fun openRepeatFromEdit() {
        val openRepeat = onRepeat ?: return
        val titleText = titleDraft.text.trim().ifEmpty { edit.title }
        editCommitted = true
        stopListening()
        openRepeat(edit.taskId, titleText)
    }

    ModalBottomSheet(
        onDismissRequest = { commitEditAndClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = FocusColors.Live.Surface,
        contentWindowInsets = { WindowInsets.navigationBars },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        BackHandler(onBack = ::commitEditAndClose)
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ButtonGap),
        ) {
            CaptureSheetHeader(
                heading = stringResource(R.string.capture_edit_heading),
                atFirstStep = true,
                onStepBack = ::commitEditAndClose,
            )
            CaptureTitleField(
                value = title,
                onValueChange = { incoming ->
                    if (speech.isListening(EditTitleField)) return@CaptureTitleField
                    titleDraft.edit(incoming)
                },
                label = stringResource(R.string.capture_field_task),
                listening = speech.isListening(EditTitleField),
                reducedMotion = reducedMotion,
                focusRequester = focusRequester,
                onSpeakToggle = { speech.toggle(EditTitleField) },
                onDone = ::commitEditAndClose,
            )
            LaunchedEffect(focusRequester) {
                focusRequester.requestFocus()
            }
            speech.note?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, color = FocusColors.Live.Muted)
            }
            EditTaskContent(
                selectedPlace = selectedPlace,
                onPlace = ::placeTyped,
                onRepeat = onRepeat?.let { { openRepeatFromEdit() } },
                onSave = ::commitEditAndClose,
            )
        }
    }
}

@Composable
private fun EditTaskContent(
    selectedPlace: CapturePlace?,
    onPlace: (CapturePlace) -> Unit,
    onRepeat: (() -> Unit)?,
    onSave: () -> Unit,
) {
    val bringBackLabel = stringResource(R.string.action_bring_back)
    PlaceChoices(
        places = editPlaces().filter { it != CapturePlace.LATER },
        labelFor = { place ->
            if (place == CapturePlace.SCHEDULE) bringBackLabel else place.label()
        },
        selected = selectedPlace,
        onPlace = onPlace,
    )
    if (onRepeat != null) {
        Text(
            text = stringResource(R.string.capture_edit_repeat_subline),
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Live.Muted,
        )
        RepeatSettingsRow(onClick = onRepeat)
    }
    PrimaryButton(
        label = stringResource(R.string.action_save),
        onClick = onSave,
    )
}

@Composable
private fun PlaceChoices(
    places: List<CapturePlace>,
    onPlace: (CapturePlace) -> Unit,
    labelFor: @Composable (CapturePlace) -> String = { it.label() },
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
private fun CapturePlace.tone(): ChoiceTone = when (this) {
    CapturePlace.INBOX -> ChoiceTone.Mint
    CapturePlace.TODAY,
    CapturePlace.START,
    CapturePlace.SCHEDULE,
    CapturePlace.LATER,
    CapturePlace.INTERRUPT,
    -> ChoiceTone.Quiet
}

@Composable
private fun CapturePlace.label(): String = when (this) {
    CapturePlace.INBOX -> stringResource(R.string.capture_place_inbox)
    CapturePlace.TODAY -> stringResource(R.string.action_today)
    CapturePlace.START -> stringResource(R.string.capture_place_start)
    CapturePlace.SCHEDULE -> stringResource(R.string.action_bring_back)
    CapturePlace.LATER -> stringResource(R.string.action_later)
    CapturePlace.INTERRUPT -> stringResource(R.string.action_interrupt)
}

@Composable
private fun RepeatSettingsRow(onClick: () -> Unit) {
    val interaction = rememberPressSource()
    val label = stringResource(R.string.action_repeat_row)
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .height(ButtonMinHeight)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(ButtonRadius),
        color = FocusColors.Live.Surface,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = FocusColors.Live.Text,
            )
            Text(
                text = stringResource(R.string.action_repeat_chevron),
                style = MaterialTheme.typography.titleMedium,
                color = FocusColors.Live.Muted,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}
