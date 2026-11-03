package com.datadragon.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One-time filling when a weekday field is added to an existing form. */
object WeekdayBackfill {
    /** The chosen default timestamp wins; the card's creation timestamp is the fallback. */
    fun fillEntry(
        valuesJson: String,
        createdAt: String,
        fields: List<FieldDef>,
        targetIds: Set<String>,
        timestampFieldId: String?,
    ): String? {
        val targets = fields.filter {
            it.id in targetIds && it.type == FieldType.DAY_OF_WEEK && it.weekday.defaultEnteredDay
        }
        if (targets.isEmpty()) return null
        val values = runCatching { Json.parseToJsonElement(valuesJson) as? JsonObject }.getOrNull()
            ?.toMutableMap() ?: return null
        val date = timestampDate(values, createdAt, fields, timestampFieldId) ?: return null
        var changed = false
        targets.forEach { field ->
            if (Weekday.selected(values[field.id]).isNotEmpty()) return@forEach
            val selected = Weekday.defaults(field.choices, field.weekday, date)
            if (selected.isNotEmpty()) {
                values[field.id] = EntryValues.stringArray(selected)
                changed = true
            }
        }
        return if (changed) EntryValues.encode(values) else null
    }

    /** Resolve the same hierarchy for backfill, new cards and timestamp edits. */
    fun timestampDate(
        values: Map<String, JsonElement>,
        createdAt: String,
        fields: List<FieldDef>,
        timestampFieldId: String?,
    ): LocalDate? {
        val source = fields.firstOrNull { it.id == timestampFieldId }
        val raw = (source?.id?.let { values[it] } as? JsonPrimitive)?.contentOrNull
        val customDate = runCatching {
            when (source?.type) {
                FieldType.DATE -> raw?.let { LocalDate.parse(it, EntryValues.DATE_STORAGE) }
                FieldType.DATETIME -> raw?.let { LocalDateTime.parse(it, EntryValues.DATETIME_STORAGE).toLocalDate() }
                else -> null
            }
        }.getOrNull()
        return customDate ?: runCatching { OffsetDateTime.parse(createdAt).toLocalDate() }.getOrNull()
    }

    /** Only a changed effective timestamp date triggers synchronization; other card edits keep manual choices. */
    fun adjust(
        values: Map<String, JsonElement>,
        createdAt: String,
        fields: List<FieldDef>,
        timestampFieldId: String?,
        previousValues: Map<String, JsonElement>,
        previousFields: List<FieldDef> = fields,
        previousTimestampFieldId: String? = timestampFieldId,
    ): Map<String, JsonElement> {
        val before = timestampDate(previousValues, createdAt, previousFields, previousTimestampFieldId)
        val after = timestampDate(values, createdAt, fields, timestampFieldId) ?: return values
        if (before == after) return values
        return values.toMutableMap().apply {
            fields.filter { it.type == FieldType.DAY_OF_WEEK && it.weekday.defaultEnteredDay &&
                previousFields.any { old -> old.id == it.id } }.forEach { field ->
                val next = field.choices.firstOrNull { !it.retired && it.weekday == after.dayOfWeek.value }?.id
                    ?: return@forEach
                val previousDay = field.choices.firstOrNull { it.weekday == before?.dayOfWeek?.value }?.id
                val selected = if (field.weekday.onlyOne) setOf(next)
                    else (Weekday.selected(values[field.id]).toSet() - setOfNotNull(previousDay)) + next
                put(field.id, EntryValues.stringArray(selected))
            }
        }
    }

    /** Run in the form-save transaction. Updating only values preserves dates and identities. */
    suspend fun apply(
        entryDao: LogEntryDao,
        templateId: Long,
        fields: List<FieldDef>,
        targetIds: Set<String>,
        timestampFieldId: String?,
    ) {
        if (targetIds.isEmpty()) return
        entryDao.getForTemplateOnce(templateId).forEach { entry ->
            val filled = fillEntry(entry.valuesJson, entry.createdAt, fields, targetIds, timestampFieldId)
                ?: return@forEach
            entryDao.update(entry.copy(valuesJson = filled))
        }
    }
}
