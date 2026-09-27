package app.backpin.focus.ui.capture

import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeechResultTest {
    @Test
    fun speechResultLandsInTheTitle() {
        val draft = TitleDraft()
        draft.beginUtterance()
        draft.hear(recognizedText(results(SpeechRecognizer.RESULTS_RECOGNITION, "Buy milk")))
        assertEquals("Buy milk", draft.text)

        draft.beginUtterance()
        draft.hear(recognizedText(results(RecognizerIntent.EXTRA_RESULTS, "today")))
        assertEquals("Buy milk today", draft.text)
    }

    @Test
    fun partialHypothesesReplaceInsideOneUtterance() {
        val draft = TitleDraft()
        draft.edit("Buy")
        draft.beginUtterance()
        draft.hear("mil")
        draft.hear("milk")
        assertEquals("Buy milk", draft.text)
    }

    @Test
    fun blankHypothesisLeavesTheTitle() {
        val draft = TitleDraft()
        draft.edit("Buy milk")
        draft.beginUtterance()
        draft.hear(recognizedText(Bundle()))
        draft.hear(recognizedText(null))
        assertEquals("Buy milk", draft.text)
    }

    @Test
    fun unavailableAndDeniedAreVisibleNotes() {
        assertEquals(
            "Speech isn't available on this phone. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_CLIENT)),
        )
        assertEquals(
            "Microphone permission is off. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)),
        )
        assertTrue(speechProblemNote(speechProblem(SpeechRecognizer.ERROR_NO_MATCH)).isNotBlank())
    }

    private fun results(
        key: String,
        phrase: String,
    ): Bundle =
        Bundle().apply {
            putStringArrayList(key, arrayListOf(phrase))
        }
}
