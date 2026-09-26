package app.backpin.focus.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

/**
 * Quiet motion for Home and the pile screens.
 * Navigation and list changes use ease-out. Nothing here bounces.
 */
internal object MotionSpec {
    const val NavigateMillis = 240
    const val SettleMillis = 220
    const val ReorderMillis = 220
    const val PressMillis = 100
    const val LiftMillis = 160
    const val ReducedFadeMillis = 120
    const val PressedScale = 0.98f
    const val LiftScale = 1.03f
    const val SharedAxisFraction = 0.16f

    val EaseOut: Easing = LinearOutSlowInEasing

    fun <T> easeOut(durationMillis: Int): AnimationSpec<T> = tween(durationMillis = durationMillis, easing = EaseOut)
}

@Composable
internal fun <T> motionSpec(
    durationMillis: Int,
    reducedMotion: Boolean,
): AnimationSpec<T> = if (reducedMotion) snap() else MotionSpec.easeOut(durationMillis)

/** Scale while pressed. Reduced motion stays at rest. No ripple. */
@Composable
internal fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val reducedMotion = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) MotionSpec.PressedScale else 1f,
        animationSpec = motionSpec(MotionSpec.PressMillis, reducedMotion),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Material buttons draw their own ripple. This turns that off for the rounded controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WithoutRipple(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
}

internal fun pileEnter(
    forward: Boolean,
    reducedMotion: Boolean,
): EnterTransition {
    val fade =
        fadeIn(
            animationSpec =
                tween(
                    durationMillis = if (reducedMotion) MotionSpec.ReducedFadeMillis else MotionSpec.NavigateMillis,
                    easing = MotionSpec.EaseOut,
                ),
        )
    if (reducedMotion) return fade
    val slide =
        slideInHorizontally(
            animationSpec = tween(durationMillis = MotionSpec.NavigateMillis, easing = MotionSpec.EaseOut),
        ) { fullWidth ->
            val direction = if (forward) 1 else -1
            (fullWidth * MotionSpec.SharedAxisFraction * direction).roundToInt()
        }
    return fade + slide
}

internal fun pileExit(
    forward: Boolean,
    reducedMotion: Boolean,
): ExitTransition {
    val fade =
        fadeOut(
            animationSpec =
                tween(
                    durationMillis = if (reducedMotion) MotionSpec.ReducedFadeMillis else MotionSpec.NavigateMillis,
                    easing = MotionSpec.EaseOut,
                ),
        )
    if (reducedMotion) return fade
    val slide =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = MotionSpec.NavigateMillis, easing = MotionSpec.EaseOut),
        ) { fullWidth ->
            val direction = if (forward) -1 else 1
            (fullWidth * MotionSpec.SharedAxisFraction * direction).roundToInt()
        }
    return fade + slide
}
