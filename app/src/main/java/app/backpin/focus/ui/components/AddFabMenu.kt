// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.backpin.focus.R
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private val MenuWidth = 260.dp

/**
 * INT/2 long-press menu on + Add. Offered only while a task is active.
 * Quiet surface, no ripple.
 */
@Composable
internal fun AddFabMenu(
    onAdd: () -> Unit,
    onInterrupt: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(MenuWidth),
            shape = RoundedCornerShape(ButtonRadius),
            color = FocusColors.Live.SurfaceHigh,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = Space)) {
                MenuRow(
                    title = stringResource(R.string.action_add),
                    subtitle = stringResource(R.string.add_fab_menu_add_subtitle),
                    onClick = {
                        onDismiss()
                        onAdd()
                    },
                )
                MenuRow(
                    title = stringResource(R.string.add_fab_menu_interrupt),
                    subtitle = stringResource(R.string.add_fab_menu_interrupt_subtitle),
                    onClick = {
                        onDismiss()
                        onInterrupt()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = FocusColors.Live.Text,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FocusColors.Live.Muted,
        )
    }
}
