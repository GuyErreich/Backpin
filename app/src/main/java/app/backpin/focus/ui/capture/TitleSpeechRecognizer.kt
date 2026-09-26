package app.backpin.focus.ui.capture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal enum class SpeechStop {
    Finished,
    Silent,
    Permission,
}

/**
 * One utterance from the device [SpeechRecognizer].
 * [RecognizerIntent.EXTRA_PREFER_OFFLINE] is set. If the offline pack is missing,
 * the device recognizer may use the network. This app does not ship a model or a speech account.
 * Call [listen] and [cancel] on the main thread.
 */
internal class TitleSpeechRecognizer(
    private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts a single utterance. [onPartial] and [onFinished] receive the hypothesis
     * for this utterance, not a delta. Returns false when this phone has no recognizer.
     */
    fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onStop: (SpeechStop) -> Unit,
    ): Boolean {
        cancel()
        if (!isAvailable()) return false
        val created =
            try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (_: RuntimeException) {
                return false
            }
        recognizer = created
        var open = true

        fun finish(
            hypothesis: String?,
            stop: SpeechStop,
        ) {
            if (!open) return
            open = false
            if (hypothesis != null) onFinished(hypothesis)
            onStop(stop)
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
                    val stop =
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            SpeechStop.Permission
                        } else {
                            SpeechStop.Silent
                        }
                    finish(hypothesis = null, stop = stop)
                }

                override fun onResults(results: Bundle?) {
                    finish(hypothesis(results), SpeechStop.Finished)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!open) return
                    onPartial(hypothesis(partialResults))
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        return try {
            created.startListening(recognizeIntent())
            true
        } catch (_: RuntimeException) {
            open = false
            release(created)
            false
        }
    }

    fun cancel() {
        val current = recognizer ?: return
        recognizer = null
        current.setRecognitionListener(null)
        current.cancel()
        current.destroy()
    }

    private fun release(created: SpeechRecognizer) {
        if (recognizer !== created) return
        recognizer = null
        created.setRecognitionListener(null)
        created.destroy()
    }

    private fun recognizeIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun hypothesis(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
}
