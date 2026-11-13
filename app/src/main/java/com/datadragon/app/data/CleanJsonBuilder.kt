package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Builds the "Human Readable JSON (.json)" export for one form: the same report as
 * the `.txt` export ([ReportBuilder]), as structured JSON for people, AI models
 * and data analysis. It is never read back by the app; restore and import keep
 * using the `datadragon-backup` file from "Application Data (.json)", which this
 * leaves untouched.
 *
 * - the root holds `title` (`"<Form> Report"`), `dateRange` (`start` / `end`
 *   ISO dates, left out when there are no entries), `totalEntries` and
 *   `entries`, oldest first like the report;
 * - each entry holds `createdAt` (its stored ISO timestamp), `values`, `Notes`
 *   (the free-text Notes box, when filled) and `Follow-Up Notes` (when the
 *   export dialog includes them and the entry has some);
 * - `values` is keyed by field label in the form's field order. A repeated
 *   label gets a ` (2)`, ` (3)`… suffix so nothing is overwritten. Empty fields
 *   are left out, as in the report;
 * - Single Choice reads as its choice label, Multiple Choice and Day of Week
 *   as arrays of labels; a choice id the field no longer knows is kept raw;
 * - Number fields are JSON numbers; Scale is `{ "value", "max" }`; Ratio is
 *   `{ "value", "unit" }`; dates and times stay in their stored ISO form;
 * - Comparison and List Item Quantities read as `Rows`, each keyed by the
 *   field's own column captions, followed by the summary lines the report
 *   shows under the table, with the same labels and math.
 */
object CleanJsonBuilder {

    const val NOTES_KEY = "Notes"
    const val FOLLOW_UP_NOTES_KEY = "Follow-Up Notes"
    const val ROWS_KEY = "Rows"
    const val DIFFERENCE_KEY = "Difference"
    const val CHECKED_KEY = "Checked"

    internal val json = Json { prettyPrint = true }

    fun build(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): String {
        // The same order as the .txt report: oldest first.
        val ordered = entries.sortedBy { it.createdAt }
        val root = linkedMapOf<String, JsonElement>()
        root["title"] = JsonPrimitive("${template.name} Report")
        if (ordered.isNotEmpty()) {
            root["dateRange"] = dateRange(entryDate(ordered.first().createdAt), entryDate(ordered.last().createdAt))
        }
        root["totalEntries"] = JsonPrimitive(ordered.size)
        root["entries"] = JsonArray(
            ordered.map { entry ->
                val notes = if (includeFollowUps) notesByEntry[entry.id].orEmpty() else emptyList()
                entry(fields, entry, notes)
            },
        )
        return json.encodeToString(JsonElement.serializer(), JsonObject(root))
    }

    /** `{ "start", "end" }` for a report's date range. */
    internal fun dateRange(start: String, end: String): JsonObject =
        JsonObject(linkedMapOf("start" to JsonPrimitive(start), "end" to JsonPrimitive(end)))

    /** The ISO date of a stored entry timestamp, on the report's calendar day. */
    private fun entryDate(iso: String): String =
        runCatching { OffsetDateTime.parse(iso).toLocalDate().toString() }.getOrDefault(iso)

    private fun entry(fields: List<FieldDef>, entry: LogEntry, notes: List<EntryNote>): JsonObject {
        val stored = EntryValues.decode(entry.valuesJson)
        val result = linkedMapOf<String, JsonElement>()
        result["createdAt"] = JsonPrimitive(entry.createdAt)
        result["values"] = values(fields, stored)
        EntryValues.notes(stored)?.let { result[NOTES_KEY] = JsonPrimitive(it) }
        if (notes.isNotEmpty()) {
            result[FOLLOW_UP_NOTES_KEY] = JsonArray(
                notes.map { note ->
                    JsonObject(
                        linkedMapOf(
                            "createdAt" to JsonPrimitive(note.createdAt),
                            "text" to JsonPrimitive(note.text),
                        ),
                    )
                },
            )
        }
        return JsonObject(result)
    }

    private fun values(fields: List<FieldDef>, values: JsonObject): JsonObject {
        val result = linkedMapOf<String, JsonElement>()
        fields.forEach { field ->
            value(field, values)?.let { result.putUnique(field.label, it) }
        }
        return JsonObject(result)
    }

    private fun value(field: FieldDef, values: JsonObject): JsonElement? {
        val raw = values[field.id] ?: return null
        return when (field.type) {
            FieldType.DROPDOWN ->
                EntryValues.choiceLabels(field, raw).firstOrNull()?.let(::JsonPrimitive)
            FieldType.DAY_OF_WEEK, FieldType.MULTIPLE ->
                EntryValues.choiceLabels(field, raw).takeIf { it.isNotEmpty() }?.let(EntryValues::stringArray)
            FieldType.TAGS ->
                EntryValues.stringList(values, field).takeIf { it.isNotEmpty() }?.let(EntryValues::stringArray)
            FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES ->
                itemRows(field, ComparisonValues.decode(raw))
            FieldType.NUMBER -> EntryValues.rawValue(values, field)?.let(::numberOrText)
            FieldType.SCALE -> EntryValues.rawValue(values, field)?.let { n ->
                val result = linkedMapOf<String, JsonElement>("value" to numberOrText(n))
                field.to?.let { result["max"] = JsonPrimitive(it) }
                JsonObject(result)
            }
            FieldType.RATIO -> EntryValues.rawValue(values, field)?.let { ratio ->
                val result = linkedMapOf<String, JsonElement>("value" to JsonPrimitive(ratio))
                field.unit.trim().takeIf { it.isNotEmpty() }?.let { result["unit"] = JsonPrimitive(it) }
                JsonObject(result)
            }
            else -> EntryValues.rawValue(values, field)?.let(::JsonPrimitive)
        }
    }

    /** [text] as a JSON number, or as the text itself when it isn't one, so nothing is lost. */
    internal fun numberOrText(text: String): JsonPrimitive =
        text.trim().toBigDecimalOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)

    /**
     * The rows and summary lines of an item-row field, matching
     * [ComparisonValues.display]: only answered rows, with clicker Starting
     * Numbers filled in, and only the summary lines that have a value.
     */
    internal fun itemRows(field: FieldDef, stored: List<ComparisonRow>): JsonObject? {
        val rows = ComparisonValues.withStartingNumbers(field, ComparisonValues.answers(stored))
        if (rows.isEmpty()) return null
        val result = linkedMapOf<String, JsonElement>()
        result[ROWS_KEY] = JsonArray(rows.map { row(field, it) })
        ComparisonValues.summaryLines(field, rows).forEach { line ->
            line.value?.let { result.putUnique(line.label, number(it)) }
        }
        return JsonObject(result)
    }

    private fun row(field: FieldDef, row: ComparisonRow): JsonObject {
        val result = linkedMapOf<String, JsonElement>()
        result.putUnique(field.comparisonItemLabel, JsonPrimitive(field.choice(row.item)?.label ?: row.item))
        ComparisonValues.parse(row.first)?.let { result.putUnique(field.comparisonFirstLabel, number(it)) }
        if (field.type == FieldType.COMPARISON) {
            ComparisonValues.parse(row.second)?.let { result.putUnique(field.comparisonSecondLabel, number(it)) }
            ComparisonValues.difference(row)?.let { result.putUnique(DIFFERENCE_KEY, number(it)) }
        }
        if (row.checked && field.isQuantityListWithCheckbox) result.putUnique(CHECKED_KEY, JsonPrimitive(true))
        return JsonObject(result)
    }

    /** A number written as the app shows it: plain digits, no trailing zeros. */
    private fun number(value: BigDecimal): JsonPrimitive = JsonPrimitive(BigDecimal(ComparisonValues.format(value)))
}

/** Put [value] under [key], or under `key (2)`, `key (3)`… when [key] is taken. */
internal fun MutableMap<String, JsonElement>.putUnique(key: String, value: JsonElement) {
    var candidate = key
    var suffix = 2
    while (candidate in this) candidate = "$key (${suffix++})"
    this[candidate] = value
}
