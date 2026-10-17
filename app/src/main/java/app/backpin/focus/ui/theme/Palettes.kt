// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.backpin.focus.domain.ThemeId

/** Soft fill behind a default accent choice, matching the old lavender container. */
internal const val CHOICE_FILL_ALPHA = 0x40 / 255f

/** The twelve color roles every theme fills. */
@Immutable
data class ThemePalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val now: Color,
    val missed: Color,
    val done: Color,
    val danger: Color,
) {
    val extras: FocusExtraColors
        get() = FocusExtraColors(
            now = now,
            missed = missed,
            done = done,
            surfaceRaised = surfaceRaised,
        )

    fun toColorScheme(): ColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = now,
        onSecondary = onAccent,
        secondaryContainer = now.copy(alpha = CHOICE_FILL_ALPHA),
        onSecondaryContainer = now,
        tertiary = missed,
        onTertiary = onAccent,
        error = danger,
        onError = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = textSecondary,
        outline = outline,
    )
}

/**
 * Roles that do not map onto [ColorScheme].
 * Read them from [LocalFocusExtraColors] or [MaterialTheme.extras].
 */
@Immutable
data class FocusExtraColors(
    val now: Color,
    val missed: Color,
    val done: Color,
    val surfaceRaised: Color,
)

private val SignalPalette =
    ThemePalette(
        background = Color(0xFF101317),
        surface = Color(0xFF191E24),
        surfaceRaised = Color(0xFF222932),
        outline = Color(0xFF6C7783),
        textPrimary = Color(0xFFF2F5F8),
        textSecondary = Color(0xFFAEB8C2),
        accent = Color(0xFF4FD1A5),
        onAccent = Color(0xFF06231A),
        now = Color(0xFF5AB4FF),
        missed = Color(0xFFFFB020),
        done = Color(0xFF4FD1A5),
        danger = Color(0xFFFF6161),
    )

private val StillWaterPalette =
    ThemePalette(
        background = Color(0xFF14181C),
        surface = Color(0xFF1C2126),
        surfaceRaised = Color(0xFF252B31),
        outline = Color(0xFF707A80),
        textPrimary = Color(0xFFE3E7EA),
        textSecondary = Color(0xFFA3ACB3),
        accent = Color(0xFF9CC7B4),
        onAccent = Color(0xFF13201A),
        now = Color(0xFFA9C1D6),
        missed = Color(0xFFD4B48C),
        done = Color(0xFF9CC7B4),
        danger = Color(0xFFE0A3A3),
    )

private val EmberPalette =
    ThemePalette(
        background = Color(0xFF1A1411),
        surface = Color(0xFF261C17),
        surfaceRaised = Color(0xFF33251E),
        outline = Color(0xFF897063),
        textPrimary = Color(0xFFFFF1E6),
        textSecondary = Color(0xFFD6B9A6),
        accent = Color(0xFFFF9A4D),
        onAccent = Color(0xFF2A1306),
        now = Color(0xFFF5A3D7),
        missed = Color(0xFFF2C46B),
        done = Color(0xFF7FE0A0),
        danger = Color(0xFFFF6B6B),
    )

private val ClearLinePalette =
    ThemePalette(
        background = Color(0xFF000000),
        surface = Color(0xFF0D0D0D),
        surfaceRaised = Color(0xFF1A1A1A),
        outline = Color(0xFFB0B0B0),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFD9D9D9),
        accent = Color(0xFF5CFFC8),
        onAccent = Color(0xFF000000),
        now = Color(0xFF7CC4FF),
        missed = Color(0xFFFFC44D),
        done = Color(0xFF5CFFC8),
        danger = Color(0xFFFF8A8A),
    )

fun paletteFor(themeId: ThemeId): ThemePalette = when (themeId) {
    ThemeId.SIGNAL -> SignalPalette
    ThemeId.STILL_WATER -> StillWaterPalette
    ThemeId.EMBER -> EmberPalette
    ThemeId.CLEAR_LINE -> ClearLinePalette
}

/** Live palette for non-composable helpers. [FocusStackTheme] updates this before children draw. */
internal var activePalette: ThemePalette = SignalPalette

val LocalFocusPalette = staticCompositionLocalOf { paletteFor(ThemeId.SIGNAL) }

val LocalFocusExtraColors = staticCompositionLocalOf { paletteFor(ThemeId.SIGNAL).extras }

val MaterialTheme.extras: FocusExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFocusExtraColors.current
