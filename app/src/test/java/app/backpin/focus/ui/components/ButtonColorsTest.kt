// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import app.backpin.focus.ui.theme.FocusColors
import app.backpin.focus.ui.theme.FocusStackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ButtonColorsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accentLabelsTakeTheBorderColorAndQuietStaysText() {
        assertEquals(FocusColors.Mint, accentLabelColor(AccentTone.Mint))
        assertEquals(FocusColors.Lavender, accentLabelColor(AccentTone.Lavender))
        assertEquals(FocusColors.Amber, accentLabelColor(AccentTone.Amber))
        assertEquals(FocusColors.Mint, choiceLabelColor(ChoiceTone.Mint))
        assertEquals(FocusColors.Lavender, choiceLabelColor(ChoiceTone.Lavender))
        assertEquals(FocusColors.Amber, choiceLabelColor(ChoiceTone.Amber))
        assertEquals(FocusColors.Text, choiceLabelColor(ChoiceTone.Quiet))
        assertEquals(FocusColors.Mint.copy(alpha = 0.38f), accentLabelColor(AccentTone.Mint, enabled = false))
        assertEquals(FocusColors.Amber.copy(alpha = 0.38f), choiceLabelColor(ChoiceTone.Amber, enabled = false))
        assertEquals(Color.Transparent, choiceContainerColor(ChoiceTone.Lavender, filled = false))
        assertEquals(FocusColors.LavenderContainer, choiceContainerColor(ChoiceTone.Lavender, filled = true))
        assertEquals(
            FocusColors.LavenderContainer.copy(alpha = 0.38f),
            choiceContainerColor(ChoiceTone.Lavender, filled = true, enabled = false),
        )
    }

    @Test
    fun renderedLabelsUseThatColorOverTheThemeTypography() {
        compose.setContent {
            FocusStackTheme {
                Column {
                    AccentButton("Done", AccentTone.Mint, onClick = {})
                    AccentButton("Interrupt", AccentTone.Lavender, onClick = {})
                    AccentButton("Add countdown", AccentTone.Amber, onClick = {})
                    AccentButton("Resume", AccentTone.Mint, onClick = {}, enabled = false)
                    QuietButton("Later", onClick = {})
                    CaptureChoice("Save to Inbox", onClick = {}, tone = ChoiceTone.Mint)
                    CaptureChoice("Interrupt now", onClick = {}, tone = ChoiceTone.Lavender)
                    CaptureChoice("Schedule", onClick = {}, tone = ChoiceTone.Amber)
                    CaptureChoice("Today", onClick = {})
                }
            }
        }
        assertEquals(FocusColors.Mint, labelColor("Done"))
        assertEquals(FocusColors.Lavender, labelColor("Interrupt"))
        assertEquals(FocusColors.Amber, labelColor("Add countdown"))
        assertEquals(FocusColors.Mint.copy(alpha = 0.38f), labelColor("Resume"))
        assertEquals(FocusColors.Text, labelColor("Later"))
        assertEquals(FocusColors.Mint, labelColor("Save to Inbox"))
        assertEquals(FocusColors.Lavender, labelColor("Interrupt now"))
        assertEquals(FocusColors.Amber, labelColor("Schedule"))
        assertEquals(FocusColors.Text, labelColor("Today"))
    }

    private fun labelColor(text: String): Color {
        val node = compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }
}
