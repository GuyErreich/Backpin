package app.backpin.focus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FocusColors {
    val Midnight = Color(0xFF10141F)
    val Surface = Color(0xFF1B2230)
    val SurfaceHigh = Color(0xFF243044)
    val Text = Color(0xFFF3F6FB)
    val Muted = Color(0xFFA7B2C4)
    val Mint = Color(0xFF71E2BE)
    val OnMint = Color(0xFF10241C)
    val Amber = Color(0xFFFFC979)
    val OnAmber = Color(0xFF2A1C08)
    val Lavender = Color(0xFFB7A4FF)
    val OnLavender = Color(0xFF1B1430)
    val Outline = Color(0xFF3A465C)
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

private val scheme =
    darkColorScheme(
        primary = FocusColors.Mint,
        onPrimary = FocusColors.OnMint,
        secondary = FocusColors.Lavender,
        onSecondary = FocusColors.OnLavender,
        tertiary = FocusColors.Amber,
        onTertiary = FocusColors.OnAmber,
        background = FocusColors.Midnight,
        onBackground = FocusColors.Text,
        surface = FocusColors.Surface,
        onSurface = FocusColors.Text,
        surfaceVariant = FocusColors.SurfaceHigh,
        onSurfaceVariant = FocusColors.Muted,
        outline = FocusColors.Outline,
    )

private val typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = FocusColors.Text,
            ),
        headlineMedium =
            TextStyle(
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = FocusColors.Text,
            ),
        titleLarge =
            TextStyle(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = FocusColors.Text,
            ),
        titleMedium =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = FocusColors.Text,
            ),
        bodyLarge =
            TextStyle(
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = FocusColors.Text,
            ),
        bodyMedium =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = FocusColors.Muted,
            ),
        labelLarge =
            TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
    )

@Composable
fun FocusStackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content,
    )
}
