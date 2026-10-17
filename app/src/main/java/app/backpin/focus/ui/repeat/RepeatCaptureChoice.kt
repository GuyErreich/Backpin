// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.repeat

import androidx.compose.runtime.Composable
import app.backpin.focus.ui.components.CaptureChoice

/**
 * Repeat destination on [app.backpin.focus.ui.capture.CaptureSheet].
 * Capture only grows an optional last-param hook; the label lives here.
 */
@Composable
internal fun RepeatCaptureChoice(onClick: () -> Unit) {
    CaptureChoice(label = "Repeat", onClick = onClick)
}
