package app.backpin.focus.ui.capture

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
