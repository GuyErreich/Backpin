// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FocusColors {
    val Midnight: Color get() = activePalette.background
    val Surface: Color get() = activePalette.surface
    val SurfaceHigh: Color get() = activePalette.surfaceRaised
    val Text: Color get() = activePalette.textPrimary
    val Muted: Color get() = activePalette.textSecondary
    val Mint: Color get() = activePalette.done
    val OnMint: Color get() = activePalette.onAccent
    val Amber: Color get() = activePalette.missed
    val OnAmber: Color get() = activePalette.onAccent
    val Lavender: Color get() = activePalette.now
    val OnLavender: Color get() = activePalette.onAccent
    /** Soft fill for the default lavender choice, such as Again in N days. */
    val LavenderContainer: Color get() = activePalette.now.copy(alpha = CHOICE_FILL_ALPHA)
    val Outline: Color get() = activePalette.outline
}

val CardRadius = 24.dp
val Space = 8.dp

/**
 * Sheets, Settings, and dialogs are at least this tall, so a tap lands on the one you meant.
 * List-card actions use [CardActionHeight] instead.
 */
val ButtonMinHeight = 56.dp

/** A list-card action: shorter than a choice button, and only as wide as its label. */
val CardActionHeight = 40.dp

/** Space between list-card actions. Choice rows keep [ButtonGap]. */
val CardActionGap = 8.dp
val ButtonRadius = 16.dp

/** Space between neighboring choice buttons, in rows and in stacks. */
val ButtonGap = 12.dp
val ButtonPadding = 20.dp
val ButtonBorder = 1.5.dp

/** Label on every button. It carries no color; each button passes its own. */
val ButtonLabel =
    TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

private val typography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun FocusStackTheme(
    themeId: app.backpin.focus.domain.ThemeId = app.backpin.focus.domain.ThemeId.SIGNAL,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeId)
    activePalette = palette
    key(themeId) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalFocusPalette provides palette,
            LocalFocusExtraColors provides palette.extras,
            androidx.compose.material3.LocalContentColor provides palette.textPrimary,
        ) {
            MaterialTheme(
                colorScheme = colorsFor(themeId),
                typography = palette.withTypography(typography),
                content = content,
            )
        }
    }
}

/** Palette for [themeId]. */
fun colorsFor(themeId: app.backpin.focus.domain.ThemeId) = when (themeId) {
    app.backpin.focus.domain.ThemeId.SIGNAL,
    app.backpin.focus.domain.ThemeId.STILL_WATER,
    app.backpin.focus.domain.ThemeId.EMBER,
    app.backpin.focus.domain.ThemeId.CLEAR_LINE,
    -> paletteFor(themeId).toColorScheme()
}
