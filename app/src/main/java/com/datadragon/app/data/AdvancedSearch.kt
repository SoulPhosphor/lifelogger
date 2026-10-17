package com.datadragon.app.data

import java.text.Normalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Temporary view criteria. No search operation writes records or preferences. */
data class AdvancedSearchCriteria(
    val query: String = "",
    val wholeWords: Boolean = false,
    val matchCase: Boolean = false,
    /** null means every text field and Notes; otherwise a permanent field id or NOTES_KEY. */
    val textFieldId: String? = null,
    val answerFieldId: String? = null,
    val answer: String? = null,
) {
    val active: Boolean get() = query.isNotBlank() || (answerFieldId != null && answer != null)
}

data class SearchAnswer(val value: String, val label: String)

object AdvancedSearch {
    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC)

    /** Literal Unicode text, including combining marks as word constituents. */
    fun matcher(criteria: AdvancedSearchCriteria): (String) -> Boolean {
        val query = normalized(criteria.query.trim())
        if (query.isEmpty()) return { true }
        val options = if (criteria.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val literal = Regex.escape(query)
        val pattern = Regex(
            if (criteria.wholeWords) "(?<![\\p{L}\\p{M}\\p{N}_])$literal(?![\\p{L}\\p{M}\\p{N}_])" else literal,
            options,
        )
        return { pattern.containsMatchIn(normalized(it)) }
    }

    fun textFields(fields: List<FieldDef>) = fields.filter { it.type == FieldType.TEXT || it.type == FieldType.MULTILINE }

    fun answerFields(fields: List<FieldDef>) = fields.filter {
        it.type.hasChoices || it.type == FieldType.YESNO || it.type == FieldType.SCALE
    }

    fun answers(field: FieldDef): List<SearchAnswer> = when (field.type) {
        FieldType.DROPDOWN, FieldType.MULTIPLE -> field.choices.map {
            SearchAnswer(it.id, if (it.retired) "${it.label} (Retired)" else it.label)
        }
        // Include Unknown even after it stops being offered for new entries.
        FieldType.YESNO -> listOf("Yes", "No", "Unknown").map { SearchAnswer(it, it) }
        FieldType.SCALE -> ((field.from ?: 1)..(field.to ?: 10)).map { SearchAnswer(it.toString(), it.toString()) }
        else -> emptyList()
    }

    fun forms(entries: List<LogEntry>, fields: List<FieldDef>, criteria: AdvancedSearchCriteria): List<LogEntry> {
        if (!criteria.active) return entries
        val matches = matcher(criteria)
        val textIds = if (criteria.textFieldId == null) {
            textFields(fields).map { it.id } + EntryValues.NOTES_KEY
        } else listOfNotNull(criteria.textFieldId)
        return entries.filter { entry ->
            val values = EntryValues.decode(entry.valuesJson)
            val textMatches = criteria.query.isBlank() || textIds.any { id ->
                (values[id] as? JsonPrimitive)?.contentOrNull?.let(matches) == true
            }
            val answerMatches = if (criteria.answerFieldId == null || criteria.answer == null) true else {
                when (val raw = values[criteria.answerFieldId]) {
                    is JsonArray -> raw.any { (it as? JsonPrimitive)?.contentOrNull == criteria.answer }
                    is JsonPrimitive -> raw.contentOrNull == criteria.answer
                    else -> false
                }
            }
            textMatches && answerMatches
        }
    }

    fun days(cards: List<DailyList>, items: Map<Long, List<DailyListItem>>, criteria: AdvancedSearchCriteria): List<DailyList> {
        if (!criteria.active) return cards
        val matches = matcher(criteria)
        return cards.filter { matches(it.title) || items[it.id].orEmpty().any { item -> matches(item.text) } }
    }

    /** Sorting alone never creates a second header line. Counts are cards, never occurrences. */
    fun summary(criteria: AdvancedSearchCriteria, count: Int, fields: List<FieldDef> = emptyList()): String? {
        if (!criteria.active) return null
        val parts = mutableListOf<String>()
        if (criteria.query.isNotBlank()) {
            val scope = criteria.textFieldId?.let { id ->
                if (id == EntryValues.NOTES_KEY) "Notes" else fields.firstOrNull { it.id == id }?.label
            }
            parts += listOfNotNull(scope, "\"${criteria.query.trim()}\"").joinToString(": ")
        }
        fields.firstOrNull { it.id == criteria.answerFieldId }?.let { field ->
            val answer = answers(field).firstOrNull { it.value == criteria.answer }?.label
            if (answer != null) parts += "${field.label}: $answer"
        }
        parts += "$count matching entries"
        return parts.joinToString(" · ")
    }
}
