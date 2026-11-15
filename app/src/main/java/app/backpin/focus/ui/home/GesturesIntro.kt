// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.components.BACK_LABEL
import app.backpin.focus.ui.components.BackArrow
import app.backpin.focus.ui.components.QuietButton
import app.backpin.focus.ui.theme.CardRadius
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.Space

private data class GesturesIntroStep(
    val sentenceRes: Int,
    val buttonRes: Int,
)

/**
 * First-run cards on Home. The back arrow steps back, and on the first card it closes.
 */
@Composable
fun GesturesIntroOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps =
        listOf(
            GesturesIntroStep(R.string.gestures_intro_slide_right, R.string.gestures_intro_next),
            GesturesIntroStep(R.string.gestures_intro_slide_left, R.string.gestures_intro_next),
            GesturesIntroStep(R.string.gestures_intro_tap, R.string.gestures_intro_next),
            GesturesIntroStep(R.string.gestures_intro_hold, R.string.gestures_intro_got_it),
        )
    var step by rememberSaveable { mutableIntStateOf(0) }
    fun goBack() {
        if (step == 0) {
            onFinished()
        } else {
            step -= 1
        }
    }
    BackHandler(onBack = ::goBack)
    Box(
        modifier
            .fillMaxSize()
            .background(FocusColors.Live.Midnight.copy(alpha = 0.56f)),
    ) {
        BackArrow(
            label = BACK_LABEL,
            onClick = ::goBack,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        Surface(
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(CardRadius),
            color = FocusColors.Live.SurfaceHigh,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(Space),
            ) {
                Text(
                    text = stringResource(steps[step].sentenceRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FocusColors.Live.Text,
                    modifier = Modifier.semantics { heading() },
                )
                QuietButton(
                    label = stringResource(steps[step].buttonRes),
                    onClick = {
                        if (step >= steps.lastIndex) {
                            onFinished()
                        } else {
                            step += 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
