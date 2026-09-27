package app.backpin.focus.ui.capture

import androidx.compose.runtime.mutableStateOf

/**
 * Title or fill-in text. Speech only changes [text]. It does not save.
 * [beginUtterance] snapshots the field so later partials replace one another.
 */
internal class TitleDraft {
    private val state = mutableStateOf("")
    private var utteranceBase = ""

    val text: String
        get() = state.value

    fun edit(value: String) {
        state.value = value
    }

    fun beginUtterance() {
        utteranceBase = state.value
    }

    fun hear(spoken: String) {
        state.value = appendSpokenText(utteranceBase, spoken)
    }
}

/**
 * Joins one spoken phrase onto text already in the field.
 *
 * Pass the field text from before this utterance. Partial hypotheses then replace
 * one another instead of stacking. A blank recognition returns [current] unchanged;
 * callers should not write that result over a later partial.
 */
internal fun appendSpokenText(
    current: String,
    spoken: String,
): String {
    val phrase = spoken.trim()
    if (phrase.isEmpty()) return current
    val base = current.trimEnd()
    if (base.isEmpty()) return phrase
    return "$base $phrase"
}
