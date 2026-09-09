package com.datadragon.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * Owns the shape of [IdeaEntry.valuesJson] and how stored idea values read back.
 *
 * Values are keyed by [IdeaFieldDef.id] — the field's permanent identity — so a
 * later rename or reorder never disconnects stored data from its field. The
 * storage forms themselves are the shared ones from [EntryValues]: a Tags field
 * is a JSON array of strings, everything else a JSON string in the same
 * machine-readable form a form entry uses. Display formatting happens here, at
 * read time, never at storage time.
 */
object IdeaValues {

    fun encode(values: Map<String, JsonElement>): String = EntryValues.encode(values)

    fun decode(valuesJson: String): JsonObject = EntryValues.decode(valuesJson)

    /** The raw stored string for any single-valued field, or null if absent. */
    fun rawValue(values: JsonObject, field: IdeaFieldDef): String? =
        (values[field.id] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }

    /** The saved tags of a Tags field, in the order they were added. */
    fun tags(values: JsonObject, field: IdeaFieldDef): List<String> =
        (values[field.id] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    /** True when this field has something saved against it. */
    fun hasValue(values: JsonObject, field: IdeaFieldDef): Boolean =
        if (field.type == FieldType.TAGS) {
            tags(values, field).isNotEmpty()
        } else {
            rawValue(values, field) != null
        }

    /**
     * A human-readable rendering of one field's stored value, or null when the
     * field has no value. Multiline and webpage values come back verbatim —
     * their own on-screen treatment (truncation, the open button) is applied by
     * the composable that draws them, not here.
     */
    fun displayValue(values: JsonObject, field: IdeaFieldDef): String? = when (field.type) {
        FieldType.TAGS -> tags(values, field).takeIf { it.isNotEmpty() }?.joinToString(", ")
        FieldType.DATE -> reformat(rawValue(values, field)) {
            EntryValues.displayDate(LocalDate.parse(it, EntryValues.DATE_STORAGE))
        }
        FieldType.TIME -> reformat(rawValue(values, field)) {
            EntryValues.displayTime(LocalTime.parse(it, EntryValues.TIME_STORAGE))
        }
        FieldType.DATETIME -> reformat(rawValue(values, field)) {
            EntryValues.displayDateTime(LocalDateTime.parse(it, EntryValues.DATETIME_STORAGE))
        }
        else -> rawValue(values, field)
    }

    private fun reformat(raw: String?, format: (String) -> String): String? {
        val text = raw ?: return null
        return try {
            format(text)
        } catch (_: Exception) {
            text // fall back to the stored text if it doesn't parse
        }
    }

    /**
     * Every piece of this idea's text a search should look through, across all
     * of its fields (dates in their displayed form, so searching "June" works).
     */
    fun searchableText(values: JsonObject, fields: List<IdeaFieldDef>): List<String> =
        fields.flatMap { field ->
            when (field.type) {
                FieldType.TAGS -> tags(values, field)
                else -> listOfNotNull(displayValue(values, field))
            }
        }

    /** Whitespace-separated words, so runs of spaces or blank lines never inflate the count. */
    fun wordCount(text: String): Int =
        text.split(Regex("\\s+")).count { it.isNotEmpty() }

    /** Every stored character, spaces and line breaks included. */
    fun characterCount(text: String): Int = text.length

    /**
     * The right-aligned count line under a multiline field, or null when the
     * field asks for neither count. With both on they share one line, separated
     * by a centred dot: `214 Words · 1,382 Characters`.
     */
    fun countsLine(field: IdeaFieldDef, text: String): String? {
        val parts = buildList {
            if (field.wordCount) add(plural(wordCount(text), "Word", "Words"))
            if (field.characterCount) add(plural(characterCount(text), "Character", "Characters"))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun plural(count: Int, one: String, many: String): String {
        val formatted = NumberFormat.getIntegerInstance(Locale.getDefault()).format(count)
        return "$formatted ${if (count == 1) one else many}"
    }
}
