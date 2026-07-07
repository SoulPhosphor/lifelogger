package com.datadragon.app.data

/**
 * Title-cases a string per the project's text house style (see CLAUDE.md):
 * capitalize every major word; keep minor words (a, an, the, and, or, of, to,
 * for, in, on, at, by) lowercase unless they are the first word.
 *
 * Only the first letter of each major word is changed — the rest of the word is
 * left exactly as typed, so acronyms and intentional mixed case (e.g. "PDF",
 * "iPhone") survive. Words are split on spaces only, so punctuation and
 * slashes inside a word are preserved.
 *
 * Used by the optional "auto capitalize" settings, which apply this to
 * user-entered field labels and dropdown/multiple options as they are created.
 */
object TitleCase {

    private val MINOR_WORDS = setOf(
        "a", "an", "the", "and", "or", "of", "to", "for", "in", "on", "at", "by",
    )

    fun apply(text: String): String {
        val words = text.split(" ")
        return words.mapIndexed { index, word ->
            when {
                word.isEmpty() -> word
                index != 0 && word.lowercase() in MINOR_WORDS -> word.lowercase()
                else -> word.replaceFirstChar { it.uppercaseChar() }
            }
        }.joinToString(" ")
    }

    /** Title-case each line of a multi-line block (e.g. an options list). */
    fun applyLines(text: String): String =
        text.split("\n").joinToString("\n") { apply(it) }
}
