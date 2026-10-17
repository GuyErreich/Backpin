// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    @Test
    fun appendsASpokenPhraseAfterExistingText() {
        assertEquals("Buy milk", appendSpokenText("", "Buy milk"))
        assertEquals("Buy milk", appendSpokenText("Buy", "milk"))
        assertEquals("Buy milk", appendSpokenText("Buy ", " milk "))
        assertEquals("Buy milk today", appendSpokenText("Buy milk", "today"))
    }

    @Test
    fun blankRecognitionLeavesTheFieldUnchanged() {
        assertEquals("Buy milk", appendSpokenText("Buy milk", ""))
        assertEquals("Buy milk", appendSpokenText("Buy milk", "   "))
        assertEquals("", appendSpokenText("", "  "))
    }

    @Test
    fun partialHypothesesReplaceWhenAppliedToTheSameBase() {
        assertEquals("Buy mil", appendSpokenText("Buy", "mil"))
        assertEquals("Buy milk", appendSpokenText("Buy", "milk"))
    }
}
