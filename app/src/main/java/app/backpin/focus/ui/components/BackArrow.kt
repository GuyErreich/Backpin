// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.FocusColors

/** Spoken label when the arrow steps back one screen or step. */
internal const val BACK_LABEL = "Back"

/** Spoken label when the arrow closes a sheet from its first step. */
internal const val CLOSE_LABEL = "Close"

/**
 * The one way back, at the top left of every screen and sheet, with a 48dp target.
 * In a sheet it neither scales nor takes focus, like [CaptureChoice], so the tap is not lost.
 */
@Composable
internal fun BackArrow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inSheet: Boolean = false,
) {
    val interaction = rememberPressSource()
    WithoutRipple {
        IconButton(
            onClick = onClick,
            interactionSource = interaction,
            modifier =
                modifier
                    .size(48.dp)
                    .then(
                        if (inSheet) {
                            Modifier.focusProperties { canFocus = false }
                        } else {
                            Modifier.pressScale(interaction)
                        },
                    ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = label,
                tint = FocusColors.Live.Text,
            )
        }
    }
}
