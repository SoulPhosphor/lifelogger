package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Owns the shape of [LogEntry.valuesJson] and how stored values read back for
 * display. The stored JSON is an object keyed by each field's permanent
 * [FieldDef.id], so renaming or reordering a field never disconnects its values:
 *
 * - `dropdown` (Single Choice) fields store the selected option's permanent
 *   [ChoiceOption.id] as a JSON string; `multiple` fields store a JSON array of
 *   the selected option ids. A rename therefore never touches stored answers.
 * - `tags` fields are stored as a JSON array of the tag strings.
 * - every other type is stored as a JSON string in a machine-readable form
 *   (`date` as `yyyy-MM-dd`, `time` as `HH:mm`, `datetime` as `yyyy-MM-dd'T'HH:mm`,
 *   `scale`/`number` as the raw number text). Display formatting (12-hour
 *   AM/PM times, the `4 / 5` scale form) is applied here at read time, never at
 *   storage time (docs/FORMATTING_SPEC.md §1).
 *
 * Empty optional fields are simply absent from the object.
 *
 * The free-text Notes box lives under [NOTES_KEY], a reserved key that can never
 * collide with a field id.
 *
 * Entries written before field identities existed were keyed by label;
 * [FieldIdentityMigration] converts them once.
 */
object EntryValues {

    const val NOTES_KEY = "__notes__"

    private val json = Json { ignoreUnknownKeys = true }

    // Machine-readable storage patterns. These are what controls write.
    val DATE_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val TIME_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val DATETIME_STORAGE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    // Months are always spelled out in full; times always display as 12-hour
    // with AM/PM (docs/FORMATTING_SPEC.md §1).
    private val dateDisplay = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
    private val timeDisplay = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateTimeDisplay = DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", Locale.getDefault())

    // Entry rows use a stamp without the year (docs/UI_SPEC.md §3).
    private val rowTimestampDisplay = DateTimeFormatter.ofPattern("MMMM d, h:mm a", Locale.getDefault())

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

    /** The selected option ids of a `multiple` field. */
    fun selectedOptions(values: JsonObject, field: FieldDef): Set<String> =
        stringList(values, field).toSet()

    /**
     * The saved strings of an array-valued field, in stored order. A `tags`
     * field keeps the order its tags were added in, so it reads back that way.
     */
    fun stringList(values: JsonObject, field: FieldDef): List<String> =
        (values[field.id] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    /** The raw stored string for any single-valued field, or null if absent. */
    fun rawValue(values: JsonObject, field: FieldDef): String? =
        (values[field.id] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }

    /**
     * The labels of the options a choice field's stored value selects, in the
     * field's current option order. An id the field no longer knows (never
     * expected after migration) is shown as its stored text rather than hidden.
     */
    fun choiceLabels(field: FieldDef, raw: JsonElement): List<String> {
        val ids = when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(raw.contentOrNull?.ifBlank { null })
            else -> emptyList()
        }
        val known = field.choices.filter { it.id in ids }.map { it.label }
        val unknown = ids.filter { id -> field.choices.none { it.id == id } }
        return known + unknown
    }

    /**
     * A human-readable rendering of one field's stored value, or null when the
     * field has no value. Used by the entry list and (later) reports.
     */
    fun displayValue(field: FieldDef, values: JsonObject): String? {
        val raw = values[field.id] ?: return null
        return when (field.type) {
            FieldType.DROPDOWN, FieldType.MULTIPLE ->
                choiceLabels(field, raw).takeIf { it.isNotEmpty() }?.joinToString(", ")
            FieldType.TAGS ->
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
            FieldType.RATIO ->
                (raw as? JsonPrimitive)?.contentOrNull?.ifBlank { null }?.let { RatioValues.display(it, field.unit) }
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
     * A short "last entry" phrase for the Home screen: "Today", "Yesterday", or a
     * `MMMM d` date (docs/UI_SPEC.md §2). Returns null if there is no timestamp.
     */
    fun displayLastEntry(iso: String?): String? {
        val date = iso?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
            ?: return null
        return relativeDay(date)
    }

    /**
     * The Clicker "Last Saved" day, from an epoch-millis timestamp
     * ([ClickerLog.lastModifiedAt]). Same Today / Yesterday / "MMMM d" wording as
     * [displayLastEntry], so a Clicker grouping's Home row reads like a Form's.
     */
    fun displayLastSaved(epochMillis: Long?): String? {
        val date = epochMillis?.let {
            runCatching {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }.getOrNull()
        } ?: return null
        return relativeDay(date)
    }

    /** "Today" / "Yesterday" / "MMMM d" for a date, relative to the current day. */
    private fun relativeDay(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()).format(date)
        }
    }

    /**
     * Remove every reference to the option [optionId] from the value stored under
     * [fieldId] — the data side of permanently deleting an option. A Single
     * Choice answer holding it becomes unanswered (the key is dropped); a
     * Multiple Choice answer keeps all its other selections, and is dropped only
     * when nothing else was selected. Every other field, the Notes, and the entry
     * itself are untouched.
     *
     * Returns the input unchanged when the entry never referenced the option.
     */
    fun withoutOption(valuesJson: String, fieldId: String, optionId: String): String {
        val values = decode(valuesJson)
        val current = values[fieldId] ?: return valuesJson
        val updated = values.toMutableMap()
        when (current) {
            is JsonArray -> {
                val kept = current.filterNot { (it as? JsonPrimitive)?.contentOrNull == optionId }
                if (kept.size == current.size) return valuesJson
                if (kept.isEmpty()) updated.remove(fieldId) else updated[fieldId] = JsonArray(kept)
            }
            is JsonPrimitive -> {
                if (current.contentOrNull != optionId) return valuesJson
                updated.remove(fieldId)
            }
            else -> return valuesJson
        }
        return encode(updated)
    }

    /** Convenience for callers building a value map. */
    fun string(value: String): JsonElement = JsonPrimitive(value)

    fun stringArray(values: Collection<String>): JsonElement =
        JsonArray(values.map { JsonPrimitive(it) })
}
