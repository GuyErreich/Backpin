// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.CLOSE_LABEL
import app.backpin.focus.ui.motion.MotionSpec
import app.backpin.focus.ui.motion.motionSpec
import app.backpin.focus.ui.theme.FocusColors

/** Shared back-arrow + heading row for capture and edit sheets. */
@Composable
internal fun CaptureSheetHeader(
    heading: String,
    atFirstStep: Boolean,
    onStepBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackArrow(
            label = if (atFirstStep) CLOSE_LABEL else BACK_LABEL,
            onClick = onStepBack,
            inSheet = true,
        )
        Text(
            heading,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/** Shared title field with speak mic for capture and edit sheets. */
@Composable
internal fun CaptureTitleField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    listening: Boolean,
    reducedMotion: Boolean,
    focusRequester: FocusRequester,
    onSpeakToggle: () -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        shape = RoundedCornerShape(8.dp),
        colors = captureTitleFieldColors(),
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.capture_field_placeholder)) },
        singleLine = true,
        trailingIcon = {
            SpeakMic(
                listening = listening,
                reducedMotion = reducedMotion,
                contentDescription =
                if (listening) {
                    stringResource(R.string.capture_stop_listening)
                } else {
                    stringResource(R.string.capture_speak_title)
                },
                onClick = onSpeakToggle,
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
internal fun captureTitleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FocusColors.Live.Text,
    unfocusedTextColor = FocusColors.Live.Text,
    cursorColor = FocusColors.Live.Mint,
    focusedBorderColor = FocusColors.Live.Outline,
    unfocusedBorderColor = FocusColors.Live.Outline,
    focusedLabelColor = FocusColors.Live.Muted,
    unfocusedLabelColor = FocusColors.Live.Muted,
    focusedPlaceholderColor = FocusColors.Live.Muted,
    unfocusedPlaceholderColor = FocusColors.Live.Muted,
    focusedContainerColor = FocusColors.Live.Surface,
    unfocusedContainerColor = FocusColors.Live.Surface,
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
            tint = if (listening) FocusColors.Live.Mint else FocusColors.Live.Muted,
        )
    }
}
