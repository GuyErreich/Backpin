// SPDX-License-Identifier: GPL-3.0-or-later
package app.backpin.focus.ui.capture

private val firstBlank = Regex("<([^<>]*)>")

/** Chip label. The first angle-bracket blank is an ellipsis; later blanks stay written. */
internal fun phraseChipLabel(template: String): String {
    val match = firstBlank.find(template) ?: return template
    return template.replaceRange(match.range, "…")
}

/**
 * Prompt for the first blank, taken from the word inside the brackets.
 * Null when the phrase is finished.
 */
internal fun phrasePrompt(template: String): String? {
    val match = firstBlank.find(template) ?: return null
    return match.groupValues[1].trim().ifEmpty { "word" }
}

/**
 * Replaces only the first angle-bracket blank with [word].
 * A blank word, or a finished phrase, returns null so no task is created.
 * Any later blank stays in the title as written.
 */
internal fun fillPhraseBlank(
    template: String,
    word: String,
): String? {
    val match = firstBlank.find(template) ?: return null
    val filled = word.trim()
    if (filled.isEmpty()) return null
    return template.replaceRange(match.range, filled)
}

/**
 * What a phrase chip does before any task exists.
 * A finished phrase fills the title. A blank asks for that word first.
 */
internal sealed interface PhraseChipAction {
    data class AskPlace(
        val title: String,
    ) : PhraseChipAction

    data object AskFill : PhraseChipAction
}

internal fun phraseChipAction(template: String): PhraseChipAction {
    if (phrasePrompt(template) == null) return PhraseChipAction.AskPlace(template)
    return PhraseChipAction.AskFill
}
