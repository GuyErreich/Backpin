// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
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
import app.backpin.focus.ui.theme.CardActionGap
import app.backpin.focus.ui.theme.CardActionHeight
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

/** Horizontal room around a card-action label. The button stays as wide as that label. */
private val CardActionPadding = 12.dp

private val LocalButtonPadding = compositionLocalOf { ButtonPadding }
private val LocalButtonVerticalPadding = compositionLocalOf { 12.dp }
private val LocalButtonMinHeight = compositionLocalOf { ButtonMinHeight }

/** Card actions are exactly [CardActionHeight]. Choice buttons only have a minimum. */
private val LocalLockButtonHeight = compositionLocalOf { false }

private fun Color.shown(enabled: Boolean): Color = if (enabled) this else copy(alpha = DISABLED_ALPHA)

/** Tone swatch for the Signal palette. */
private fun AccentTone.color(): Color = when (this) {
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
): Color = when (tone) {
    ChoiceTone.Mint -> accentLabelColor(AccentTone.Mint, enabled)
    ChoiceTone.Lavender -> accentLabelColor(AccentTone.Lavender, enabled)
    ChoiceTone.Amber -> accentLabelColor(AccentTone.Amber, enabled)
    ChoiceTone.Quiet -> FocusColors.Text.shown(enabled)
}

/** Soft container behind a default choice. Outlined choices stay transparent. */
internal fun choiceContainerColor(
    tone: ChoiceTone,
    filled: Boolean,
    enabled: Boolean = true,
): Color {
    if (!filled) return Color.Transparent
    val fill =
        when (tone) {
            ChoiceTone.Lavender -> FocusColors.LavenderContainer
            ChoiceTone.Mint -> FocusColors.Mint.copy(alpha = 0.25f)
            ChoiceTone.Amber -> FocusColors.Amber.copy(alpha = 0.25f)
            ChoiceTone.Quiet -> FocusColors.SurfaceHigh
        }
    return fill.shown(enabled)
}

/**
 * The one button shape: outlined, 16dp corners, no ripple.
 * Choice buttons are at least 56dp. [CardActions] locks its buttons to 40dp.
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
    val minHeight = LocalButtonMinHeight.current
    val locked = LocalLockButtonHeight.current
    val heightModifier = if (locked) Modifier.height(minHeight) else Modifier.heightIn(min = minHeight)
    WithoutRipple {
        if (locked) {
            LabelWidthButton(
                label = label,
                labelColor = labelColor,
                borderColor = borderColor,
                onClick = onClick,
                modifier = modifier.then(heightModifier),
                enabled = enabled,
                interactionSource = interactionSource,
                containerColor = containerColor,
            )
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                interactionSource = interactionSource,
                modifier = modifier.then(heightModifier),
                shape = RoundedCornerShape(ButtonRadius),
                border = BorderStroke(ButtonBorder, borderColor),
                contentPadding =
                PaddingValues(
                    horizontal = LocalButtonPadding.current,
                    vertical = LocalButtonVerticalPadding.current,
                ),
                colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = containerColor,
                    contentColor = labelColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor = labelColor,
                ),
            ) {
                Text(
                    label,
                    color = labelColor,
                    style = ButtonLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A card action. [OutlinedButton] is at least 58dp wide, which would make every short label the same block.
 * This one is exactly as wide as the label.
 */
@Composable
private fun LabelWidthButton(
    label: String,
    labelColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    containerColor: Color,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(ButtonRadius),
        color = containerColor,
        contentColor = labelColor,
        border = BorderStroke(ButtonBorder, borderColor),
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxHeight()
                .padding(horizontal = LocalButtonPadding.current),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = labelColor,
                style = ButtonLabel,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
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
    val color =
        when (tone) {
            AccentTone.Mint -> FocusColors.Live.Mint
            AccentTone.Lavender -> FocusColors.Live.Lavender
            AccentTone.Amber -> FocusColors.Live.Amber
        }.shown(enabled)
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
    val text = FocusColors.Live.Text.shown(enabled)
    val outline = FocusColors.Live.Outline.shown(enabled)
    OutlinedFocusButton(
        label = label,
        labelColor = text,
        borderColor = if (picked) text else outline,
        onClick = onClick,
        modifier =
        modifier
            .pressScale(interaction)
            .then(if (selected != null) Modifier.semantics { this.selected = picked } else Modifier),
        enabled = enabled,
        interactionSource = interaction,
        containerColor = if (picked) FocusColors.Live.SurfaceHigh else Color.Transparent,
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
    filled: Boolean = false,
    selected: Boolean = false,
) {
    val labelColor =
        when (tone) {
            ChoiceTone.Mint -> FocusColors.Live.Mint
            ChoiceTone.Lavender -> FocusColors.Live.Lavender
            ChoiceTone.Amber -> FocusColors.Live.Amber
            ChoiceTone.Quiet -> FocusColors.Live.Text
        }.shown(enabled)
    val borderColor =
        when {
            selected && tone == ChoiceTone.Quiet -> FocusColors.Live.Text.shown(enabled)
            tone == ChoiceTone.Quiet -> FocusColors.Live.Outline.shown(enabled)
            else -> labelColor
        }
    val fill =
        when (tone) {
            ChoiceTone.Lavender -> FocusColors.Live.LavenderContainer
            ChoiceTone.Mint -> FocusColors.Live.Mint.copy(alpha = 0.25f)
            ChoiceTone.Amber -> FocusColors.Live.Amber.copy(alpha = 0.25f)
            ChoiceTone.Quiet -> FocusColors.Live.SurfaceHigh
        }
    OutlinedFocusButton(
        label = label,
        labelColor = labelColor,
        borderColor = borderColor,
        onClick = onClick,
        modifier =
        Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier),
        enabled = enabled,
        interactionSource = null,
        containerColor =
        when {
            filled -> fill.shown(enabled)
            selected -> FocusColors.Live.SurfaceHigh
            else -> Color.Transparent
        },
    )
}

/**
 * Equal-width buttons for sheets, Settings, and dialogs. Up to three to a line.
 * Four or more wrap into balanced lines whose cells all keep the same width, and each
 * line takes its tallest button's height. One button fills the line.
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

/**
 * Actions on a list card. They wrap, each only as wide as its label, 40dp tall, with 8dp between them.
 * The title stays the first thing you read.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun CardActions(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalButtonMinHeight provides CardActionHeight,
        LocalLockButtonHeight provides true,
        LocalButtonPadding provides CardActionPadding,
        LocalButtonVerticalPadding provides 0.dp,
        LocalMinimumInteractiveComponentSize provides 0.dp,
    ) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardActionGap),
            verticalArrangement = Arrangement.spacedBy(CardActionGap),
        ) {
            content()
        }
    }
}
