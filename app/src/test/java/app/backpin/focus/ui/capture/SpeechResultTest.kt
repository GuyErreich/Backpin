package app.backpin.focus.ui.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    fun errorCodesBecomeSpecificNotes() {
        assertEquals(
            "No speech service on this phone. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_CLIENT)),
        )
        assertEquals(
            "No speech service on this phone. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_SERVER)),
        )
        assertEquals(
            "Speech language isn't installed. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)),
        )
        assertEquals(
            "Speech language isn't installed. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)),
        )
        assertEquals(
            "No network for speech. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_NETWORK)),
        )
        assertEquals(
            "No network for speech. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)),
        )
        assertEquals(
            "Microphone permission is off. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)),
        )
        assertEquals(
            "Didn't catch that. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_NO_MATCH)),
        )
        assertEquals(
            "Didn't catch that. You can still type.",
            speechProblemNote(speechProblem(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)),
        )
        assertEquals(
            "Speech isn't available on this phone. You can still type.",
            speechProblemNote(SpeechProblem.Unavailable),
        )
        assertTrue(fallsBackToSystem(speechProblem(SpeechRecognizer.ERROR_NETWORK)))
        assertFalse(fallsBackToSystem(speechProblem(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)))
        assertFalse(fallsBackToSystem(speechProblem(SpeechRecognizer.ERROR_NO_MATCH)))
    }

    @Test
    fun retryDropsLanguageExtra() {
        val started = listeningEar(includeLanguage = true)
        assertTrue(started.recognizer.listen(onPartial = {}, onFinished = {}, onProblem = started.onProblem))
        assertEquals(1, started.intents.size)
        assertTrue(started.intents[0].hasExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertFalse(started.intents[0].hasExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE))
        started.listeners[0].onError(SpeechRecognizer.ERROR_CLIENT)
        assertEquals(2, started.intents.size)
        assertFalse(started.intents[1].hasExtra(RecognizerIntent.EXTRA_LANGUAGE))
        started.listeners[1].onError(SpeechRecognizer.ERROR_SERVER)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SpeechProblem.NoService, started.problem)
        assertEquals(2, started.intents.size)
    }

    @Test
    fun networkErrorDoesNotRetry() {
        val started = listeningEar(includeLanguage = true)
        assertTrue(started.recognizer.listen(onPartial = {}, onFinished = {}, onProblem = started.onProblem))
        started.listeners[0].onError(SpeechRecognizer.ERROR_NETWORK)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, started.intents.size)
        assertEquals(SpeechProblem.NoNetwork, started.problem)
    }

    private fun listeningEar(includeLanguage: Boolean): StartedEar {
        val intents = mutableListOf<Intent>()
        val listeners = mutableListOf<RecognitionListener>()
        val ear =
            object : SpeechEar {
                override fun start(
                    intent: Intent,
                    listener: RecognitionListener,
                ): Boolean {
                    intents += Intent(intent)
                    listeners += listener
                    return true
                }

                override fun stop() = Unit
            }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recognizer =
            TitleSpeechRecognizer(
                context = context,
                ears = { ear },
                languageIncluded = { includeLanguage },
                recognitionAvailable = { true },
            )
        return StartedEar(recognizer, intents, listeners)
    }

    private class StartedEar(
        val recognizer: TitleSpeechRecognizer,
        val intents: MutableList<Intent>,
        val listeners: MutableList<RecognitionListener>,
    ) {
        var problem: SpeechProblem? = null
        val onProblem: (SpeechProblem) -> Unit = { problem = it }
    }

    private fun results(
        key: String,
        phrase: String,
    ): Bundle =
        Bundle().apply {
            putStringArrayList(key, arrayListOf(phrase))
        }
}
