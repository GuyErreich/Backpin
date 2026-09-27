package app.backpin.focus.ui.capture

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal enum class SpeechProblem {
    Unavailable,
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
        else -> SpeechProblem.Unavailable
    }

internal fun speechProblemNote(problem: SpeechProblem): String =
    when (problem) {
        SpeechProblem.Unavailable -> "Speech isn't available on this phone. You can still type."
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
