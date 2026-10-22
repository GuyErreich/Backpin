// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.ButtonLabel
import app.backpin.focus.ui.theme.ButtonMinHeight
import app.backpin.focus.ui.theme.FocusColors

/**
 * Mint floating control. Home uses + Add. Coming up uses Add countdown.
 * Long-press is forwarded when the caller has one.
 */
@Composable
internal fun AddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    @StringRes labelRes: Int = R.string.action_add,
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
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(labelRes),
                color = FocusColors.Live.OnMint,
                style =
                ButtonLabel.copy(
                    textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }
    }
}
