// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.backpin.focus.R
import app.backpin.focus.ui.theme.ButtonMinHeight
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AddFabLabelAlignmentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addAndCountdownLabelsSitInTheMiddleOfThePill() {
        val labels = listOf(R.string.action_add, R.string.action_add_countdown)
        compose.setContent {
            FocusStackTheme {
                Column {
                    labels.forEach { label ->
                        AddFab(onClick = {}, labelRes = label)
                    }
                }
            }
        }
        labels.forEach { res ->
            val label = compose.activity.getString(res)
            val pill = compose.onNodeWithText(label).getBoundsInRoot()
            val text = compose.onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot()
            val pillMidY = (pill.top + pill.bottom) / 2
            val textMidY = (text.top + text.bottom) / 2
            val pillMidX = (pill.left + pill.right) / 2
            val textMidX = (text.left + text.right) / 2
            val pillHeight = pill.bottom - pill.top
            val textHeight = text.bottom - text.top
            assertTrue("$label pill is at least $ButtonMinHeight", pillHeight >= ButtonMinHeight - 1.dp)
            assertTrue(
                "$label text should be shorter than the pill (pill $pillHeight, text $textHeight)",
                textHeight < pillHeight - 8.dp,
            )
            assertTrue(
                "$label vertical center $textMidY vs pill $pillMidY",
                abs(textMidY.value - pillMidY.value) < 1f,
            )
            assertTrue(
                "$label horizontal center $textMidX vs pill $pillMidX",
                abs(textMidX.value - pillMidX.value) < 1f,
            )
        }
    }
}
