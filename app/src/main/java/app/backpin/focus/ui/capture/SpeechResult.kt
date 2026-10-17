// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

internal enum class SpeechProblem {
    /** Neither the in-app recognizer nor the system speech screen exists. */
    Unavailable,

    /** The recognizer service failed, or the phone has no speech service. */
    NoService,

    /** The requested speech language is not installed. */
    LanguageMissing,

    /** Recognition needed a network and could not reach one. */
    NoNetwork,

    Denied,
    Unheard,
}

/** Maps a [SpeechRecognizer] error to a note the sheet can show. Never silent. */
internal fun speechProblem(errorCode: Int): SpeechProblem =
    when (errorCode) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechProblem.Denied
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> SpeechProblem.Unheard
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> SpeechProblem.LanguageMissing
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> SpeechProblem.NoNetwork
        else -> SpeechProblem.NoService
    }

/**
 * Language, server, and client failures are worth one more try without
 * [RecognizerIntent.EXTRA_LANGUAGE]. Other errors are not.
 */
internal fun retriesWithoutLanguage(errorCode: Int): Boolean =
    when (errorCode) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        SpeechRecognizer.ERROR_CLIENT,
        -> true
        else -> false
    }

/** Permission denied and a missed utterance stay in the sheet. Everything else can open the system screen. */
internal fun fallsBackToSystem(problem: SpeechProblem): Boolean =
    problem != SpeechProblem.Denied && problem != SpeechProblem.Unheard

internal fun speechProblemNote(problem: SpeechProblem): String =
    when (problem) {
        SpeechProblem.Unavailable -> "Speech isn't available on this phone. You can still type."
        SpeechProblem.NoService -> "No speech service on this phone. You can still type."
        SpeechProblem.LanguageMissing -> "Speech language isn't installed. You can still type."
        SpeechProblem.NoNetwork -> "No network for speech. You can still type."
        SpeechProblem.Denied -> "Microphone permission is off. You can still type."
        SpeechProblem.Unheard -> "Didn't catch that. You can still type."
    }

/**
 * First non-blank hypothesis from a recognition bundle.
 * Devices report the phrase under [SpeechRecognizer.RESULTS_RECOGNITION]
 * or [RecognizerIntent.EXTRA_RESULTS].
 */
internal fun recognizedText(results: Bundle?): String {
    if (results == null) return ""
    val keys = listOf(SpeechRecognizer.RESULTS_RECOGNITION, RecognizerIntent.EXTRA_RESULTS)
    for (key in keys) {
        val phrase = results.getStringArrayList(key)?.firstOrNull { it.isNotBlank() }
        if (phrase != null) return phrase.trim()
    }
    return ""
}

/** Activity context when this wrapper sits on a dialog. Speech and permission checks need it. */
internal fun Context.hostContext(): Context {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return this
}

/**
 * In-app recognition intent. [includeLanguage] is set only when the recognizer
 * reports support for [languageTag]. Offline recognition is not forced.
 */
internal fun recognizeSpeechIntent(
    packageName: String,
    includeLanguage: Boolean,
    languageTag: String = Locale.getDefault().toLanguageTag(),
): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        if (includeLanguage && languageTag.isNotBlank()) {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        }
    }

/** The phone's own speech screen. It chooses the language. This app does not save from it. */
internal fun systemSpeechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the title")
    }

internal fun canLaunchSystemSpeech(context: Context): Boolean =
    try {
        context.packageManager.resolveActivity(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY,
        ) != null
    } catch (_: RuntimeException) {
        false
    }

/** True when [tag] is one of [supportedTags], or they share a language prefix. */
internal fun languageTagSupported(
    supportedTags: Collection<String>,
    tag: String,
): Boolean {
    val wanted = tag.trim().lowercase()
    if (wanted.isEmpty()) return false
    return supportedTags.any { supported ->
        val candidate = supported.trim().lowercase()
        candidate.isNotEmpty() &&
            (
                candidate == wanted ||
                    wanted.startsWith("$candidate-") ||
                    candidate.startsWith("$wanted-")
            )
    }
}

/**
 * One recognition start, plus a single retry that drops [RecognizerIntent.EXTRA_LANGUAGE]
 * after a language, server, or client error.
 */
internal class RecognitionAttempt(
    private val includeLanguage: Boolean,
    private val packageName: String,
    private val languageTag: String = Locale.getDefault().toLanguageTag(),
    private val start: (Intent, RecognitionListener) -> Boolean,
) {
    private var retried = false

    fun begin(outer: RecognitionListener): Boolean {
        val sendLanguage = includeLanguage && languageTag.isNotBlank()
        val listener = if (sendLanguage) wrapping(outer) else outer
        return start(recognizeSpeechIntent(packageName, sendLanguage, languageTag), listener)
    }

    private fun wrapping(outer: RecognitionListener): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = outer.onReadyForSpeech(params)

            override fun onBeginningOfSpeech() = outer.onBeginningOfSpeech()

            override fun onRmsChanged(rmsdB: Float) = outer.onRmsChanged(rmsdB)

            override fun onBufferReceived(buffer: ByteArray?) = outer.onBufferReceived(buffer)

            override fun onEndOfSpeech() = outer.onEndOfSpeech()

            override fun onError(error: Int) {
                if (!retried && retriesWithoutLanguage(error)) {
                    retried = true
                    val again = recognizeSpeechIntent(packageName, includeLanguage = false, languageTag)
                    if (!start(again, outer)) outer.onError(error)
                } else {
                    outer.onError(error)
                }
            }

            override fun onResults(results: Bundle?) = outer.onResults(results)

            override fun onPartialResults(partialResults: Bundle?) = outer.onPartialResults(partialResults)

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = outer.onEvent(eventType, params)
        }
}

/** One utterance. [listen] and [cancel] run on the main thread. */
internal interface UtteranceRecognizer {
    fun available(): Boolean

    fun listen(
        onPartial: (String) -> Unit,
        onFinished: (String) -> Unit,
        onProblem: (SpeechProblem) -> Unit,
    ): Boolean

    fun cancel()
}
