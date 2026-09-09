package com.datadragon.app.data

/**
 * Text matching for the Idea Log search box.
 *
 * The query is always treated as literal text: it is escaped before any regex is
 * built, so a search for "a.b" or "(draft)" looks for exactly that and can never
 * be interpreted as regex syntax.
 *
 * With both switches off — the default — matching is case-insensitive and finds
 * the query anywhere inside a value, so "drag" matches "dragon", "drag" and
 * "DataDragon".
 */
object IdeaSearch {

    /**
     * A prepared query. Building the matcher once per search keeps the per-idea
     * work to a plain match, and a blank query matches nothing rather than
     * everything.
     */
    class Matcher internal constructor(
        private val query: String,
        private val wholeWord: Boolean,
        private val matchCase: Boolean,
    ) {
        // Whole Word needs boundaries that count digits and underscores as part
        // of a word, so "drag" doesn't match inside "dragon" but a whole phrase
        // like "data dragon" still matches. \b is unreliable when the query
        // starts or ends with punctuation, so the boundaries are spelled out as
        // "not preceded/followed by an alphanumeric".
        private val regex: Regex? = if (wholeWord) {
            val options = if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(
                "(?<![\\p{L}\\p{N}_])" + Regex.escape(query) + "(?![\\p{L}\\p{N}_])",
                options,
            )
        } else {
            null
        }

        fun matches(text: String): Boolean {
            val pattern = regex
            return if (pattern != null) {
                pattern.containsMatchIn(text)
            } else {
                text.contains(query, ignoreCase = !matchCase)
            }
        }

        /** True when any of [texts] matches. */
        fun matchesAny(texts: List<String>): Boolean = texts.any { matches(it) }
    }

    /**
     * Prepare [query] for matching, or return null when it is blank — a blank
     * search is not run at all rather than matching every idea.
     */
    fun matcher(query: String, wholeWord: Boolean, matchCase: Boolean): Matcher? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        return Matcher(trimmed, wholeWord, matchCase)
    }
}
