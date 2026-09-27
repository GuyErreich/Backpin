package app.backpin.focus.ui.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * One utterance from the device [SpeechRecognizer].
 * [RecognizerIntent.EXTRA_PREFER_OFFLINE] is set. If the offline pack is missing,
 * the device recognizer may use the network. This app does not ship a model or a speech account.
 * Call [listen] and [cancel] on the main thread. Results are delivered on the main thread.
 */
internal class TitleSpeechRecognizer(
    private val context: Context,
) : UtteranceRecognizer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

    override fun available(): Boolean =
        try {
            SpeechRecognizer.isRecognitionAvailable(context.hostContext())
        } catch (_: RuntimeException) {
            false
        }

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean {
        cancel()
        val host = context.hostContext()
        if (!available()) return false
        val created = createRecognizer(host) ?: return false
        val session = ++generation
        recognizer = created

        fun finish(problem: SpeechProblem?, hypothesis: String) {
            if (session != generation) return
            generation += 1
            if (problem != null) {
                onProblem(problem)
            } else {
                onFinished(hypothesis)
            }
            release(created)
        }

        created.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    onMain { finish(speechProblem(error), hypothesis = "") }
                }

                override fun onResults(results: Bundle?) {
                    val text = recognizedText(results)
                    onMain {
                        if (text.isBlank()) {
                            finish(SpeechProblem.Unheard, hypothesis = "")
                        } else {
                            finish(problem = null, hypothesis = text)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = recognizedText(partialResults)
                    if (text.isBlank()) return
                    onMain {
                        if (session == generation) onPartial(text)
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        return try {
            created.startListening(recognizeIntent(host))
            true
        } catch (_: RuntimeException) {
            if (session == generation) generation += 1
            release(created)
            false
        }
    }

    override fun cancel() {
        onMain {
            generation += 1
            val current = recognizer ?: return@onMain
            recognizer = null
            try {
                current.setRecognitionListener(null)
                current.cancel()
                current.destroy()
            } catch (_: RuntimeException) {
                // The recognizer was already released with this utterance.
            }
        }
    }

    private fun createRecognizer(host: Context): SpeechRecognizer? =
        try {
            val component = recognitionComponent(host)
            if (component != null) {
                SpeechRecognizer.createSpeechRecognizer(host, component)
            } else {
                SpeechRecognizer.createSpeechRecognizer(host)
            }
        } catch (_: RuntimeException) {
            null
        }

    private fun recognitionComponent(host: Context): ComponentName? {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        val service =
            host.packageManager
                .queryIntentServices(intent, 0)
                .firstOrNull()
                ?.serviceInfo
                ?: return null
        return ComponentName(service.packageName, service.name)
    }

    private fun release(created: SpeechRecognizer) {
        if (recognizer !== created) return
        recognizer = null
        try {
            created.setRecognitionListener(null)
            created.destroy()
        } catch (_: RuntimeException) {
            // destroy() is main-thread only; a second release is ignored.
        }
    }

    private fun recognizeIntent(host: Context): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, host.packageName)
        }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
