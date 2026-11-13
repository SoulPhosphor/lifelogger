package com.datadragon.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDateTime

/**
 * Builds the "Human Readable JSON (.json)" export for one Clicker Data grouping: the
 * same report as its `.txt` export ([ClickerReport]), as structured JSON.
 * Restore and import keep using "Application Data (.json)", untouched here.
 *
 * - the root holds `title` (`"<Grouping> Report"`), `dateRange` (`start` /
 *   `end` ISO dates, only when the grouping shows date stamps, as in the
 *   report), `totalCards` and `cards`, in the report's order;
 * - a card's automatic stamps appear only when the grouping shows them, as in
 *   the report: `dateTime` when both show, else `date` or `time` alone;
 * - `values` is keyed by field label in setup order (a repeated label gets a
 *   ` (2)`… suffix); blank fields are left out; Click Tracker and Write-In
 *   Number are JSON numbers (an untouched tracker is its starting number);
 *   Day of Week is an array of labels; dates and times are ISO; Comparison
 *   reads as `Rows` plus summary lines, like a form's;
 * - `Follow-Up Notes` holds the card's note when the export dialog includes it.
 */
object ClickerCleanJsonBuilder {

    fun build(
        log: ClickerLog,
        fields: List<ClickerField>,
        cards: List<ClickerCard>,
        includeFollowUps: Boolean,
    ): String {
        val ordered = ClickerReport.ordered(cards)
        val root = linkedMapOf<String, JsonElement>()
        root["title"] = JsonPrimitive("${log.title} Report")
        val dates = if (log.autoDateStamp) ordered.mapNotNull { ClickerReport.visibleDate(log, it) } else emptyList()
        if (dates.isNotEmpty()) {
            root["dateRange"] = CleanJsonBuilder.dateRange(dates.min().toString(), dates.max().toString())
        }
        root["totalCards"] = JsonPrimitive(ordered.size)
        root["cards"] = JsonArray(ordered.map { card(log, fields, it, includeFollowUps) })
        return CleanJsonBuilder.json.encodeToString(JsonElement.serializer(), JsonObject(root))
    }

    private fun card(log: ClickerLog, fields: List<ClickerField>, card: ClickerCard, includeFollowUps: Boolean): JsonObject {
        val values = ClickerValues.decode(card.valuesJson)
        val result = linkedMapOf<String, JsonElement>()
        val date = ClickerReport.visibleDate(log, card)
        val time = ClickerReport.visibleTime(log, card)
        when {
            date != null && time != null -> result["dateTime"] = JsonPrimitive(LocalDateTime.of(date, time).format(EntryValues.DATETIME_STORAGE))
            date != null -> result["date"] = JsonPrimitive(date.toString())
            time != null -> result["time"] = JsonPrimitive(time.format(EntryValues.TIME_STORAGE))
        }
        val fieldValues = linkedMapOf<String, JsonElement>()
        fields.forEach { field -> value(field, values)?.let { fieldValues.putUnique(field.label, it) } }
        result["values"] = JsonObject(fieldValues)
        if (includeFollowUps) {
            values[ClickerValues.FOLLOW_UP_KEY]?.takeIf { it.isNotBlank() }?.let {
                result[CleanJsonBuilder.FOLLOW_UP_NOTES_KEY] = JsonPrimitive(it)
            }
        }
        return JsonObject(result)
    }

    /** A field's value, or null exactly when the report leaves the field out. */
    private fun value(field: ClickerField, values: Map<String, String>): JsonElement? {
        // Matches the report's choice of which fields appear.
        ClickerReport.displayValue(field, values) ?: return null
        return when (field.type) {
            ClickerFieldType.CLICK_TRACKER ->
                JsonPrimitive(ClickerValues.number(values, field.id) ?: field.startingNumber)
            ClickerFieldType.DAY_OF_WEEK -> {
                val ids = Weekday.fromClicker(values[field.id])
                val known = field.choices.filter { it.id in ids }.map { it.label }
                val unknown = ids.filter { id -> field.choices.none { it.id == id } }
                EntryValues.stringArray(known + unknown)
            }
            ClickerFieldType.COMPARISON -> {
                val def = field.comparisonDef()
                CleanJsonBuilder.itemRows(def, ClickerComparison.rows(values, field.id))
            }
            ClickerFieldType.WRITE_IN_NUMBER -> CleanJsonBuilder.numberOrText(values.getValue(field.id))
            // Stored as "yyyy-MM-dd HH:mm"; written in ISO form.
            ClickerFieldType.DATE_TIME -> JsonPrimitive(values.getValue(field.id).trim().replaceFirst(' ', 'T'))
            else -> JsonPrimitive(values.getValue(field.id))
        }
    }
}
