package com.datadragon.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime

/**
 * Edit Form's one-time "Convert Time Stamps to Date and Time Field": copies an
 * entry's automatic timestamp (`createdAt`) into a Date & Time field so the
 * information survives switching Show Timestamp off.
 *
 * Only an empty destination is filled — a value the user already entered is
 * never replaced. The timestamp keeps the wall-clock date, hour and minute it
 * was recorded with (the Date & Time field stores no seconds), and the entry's
 * own `createdAt` / `updatedAt` are left untouched by the caller.
 */
object TimestampConversion {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [valuesJson] with [createdAt] written into [fieldId], or null when nothing
     * should change: the field already holds a value, the timestamp can't be
     * read, or the stored values aren't a readable JSON object (left as is
     * rather than risk losing them).
     */
    fun fill(valuesJson: String, createdAt: String, fieldId: String): String? {
        val values = runCatching { json.parseToJsonElement(valuesJson) as? JsonObject }.getOrNull() ?: return null
        if (hasValue(values, fieldId)) return null
        val stamp = runCatching {
            OffsetDateTime.parse(createdAt).toLocalDateTime().format(EntryValues.DATETIME_STORAGE)
        }.getOrNull() ?: return null
        return EntryValues.encode(values + (fieldId to JsonPrimitive(stamp)))
    }

    /**
     * Fill [fieldId] from each entry's timestamp across one form's entries, only
     * where it is empty. Returns how many entries changed. Run it inside the
     * same transaction as the Edit Form save it belongs to.
     */
    suspend fun convert(entryDao: LogEntryDao, templateId: Long, fieldId: String): Int {
        var changed = 0
        entryDao.getForTemplateOnce(templateId).forEach { entry ->
            val filled = fill(entry.valuesJson, entry.createdAt, fieldId) ?: return@forEach
            entryDao.update(entry.copy(valuesJson = filled))
            changed++
        }
        return changed
    }

    private fun hasValue(values: JsonObject, fieldId: String): Boolean {
        val existing = values[fieldId] ?: return false
        if (existing is JsonNull) return false
        val text = (existing as? JsonPrimitive)?.contentOrNull ?: return true
        return text.isNotBlank()
    }
}
