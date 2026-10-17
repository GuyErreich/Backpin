// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * One utterance from the device [SpeechRecognizer], using the system default service.
 * [RecognizerIntent.EXTRA_LANGUAGE] is sent only when that service supports the language.
 * A language, server, or client error retries once without it.
 * This app does not ship a model or a speech account.
 * Call [listen] and [cancel] on the main thread. Results are delivered on the main thread.
 */
internal class TitleSpeechRecognizer(
    private val context: Context,
    private val ears: (Context) -> SpeechEar? = ::androidEar,
    languageIncluded: (() -> Boolean)? = null,
    private val recognitionAvailable: () -> Boolean = {
        try {
            SpeechRecognizer.isRecognitionAvailable(context.hostContext())
        } catch (_: RuntimeException) {
            false
        }
    },
) : UtteranceRecognizer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val includesLanguage: () -> Boolean
    private var ear: SpeechEar? = null
    private var generation = 0

    init {
        val probe = if (languageIncluded == null) LanguageProbe(context) else null
        includesLanguage = languageIncluded ?: { probe?.include == true }
    }

    override fun available(): Boolean = recognitionAvailable()

    override fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean {
        cancel()
        val host = context.hostContext()
        if (!available()) return false
        val created = ears(host) ?: return false
        val session = ++generation
        ear = created

        fun finish(
            problem: SpeechProblem?,
            hypothesis: String,
        ) {
            if (session != generation) return
            generation += 1
            if (problem != null) {
                onProblem(problem)
            } else {
                onFinished(hypothesis)
            }
            release(created)
        }

        val outer =
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
            }

        val started =
            try {
                RecognitionAttempt(
                    includeLanguage = includesLanguage(),
                    packageName = host.packageName,
                    start = { intent, listener -> created.start(intent, listener) },
                ).begin(outer)
            } catch (_: RuntimeException) {
                false
            }
        if (!started) {
            if (session == generation) generation += 1
            release(created)
            return false
        }
        return true
    }

    override fun cancel() {
        onMain {
            generation += 1
            val current = ear ?: return@onMain
            ear = null
            current.stop()
        }
    }

    private fun release(created: SpeechEar) {
        if (ear !== created) return
        ear = null
        created.stop()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}

/** One system [SpeechRecognizer]. [start] may be called again after an error, before [stop]. */
internal interface SpeechEar {
    fun start(
        intent: Intent,
        listener: RecognitionListener,
    ): Boolean

    fun stop()
}

private fun androidEar(host: Context): SpeechEar? =
    try {
        AndroidSpeechEar(SpeechRecognizer.createSpeechRecognizer(host))
    } catch (_: RuntimeException) {
        null
    }

private class AndroidSpeechEar(
    private val recognizer: SpeechRecognizer,
) : SpeechEar {
    override fun start(
        intent: Intent,
        listener: RecognitionListener,
    ): Boolean =
        try {
            recognizer.setRecognitionListener(listener)
            recognizer.startListening(intent)
            true
        } catch (_: RuntimeException) {
            false
        }

    override fun stop() {
        try {
            recognizer.setRecognitionListener(null)
            recognizer.cancel()
            recognizer.destroy()
        } catch (_: RuntimeException) {
            // destroy() is main-thread only; a second release is ignored.
        }
    }
}

/**
 * Asks the default recognizer whether the current language tag is supported.
 * Until the answer arrives, language is omitted. Below API 33 there is no check.
 */
private class LanguageProbe(
    context: Context,
) {
    @Volatile
    var include: Boolean = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            probe(context.hostContext())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun probe(host: Context) {
        val recognizer =
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(host)) return
                SpeechRecognizer.createSpeechRecognizer(host)
            } catch (_: RuntimeException) {
                return
            }
        val intent = recognizeSpeechIntent(host.packageName, includeLanguage = false)
        try {
            recognizer.checkRecognitionSupport(
                intent,
                { command -> command.run() },
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val tags =
                            recognitionSupport.installedOnDeviceLanguages +
                                recognitionSupport.supportedOnDeviceLanguages +
                                recognitionSupport.onlineLanguages
                        include = languageTagSupported(tags, Locale.getDefault().toLanguageTag())
                        destroy(recognizer)
                    }

                    override fun onError(error: Int) {
                        include = false
                        destroy(recognizer)
                    }
                },
            )
        } catch (_: RuntimeException) {
            destroy(recognizer)
        }
    }

    private fun destroy(recognizer: SpeechRecognizer) {
        val release = {
            try {
                recognizer.destroy()
            } catch (_: RuntimeException) {
                // Already released.
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            release()
        } else {
            Handler(Looper.getMainLooper()).post(release)
        }
    }
}
