// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.backpin.focus.domain.ThemeId

private val signalPalette = paletteFor(ThemeId.SIGNAL)

object FocusColors {
    val Midnight: Color get() = signalPalette.background
    val Surface: Color get() = signalPalette.surface
    val SurfaceHigh: Color get() = signalPalette.surfaceRaised
    val Text: Color get() = signalPalette.textPrimary
    val Muted: Color get() = signalPalette.textSecondary
    val Mint: Color get() = signalPalette.done
    val OnMint: Color get() = signalPalette.onAccent
    val Amber: Color get() = signalPalette.missed
    val OnAmber: Color get() = signalPalette.onAccent
    val Lavender: Color get() = signalPalette.now
    val OnLavender: Color get() = signalPalette.onAccent

    /** Soft fill for the default lavender choice, such as Again in N days. */
    val LavenderContainer: Color get() = signalPalette.now.copy(alpha = CHOICE_FILL_ALPHA)
    val Outline: Color get() = signalPalette.outline
    val Danger: Color get() = signalPalette.danger

    /** Live colors from [LocalFocusPalette]. */
    object Live {
        val Midnight: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.background
        val Surface: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.surface
        val SurfaceHigh: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.surfaceRaised
        val Text: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.textPrimary
        val Muted: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.textSecondary
        val Mint: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.done
        val OnMint: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.onAccent
        val Amber: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.missed
        val OnAmber: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.onAccent
        val Lavender: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.now
        val OnLavender: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.onAccent

        /** Soft fill for the default lavender choice, such as Again in N days. */
        val LavenderContainer: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.now.copy(alpha = CHOICE_FILL_ALPHA)
        val Outline: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.outline
        val Danger: Color
            @Composable
            @ReadOnlyComposable
            get() = LocalFocusPalette.current.danger
    }
}

val CardRadius = 24.dp
val Space = 8.dp

/**
 * Sheets, Settings, and dialogs are at least this tall, so a tap lands on the one you meant.
 * List-card actions use [CardActionHeight] instead.
 */
val ButtonMinHeight = 56.dp

/** A list-card action: 48dp so a thumb lands on the one you meant. */
val CardActionHeight = 48.dp

/** Mint Done pill on a compact Home row. Fits inside the 43dp visual row. */
val RowDoneHeight = 32.dp

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
    themeId: ThemeId = ThemeId.SIGNAL,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeId)
    CompositionLocalProvider(
        LocalFocusPalette provides palette,
        LocalFocusExtraColors provides palette.extras,
        LocalContentColor provides palette.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = colorsFor(themeId),
            typography = palette.withTypography(typography),
            content = content,
        )
    }
}

/** Palette for [themeId]. */
fun colorsFor(themeId: ThemeId) = when (themeId) {
    ThemeId.SIGNAL,
    ThemeId.STILL_WATER,
    ThemeId.EMBER,
    ThemeId.CLEAR_LINE,
    -> paletteFor(themeId).toColorScheme()
}
