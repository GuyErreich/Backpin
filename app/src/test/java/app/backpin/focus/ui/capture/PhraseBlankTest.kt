package app.backpin.focus.ui.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhraseBlankTest {
    @Test
    fun fillsOnlyTheFirstBlank() {
        assertEquals("Talk to Dana", fillPhraseBlank("Talk to <name>", "Dana"))
        assertEquals("Talk to Dana", fillPhraseBlank("Talk to <name>", "  Dana  "))
        assertEquals("Call Dana about <topic>", fillPhraseBlank("Call <name> about <topic>", "Dana"))
        assertNull(fillPhraseBlank("Talk to <name>", ""))
        assertNull(fillPhraseBlank("Talk to <name>", "   "))
        assertNull(fillPhraseBlank("Talk to my wife", "Dana"))
    }

    @Test
    fun chipShowsAnEllipsisForTheFirstBlankOnly() {
        assertEquals("Talk to …", phraseChipLabel("Talk to <name>"))
        assertEquals("Talk to my wife", phraseChipLabel("Talk to my wife"))
        assertEquals("Call … about <topic>", phraseChipLabel("Call <name> about <topic>"))
        assertEquals("name", phrasePrompt("Talk to <name>"))
        assertNull(phrasePrompt("Talk to my wife"))
    }

    @Test
    fun chipAsksWhereAFinishedPhraseGoes() {
        assertEquals(PhraseChipAction.AskPlace("Talk to my wife"), phraseChipAction("Talk to my wife"))
    }

    @Test
    fun chipAsksForTheBlankBeforeAnyPlace() {
        assertEquals(PhraseChipAction.AskFill, phraseChipAction("Talk to <name>"))
    }
}
