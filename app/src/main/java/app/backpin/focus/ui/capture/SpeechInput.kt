// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.ContextCompat

/**
 * Mic state for one sheet. [K] names the field a phrase lands in.
 * Speech only fills that field's [TitleDraft]. It never saves.
 * [toggle] starts in-app recognition, asking for the microphone first, and falls back to
 * the phone's speech screen when that is worth trying. [note] says why nothing was heard.
 */
@Stable
internal class SpeechInput<K : Any> {
    var listening: Boolean by mutableStateOf(false)
        internal set
    var field: K? by mutableStateOf(null)
        internal set
    var note: String? by mutableStateOf(null)
        internal set

    internal var onToggle: (K) -> Unit = {}
    internal var onStop: () -> Unit = {}

    fun isListening(key: K): Boolean = listening && field == key

    fun toggle(key: K) = onToggle(key)

    fun stop() = onStop()
}

/**
 * Remembers one [SpeechInput]. [recognizer] replaces the device recognizer, and
 * [onSystemSpeech] replaces the system speech screen, in tests.
 */
@Composable
internal fun <K : Any> rememberSpeechInput(
    recognizer: UtteranceRecognizer?,
    onSystemSpeech: ((Intent, (String) -> Unit) -> Unit)?,
    draftFor: (K) -> TitleDraft,
): SpeechInput<K> {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val input = remember { SpeechInput<K>() }
    val speech = remember(context, recognizer) { recognizer ?: TitleSpeechRecognizer(context) }
    val alive = remember { mutableStateOf(true) }
    val draftOf = rememberUpdatedState(draftFor)
    var pendingListen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val pendingListenState = rememberUpdatedState(pendingListen)
    var pendingSystem by remember { mutableStateOf<K?>(null) }
    var pendingProblem by remember { mutableStateOf<SpeechProblem?>(null) }

    fun hear(
        field: K,
        spoken: String,
    ) {
        draftOf.value(field).hear(spoken)
    }

    fun shownProblem(problem: SpeechProblem?): SpeechProblem =
        if (problem != null && problem != SpeechProblem.Unavailable) problem else SpeechProblem.Unheard

    fun unavailableOr(problem: SpeechProblem?): SpeechProblem =
        when (problem) {
            null, SpeechProblem.Unavailable -> SpeechProblem.Unavailable
            else -> problem
        }

    val systemSpeechLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!alive.value) return@rememberLauncherForActivityResult
            val field = pendingSystem ?: return@rememberLauncherForActivityResult
            val problem = pendingProblem
            pendingSystem = null
            pendingProblem = null
            input.listening = false
            val spoken =
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
            if (spoken.isNotEmpty()) {
                hear(field, spoken)
            } else {
                input.note = speechProblemNote(shownProblem(problem))
            }
        }

    fun offerSystemSpeech(
        field: K,
        problem: SpeechProblem?,
    ) {
        draftOf.value(field).beginUtterance()
        input.field = field
        input.listening = false
        speech.cancel()
        val deliver: (String) -> Unit = { spoken ->
            val phrase = spoken.trim()
            if (phrase.isNotEmpty()) {
                hear(field, phrase)
                input.note = null
            } else {
                input.note = speechProblemNote(shownProblem(problem))
            }
        }
        val hook = onSystemSpeech
        if (hook != null) {
            hook(systemSpeechIntent(), deliver)
            return
        }
        if (!canLaunchSystemSpeech(context.hostContext())) {
            input.note = speechProblemNote(unavailableOr(problem))
            return
        }
        pendingSystem = field
        pendingProblem = problem
        try {
            systemSpeechLauncher.launch(systemSpeechIntent())
        } catch (_: ActivityNotFoundException) {
            pendingSystem = null
            pendingProblem = null
            input.note = speechProblemNote(SpeechProblem.Unavailable)
        } catch (_: IllegalStateException) {
            pendingSystem = null
            pendingProblem = null
            input.note = speechProblemNote(unavailableOr(problem))
        }
    }

    fun startUtterance(field: K) {
        draftOf.value(field).beginUtterance()
        focusManager.clearFocus(force = true)
        input.field = field
        input.note = null
        input.listening = true
        val started =
            speech.listen(
                onPartial = { spoken -> hear(field, spoken) },
                onFinished = { spoken ->
                    hear(field, spoken)
                    input.listening = false
                },
                onProblem = { problem ->
                    input.listening = false
                    if (fallsBackToSystem(problem)) {
                        offerSystemSpeech(field, problem)
                    } else {
                        input.note = speechProblemNote(problem)
                    }
                },
            )
        if (!started) {
            input.listening = false
            offerSystemSpeech(field, SpeechProblem.NoService)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!alive.value) return@rememberLauncherForActivityResult
            val start = pendingListenState.value
            pendingListen = null
            if (granted) {
                start?.invoke()
            } else {
                input.note = speechProblemNote(SpeechProblem.Denied)
            }
        }

    fun stopListening() {
        speech.cancel()
        input.listening = false
    }

    fun toggle(field: K) {
        if (input.isListening(field)) {
            stopListening()
            return
        }
        if (!speech.available()) {
            offerSystemSpeech(field, problem = null)
            return
        }
        val start = { startUtterance(field) }
        val granted =
            ContextCompat.checkSelfPermission(
                context.hostContext(),
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            start()
        } else {
            pendingListen = start
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (_: IllegalStateException) {
                pendingListen = null
                input.note = speechProblemNote(SpeechProblem.Denied)
            }
        }
    }

    input.onToggle = ::toggle
    input.onStop = ::stopListening

    DisposableEffect(speech) {
        onDispose {
            alive.value = false
            speech.cancel()
        }
    }
    return input
}
