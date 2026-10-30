package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Builds the "Clean JSON (.json)" export for one form: a human-readable file for
 * analysis, never read back by the app. Restore and import keep using the
 * `datadragon-backup` file from "Application Data (.json)", which this leaves
 * untouched.
 *
 * Every field and choice is named by its label instead of its permanent id:
 *
 * - the root holds `formName`, `exportedAt` and `entries`;
 * - each entry holds `createdAt`, `updatedAt` (when set), `marked: true` (only
 *   when marked), `values`, and `FollowUpNotes` (when included and not empty);
 * - `values` is keyed by field label in the form's field order, then the
 *   free-text Notes box as `Entry Notes`. A repeated key gets a ` (2)`, ` (3)`…
 *   suffix so nothing is overwritten. Empty fields are left out;
 * - Single Choice reads as its choice label, Multiple Choice as an array of
 *   labels; a choice id the field no longer knows is kept as its raw id;
 * - dates, times and numbers stay in their stored form (`2026-10-30T03:16`);
 * - Comparison and List Item Quantities read as `Rows`, each keyed by the
 *   field's own column captions, followed by the summary lines the app shows
 *   under the table, with the same labels and math.
 */
object CleanJsonBuilder {

    const val ENTRY_NOTES_KEY = "Entry Notes"
    const val ROWS_KEY = "Rows"
    const val DIFFERENCE_KEY = "Difference"
    const val CHECKED_KEY = "Checked"
    const val FOLLOW_UP_NOTES_KEY = "FollowUpNotes"

    private val json = Json { prettyPrint = true }

    fun build(
        template: LogTemplate,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
        exportedAt: String,
        notesByEntry: Map<Long, List<EntryNote>> = emptyMap(),
        includeFollowUps: Boolean = false,
    ): String {
        val root = JsonObject(
            linkedMapOf(
                "formName" to JsonPrimitive(template.name),
                "exportedAt" to JsonPrimitive(exportedAt),
                "entries" to JsonArray(
                    entries.map { entry ->
                        val notes = if (includeFollowUps) notesByEntry[entry.id].orEmpty() else emptyList()
                        entry(fields, entry, notes)
                    },
                ),
            ),
        )
        return json.encodeToString(JsonElement.serializer(), root)
    }

    private fun entry(fields: List<FieldDef>, entry: LogEntry, notes: List<EntryNote>): JsonObject {
        val result = linkedMapOf<String, JsonElement>()
        result["createdAt"] = JsonPrimitive(entry.createdAt)
        entry.updatedAt?.let { result["updatedAt"] = JsonPrimitive(it) }
        if (entry.marked) result["marked"] = JsonPrimitive(true)
        result["values"] = values(fields, EntryValues.decode(entry.valuesJson))
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
        EntryValues.notes(values)?.let { result.putUnique(ENTRY_NOTES_KEY, JsonPrimitive(it)) }
        return JsonObject(result)
    }

    private fun value(field: FieldDef, values: JsonObject): JsonElement? {
        val raw = values[field.id] ?: return null
        return when (field.type) {
            FieldType.DROPDOWN ->
                EntryValues.choiceLabels(field, raw).firstOrNull()?.let(::JsonPrimitive)
            FieldType.MULTIPLE ->
                EntryValues.choiceLabels(field, raw).takeIf { it.isNotEmpty() }?.let(EntryValues::stringArray)
            FieldType.TAGS ->
                EntryValues.stringList(values, field).takeIf { it.isNotEmpty() }?.let(EntryValues::stringArray)
            FieldType.COMPARISON, FieldType.LIST_ITEM_QUANTITIES ->
                itemRows(field, ComparisonValues.decode(raw))
            else -> EntryValues.rawValue(values, field)?.let(::JsonPrimitive)
        }
    }

    /**
     * The rows and summary lines of an item-row field, matching
     * [ComparisonValues.display]: only answered rows, with clicker Starting
     * Numbers filled in, and only the summary lines that have a value.
     */
    private fun itemRows(field: FieldDef, stored: List<ComparisonRow>): JsonObject? {
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

    /** Put [value] under [key], or under `key (2)`, `key (3)`… when [key] is taken. */
    private fun MutableMap<String, JsonElement>.putUnique(key: String, value: JsonElement) {
        var candidate = key
        var suffix = 2
        while (candidate in this) candidate = "$key (${suffix++})"
        this[candidate] = value
    }
}
