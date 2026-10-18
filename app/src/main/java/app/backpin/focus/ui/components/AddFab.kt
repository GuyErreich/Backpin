// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.ButtonLabel
import app.backpin.focus.ui.theme.ButtonMinHeight
import app.backpin.focus.ui.theme.FocusColors

/**
 * Mint + Add control that replaced the full-width Capture bar.
 * Tap opens Capture. Long-press is forwarded when the caller has one.
 */
@Composable
internal fun AddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = rememberPressSource()
    Surface(
        modifier =
        modifier
            .heightIn(min = ButtonMinHeight)
            .pressScale(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(24.dp),
        color = FocusColors.Live.Mint,
        contentColor = FocusColors.Live.OnMint,
        shadowElevation = 6.dp,
    ) {
        Text(
            text = stringResource(R.string.action_add),
            color = FocusColors.Live.OnMint,
            style = ButtonLabel,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
