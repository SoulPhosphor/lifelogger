package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved entry in a log.
 *
 * Fields: id, templateId, createdAt, updatedAt, valuesJson.
 *
 * `createdAt` is set automatically when the entry is first saved and
 * `updatedAt` when it is later edited (Phase 7). Both are ISO-8601 strings with
 * a timezone offset, e.g. `2026-06-28T14:14:00-05:00`, per
 * docs/FORMATTING_SPEC.md §1 — storage stays machine-readable; the 12h/24h
 * choice is applied only at display time.
 *
 * `valuesJson` holds the field values keyed by field label (a JSON object). Its
 * shape is owned by [EntryValues]; the user's free-text Notes live there too,
 * under a reserved key so they can never collide with a user-defined field.
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
)
