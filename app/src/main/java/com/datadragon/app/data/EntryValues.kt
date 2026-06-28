package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Owns the shape of [LogEntry.valuesJson] and how stored values read back for
 * display. The stored JSON is an object keyed by field label:
 *
 * - `multiple` fields are stored as a JSON array of the selected option strings.
 * - every other type is stored as a JSON string in a machine-readable form
 *   (`date` as `yyyy-MM-dd`, `time` as `HH:mm`, `datetime` as `yyyy-MM-dd'T'HH:mm`,
 *   `scale`/`number` as the raw number text). Display formatting (12-hour
 *   AM/PM times, the `4 / 5` scale form) is applied here at read time, never at
 *   storage time (docs/FORMATTING_SPEC.md §1).
 *
 * Empty optional fields are simply absent from the object.
 *
 * The free-text Notes box lives under [NOTES_KEY], a reserved key that can never
 * collide with a user-defined field label.
 */
object EntryValues {

    const val NOTES_KEY = "__notes__"

    private val json = Json { ignoreUnknownKeys = true }

    // Machine-readable storage patterns. These are what controls write.
    val DATE_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val TIME_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DATETIME_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    // Times always display as 12-hour with AM/PM.
    private val dateDisplay = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val timeDisplay = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateTimeDisplay = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.getDefault())

    // Entry rows use a compact stamp without the year (docs/UI_SPEC.md §3).
    private val rowTimestampDisplay = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())

    /**
     * Format an entry's stored `createdAt` (ISO-8601 with offset) for an entry
     * row. Falls back to the raw string if it doesn't parse.
     */
    fun displayEntryTimestamp(iso: String): String =
        formatEntry(iso, rowTimestampDisplay)

    /** Full date for a stored `createdAt`, e.g. `June 27, 2026` (report header). */
    fun displayEntryDate(iso: String): String = formatEntry(iso, dateDisplay)

    /** Full date and time for a stored `createdAt`, e.g. `June 27, 2026, 2:14 PM`. */
    fun displayEntryDateTime(iso: String): String = formatEntry(iso, dateTimeDisplay)

    private fun formatEntry(iso: String, formatter: DateTimeFormatter): String =
        try {
            OffsetDateTime.parse(iso).format(formatter)
        } catch (_: Exception) {
            iso
        }

    // Display helpers reused by the entry-form pickers (12-hour, the default).
    fun displayDate(date: LocalDate): String = dateDisplay.format(date)
    fun displayTime(time: LocalTime): String = timeDisplay.format(time)
    fun displayDateTime(dateTime: LocalDateTime): String = dateTimeDisplay.format(dateTime)

    // JsonElement.toString() emits canonical JSON, which decode() parses back.
    fun encode(values: Map<String, JsonElement>): String = JsonObject(values).toString()

    fun decode(valuesJson: String): JsonObject =
        try {
            json.parseToJsonElement(valuesJson) as? JsonObject ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }

    fun notes(values: JsonObject): String? =
        (values[NOTES_KEY] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }

    /** The selected option strings for a `multiple` field. */
    fun selectedOptions(values: JsonObject, label: String): Set<String> =
        (values[label] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
            ?: emptySet()

    /** The raw stored string for any single-valued field, or null if absent. */
    fun rawValue(values: JsonObject, label: String): String? =
        (values[label] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }

    /**
     * A human-readable rendering of one field's stored value, or null when the
     * field has no value. Used by the entry list and (later) reports.
     */
    fun displayValue(field: FieldDef, values: JsonObject): String? {
        val raw = values[field.label] ?: return null
        return when (field.type) {
            FieldType.MULTIPLE ->
                (raw as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ")
            FieldType.SCALE -> {
                val n = (raw as? JsonPrimitive)?.contentOrNull?.ifBlank { null } ?: return null
                field.to?.let { "$n / $it" } ?: n
            }
            FieldType.DATE ->
                reformat(raw, { LocalDate.parse(it, DATE_STORAGE) }) { dateDisplay.format(it) }
            FieldType.TIME ->
                reformat(raw, { LocalTime.parse(it, TIME_STORAGE) }) { timeDisplay.format(it) }
            FieldType.DATETIME ->
                reformat(raw, { LocalDateTime.parse(it, DATETIME_STORAGE) }) { dateTimeDisplay.format(it) }
            else -> (raw as? JsonPrimitive)?.contentOrNull?.ifBlank { null }
        }
    }

    /**
     * A one-line summary of an entry's field values for the entry row, in the
     * log's field order, joined with " · " (docs/UI_SPEC.md §3). Notes are shown
     * separately, so they are excluded here.
     */
    fun summaryLine(fields: List<FieldDef>, values: JsonObject): String =
        fields.mapNotNull { displayValue(it, values) }.joinToString(" · ")

    private inline fun <T> reformat(
        raw: JsonElement,
        parse: (String) -> T,
        format: (T) -> String,
    ): String? {
        val text = (raw as? JsonPrimitive)?.contentOrNull?.ifBlank { null } ?: return null
        return try {
            format(parse(text))
        } catch (_: Exception) {
            text // fall back to the stored text if it doesn't parse
        }
    }

    /**
     * A short "last entry" phrase for the Home screen: "today", "yesterday", or a
     * `MMM d` date (docs/UI_SPEC.md §2). Returns null if there is no timestamp.
     */
    fun displayLastEntry(iso: String?): String? {
        val date = iso?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
            ?: return null
        val today = LocalDate.now()
        return when (date) {
            today -> "today"
            today.minusDays(1) -> "yesterday"
            else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(date)
        }
    }

    /** Convenience for callers building a value map. */
    fun string(value: String): JsonElement = JsonPrimitive(value)

    fun stringArray(values: Collection<String>): JsonElement =
        JsonArray(values.map { JsonPrimitive(it) })
}
