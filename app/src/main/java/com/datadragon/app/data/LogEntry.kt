package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved entry in a log.
 *
 * Fields: id, templateId, createdAt, updatedAt, valuesJson.
 *
 * `createdAt` is set automatically when the entry is saved. Entries are never
 * edited (only added or deleted), so `updatedAt` is vestigial and always null —
 * it is kept only so existing backups round-trip. `createdAt` is an ISO-8601
 * string with a timezone offset, e.g. `2026-06-28T14:14:00-05:00`, per
 * docs/FORMATTING_SPEC.md §1 — storage stays machine-readable; the 12-hour
 * AM/PM display format is applied only at read time.
 *
 * `valuesJson` holds the field values keyed by field label (a JSON object). Its
 * shape is owned by [EntryValues]; the user's free-text Notes live there too,
 * under a reserved key so they can never collide with a user-defined field.
 *
 * `marked` is the user's manual highlight: when true, the entry shows a filled
 * star and its menu offers "Unmark". It defaults to false (no star).
 */
@Entity(
    tableName = "log_entries",
    indices = [Index("templateId")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val createdAt: String,
    val updatedAt: String? = null,
    val valuesJson: String,
    val marked: Boolean = false,
)
