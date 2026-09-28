package app.backpin.focus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.backpin.focus.ui.motion.WithoutRipple
import app.backpin.focus.ui.motion.pressScale
import app.backpin.focus.ui.motion.rememberPressSource
import app.backpin.focus.ui.theme.ButtonBorder
import app.backpin.focus.ui.theme.ButtonGap
import app.backpin.focus.ui.theme.ButtonLabel
import app.backpin.focus.ui.theme.ButtonMinHeight
import app.backpin.focus.ui.theme.ButtonPadding
import app.backpin.focus.ui.theme.ButtonRadius
import app.backpin.focus.ui.theme.FocusColors

/** Mint is Done, Resume, and Inbox. Lavender is Interrupt and events. Amber is Schedule. */
internal enum class AccentTone {
    Mint,
    Lavender,
    Amber,
}

/** [AccentTone] for sheet buttons, plus Quiet for everything else. */
internal enum class ChoiceTone {
    Mint,
    Lavender,
    Amber,
    Quiet,
}

private const val DISABLED_ALPHA = 0.38f
private const val MAX_PER_ROW = 3

/** Inside an [ActionRow] the row sets the width, so the label gets the room instead. */
private val RowButtonPadding = 8.dp

private val LocalButtonPadding = compositionLocalOf { ButtonPadding }

private fun Color.shown(enabled: Boolean): Color = if (enabled) this else copy(alpha = DISABLED_ALPHA)

private fun AccentTone.color(): Color =
    when (this) {
        AccentTone.Mint -> FocusColors.Mint
        AccentTone.Lavender -> FocusColors.Lavender
        AccentTone.Amber -> FocusColors.Amber
    }

/** Label of an [AccentButton]: the same color as its border. */
internal fun accentLabelColor(
    tone: AccentTone,
    enabled: Boolean = true,
): Color = tone.color().shown(enabled)

/** Label of a [CaptureChoice]: the accent for Mint, Lavender, and Amber, plain text for Quiet. */
internal fun choiceLabelColor(
    tone: ChoiceTone,
    enabled: Boolean = true,
): Color =
    when (tone) {
        ChoiceTone.Mint -> accentLabelColor(AccentTone.Mint, enabled)
        ChoiceTone.Lavender -> accentLabelColor(AccentTone.Lavender, enabled)
        ChoiceTone.Amber -> accentLabelColor(AccentTone.Amber, enabled)
        ChoiceTone.Quiet -> FocusColors.Text.shown(enabled)
    }

private fun choiceBorderColor(
    tone: ChoiceTone,
    enabled: Boolean,
): Color = if (tone == ChoiceTone.Quiet) FocusColors.Outline.shown(enabled) else choiceLabelColor(tone, enabled)

/**
 * The one button shape: outlined, 56dp tall, 16dp corners, no ripple.
 * The label color is set on the text itself; theme typography would otherwise win over the content color.
 */
@Composable
private fun OutlinedFocusButton(
    label: String,
    labelColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    containerColor: Color = Color.Transparent,
) {
    WithoutRipple {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = modifier.heightIn(min = ButtonMinHeight),
            shape = RoundedCornerShape(ButtonRadius),
            border = BorderStroke(ButtonBorder, borderColor),
            contentPadding = PaddingValues(horizontal = LocalButtonPadding.current, vertical = 12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = containerColor,
                    contentColor = labelColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor = labelColor,
                ),
        ) {
            Text(label, color = labelColor, style = ButtonLabel, textAlign = TextAlign.Center)
        }
    }
}

/** Colored outline for Done, Resume, Inbox, Interrupt, events, and Schedule. Scales while pressed. */
@Composable
internal fun AccentButton(
    label: String,
    tone: AccentTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = rememberPressSource()
    val color = accentLabelColor(tone, enabled)
    OutlinedFocusButton(
        label = label,
        labelColor = color,
        borderColor = color,
        onClick = onClick,
        modifier = modifier.pressScale(interaction),
        enabled = enabled,
        interactionSource = interaction,
    )
}

/**
 * Every other button. Scales while pressed. [selected] marks the current pick in a set of
 * choices, such as a Settings option, with a filled surface and a brighter edge.
 */
@Composable
internal fun QuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
) {
    val interaction = rememberPressSource()
    val picked = selected == true
    OutlinedFocusButton(
        label = label,
        labelColor = choiceLabelColor(ChoiceTone.Quiet, enabled),
        borderColor = if (picked) FocusColors.Text.shown(enabled) else choiceBorderColor(ChoiceTone.Quiet, enabled),
        onClick = onClick,
        modifier =
            modifier
                .pressScale(interaction)
                .then(if (selected != null) Modifier.semantics { this.selected = picked } else Modifier),
        enabled = enabled,
        interactionSource = interaction,
        containerColor = if (picked) FocusColors.SurfaceHigh else Color.Transparent,
    )
}

/**
 * Full-width sheet button. No press scale: a graphics layer that shrinks during the
 * down event cancels the click before onClick. It refuses focus so the keyboard
 * does not resize the sheet between down and up.
 */
@Composable
internal fun CaptureChoice(
    label: String,
    onClick: () -> Unit,
    tone: ChoiceTone = ChoiceTone.Quiet,
    enabled: Boolean = true,
) {
    OutlinedFocusButton(
        label = label,
        labelColor = choiceLabelColor(tone, enabled),
        borderColor = choiceBorderColor(tone, enabled),
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false },
        enabled = enabled,
        interactionSource = null,
    )
}

/**
 * Equal-width buttons, up to three to a line. Four or more wrap into balanced lines
 * whose cells all keep the same width, and each line takes its tallest button's height.
 * One button fills the line.
 */
@Composable
internal fun ActionRow(
    modifier: Modifier = Modifier,
    gap: Dp = ButtonGap,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalButtonPadding provides RowButtonPadding) {
        Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
            if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
            val gapPx = gap.roundToPx()
            val lines = (measurables.size + MAX_PER_ROW - 1) / MAX_PER_ROW
            val perLine = (measurables.size + lines - 1) / lines
            val width = constraints.maxWidth
            val cell = ((width - gapPx * (perLine - 1)) / perLine).coerceAtLeast(0)
            val rows =
                measurables.chunked(perLine).map { line ->
                    val height = line.maxOf { it.maxIntrinsicHeight(cell) }
                    line.map { it.measure(Constraints.fixed(cell, height)) }
                }
            val total = rows.sumOf { row -> row.maxOf { it.height } } + gapPx * (rows.size - 1)
            layout(width, total) {
                var y = 0
                rows.forEach { row ->
                    row.forEachIndexed { index, placeable ->
                        placeable.placeRelative(index * (cell + gapPx), y)
                    }
                    y += row.maxOf { it.height } + gapPx
                }
            }
        }
    }
}
