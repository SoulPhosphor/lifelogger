package com.datadragon.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A clicker card's values, stored in the card's `valuesJson` as a simple map of
 * [ClickerField.id] to a string. Numbers are the string form of the integer;
 * date/time are ISO text; text/multitext are the text itself. Interpreting a
 * value is up to the field's type.
 */
object ClickerValues {

    /**
     * The card's Follow-Up Notes text lives under this reserved key, alongside
     * the field values — the same approach as a form entry's Notes box
     * ([EntryValues.NOTES_KEY]). Field ids are UUIDs, so they never collide with
     * it, and backup/restore carry it inside `valuesJson` unchanged.
     */
    const val FOLLOW_UP_KEY = "__follow_up__"

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(valuesJson: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(valuesJson) }.getOrDefault(emptyMap())

    fun encode(values: Map<String, String>): String = json.encodeToString(values)

    /** The number stored for a field, or null when none has been entered. */
    fun number(values: Map<String, String>, fieldId: String): Int? =
        values[fieldId]?.toIntOrNull()

    /** The text stored for a field, or empty when none. */
    fun text(values: Map<String, String>, fieldId: String): String =
        values[fieldId] ?: ""
}
