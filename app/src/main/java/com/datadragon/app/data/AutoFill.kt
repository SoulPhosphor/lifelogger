package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * A Date, Time or Date & Time field's "Auto Fill": a new entry's value is
 * filled from the entry's timestamp or from another field that holds at least
 * one matching part ([FieldDef.autoFillSource]).
 *
 * - A Date field takes the date from a Date or Date & Time source.
 * - A Time field takes the time from a Time or Date & Time source.
 * - A Date & Time field takes whatever a Date, Time or Date & Time source has,
 *   and the missing part (if any) from the entry's timestamp.
 *
 * The same rules serve the entry form (new entries) and Edit Form's one-time
 * "Convert Field to Dropdown Values One Time", which fills existing entries
 * only where the field is empty.
 */
object AutoFill {

    /** [FieldDef.autoFillSource] value meaning the entry's own timestamp. */
    const val TIMESTAMP = "timestamp"

    private val json = Json { ignoreUnknownKeys = true }

    /** True for the field types that offer Auto Fill. */
    val FieldType.autoFillable: Boolean
        get() = this == FieldType.DATE || this == FieldType.TIME || this == FieldType.DATETIME

    /** Whether a [source]-typed field has at least one part a [target]-typed field can take. */
    fun canDonate(source: FieldType, target: FieldType): Boolean = when (target) {
        FieldType.DATE -> source == FieldType.DATE || source == FieldType.DATETIME
        FieldType.TIME -> source == FieldType.TIME || source == FieldType.DATETIME
        FieldType.DATETIME -> source.autoFillable
        else -> false
    }

    /**
     * The other fields [target] may auto-fill from, in form order. A field that
     * already (directly or through others) fills from [target] is left out, so
     * two fields can never fill from each other.
     */
    fun sourceFields(target: FieldDef, fields: List<FieldDef>): List<FieldDef> =
        fields.filter { it.id != target.id && canDonate(it.type, target.type) && !fillsFrom(it, target.id, fields) }

    /** True when [field]'s Auto Fill chain reaches the field with id [targetId]. */
    private fun fillsFrom(field: FieldDef, targetId: String, fields: List<FieldDef>): Boolean {
        val seen = mutableSetOf<String>()
        var current: FieldDef? = field
        while (current != null && seen.add(current.id)) {
            val source = current.autoFillSource ?: return false
            if (source == targetId) return true
            current = fields.firstOrNull { it.id == source }
        }
        return false
    }

    /**
     * [target]'s working source: [TIMESTAMP], a valid source field's id, or null
     * when Auto Fill is off or its chosen field no longer qualifies.
     */
    fun sourceOf(target: FieldDef, fields: List<FieldDef>): String? {
        if (!target.type.autoFillable) return null
        val source = target.autoFillSource ?: return null
        if (source == TIMESTAMP) return source
        return source.takeIf { id -> sourceFields(target, fields).any { it.id == id } }
    }

    /** The Auto Fill fields, each after any field it fills from. */
    fun fillOrder(fields: List<FieldDef>): List<FieldDef> {
        val pending = fields.filter { sourceOf(it, fields) != null }.toMutableList()
        val ordered = mutableListOf<FieldDef>()
        while (pending.isNotEmpty()) {
            val ready = pending.filter { field ->
                val source = sourceOf(field, fields)
                source == TIMESTAMP || pending.none { it.id == source }
            }
            // sourceOf rules out loops, so something is always ready.
            if (ready.isEmpty()) break
            ordered += ready
            pending -= ready.toSet()
        }
        return ordered
    }

    /**
     * The stored value a [target]-typed field auto-fills to. [sourceType] is
     * null for the entry's [timestamp]; otherwise [sourceRaw] is that field's
     * stored value, and null comes back when it holds nothing usable.
     */
    fun value(target: FieldType, sourceType: FieldType?, sourceRaw: String?, timestamp: LocalDateTime): String? {
        val (date, time) = if (sourceType == null) {
            timestamp.toLocalDate() to timestamp.toLocalTime()
        } else {
            parts(sourceType, sourceRaw) ?: return null
        }
        return when (target) {
            FieldType.DATE -> date?.format(EntryValues.DATE_STORAGE)
            FieldType.TIME -> time?.format(EntryValues.TIME_STORAGE)
            FieldType.DATETIME -> LocalDateTime.of(date ?: timestamp.toLocalDate(), time ?: timestamp.toLocalTime())
                .format(EntryValues.DATETIME_STORAGE)
            else -> null
        }
    }

    /** The date and time parts of a stored date / time / datetime value, or null when unreadable. */
    private fun parts(type: FieldType, raw: String?): Pair<LocalDate?, LocalTime?>? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            when (type) {
                FieldType.DATE -> LocalDate.parse(text, EntryValues.DATE_STORAGE) to null
                FieldType.TIME -> null to LocalTime.parse(text, EntryValues.TIME_STORAGE)
                FieldType.DATETIME -> LocalDateTime.parse(text, EntryValues.DATETIME_STORAGE)
                    .let { it.toLocalDate() to it.toLocalTime() }
                else -> null
            }
        }.getOrNull()
    }

    /**
     * [valuesJson] with each field in [targetIds] auto-filled where it is empty,
     * using the entry's [createdAt] as its timestamp, or null when nothing
     * changes (including unreadable values or timestamp, which are left alone).
     */
    fun fillEntry(valuesJson: String, createdAt: String, fields: List<FieldDef>, targetIds: Set<String>): String? {
        val values = runCatching { json.parseToJsonElement(valuesJson) as? JsonObject }.getOrNull()
            ?.toMutableMap() ?: return null
        val timestamp = runCatching { OffsetDateTime.parse(createdAt).toLocalDateTime() }.getOrNull() ?: return null
        var changed = false
        fillOrder(fields).filter { it.id in targetIds }.forEach { target ->
            if (stringValue(values, target.id) != null) return@forEach
            val source = sourceOf(target, fields) ?: return@forEach
            val sourceField = fields.firstOrNull { it.id == source }
            val filled = value(target.type, sourceField?.type, sourceField?.let { stringValue(values, it.id) }, timestamp)
                ?: return@forEach
            values[target.id] = JsonPrimitive(filled)
            changed = true
        }
        return if (changed) EntryValues.encode(values) else null
    }

    /** A non-blank stored string value, or null. */
    private fun stringValue(values: Map<String, kotlinx.serialization.json.JsonElement>, id: String): String? {
        val element = values[id] ?: return null
        if (element is JsonNull) return null
        return (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /**
     * Fill [targetIds] across one form's existing entries, only where empty.
     * Returns how many entries changed. Entry timestamps are never touched. Run
     * it inside the same transaction as the Edit Form save it belongs to.
     */
    suspend fun convert(entryDao: LogEntryDao, templateId: Long, fields: List<FieldDef>, targetIds: Set<String>): Int {
        if (targetIds.isEmpty()) return 0
        var changed = 0
        entryDao.getForTemplateOnce(templateId).forEach { entry ->
            val filled = fillEntry(entry.valuesJson, entry.createdAt, fields, targetIds) ?: return@forEach
            entryDao.update(entry.copy(valuesJson = filled))
            changed++
        }
        return changed
    }
}
