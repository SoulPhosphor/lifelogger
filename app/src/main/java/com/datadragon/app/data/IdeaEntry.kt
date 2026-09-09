package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved idea in an [IdeaLog].
 *
 * `createdAt` is stamped automatically when the idea is first saved and is an
 * ISO-8601 string with a timezone offset (docs/FORMATTING_SPEC.md §1); editing
 * an idea keeps it and stamps `updatedAt` instead. Ideas are always editable —
 * there is no locking here.
 *
 * `valuesJson` holds the field values keyed by [IdeaFieldDef.id], not by label,
 * so renaming or reordering a field later leaves its stored values attached.
 * Tags fields store a JSON array; everything else stores a JSON string in the
 * same machine-readable forms [EntryValues] uses.
 *
 * `marked` is the user's manual star, exactly as on a form entry. `archived`
 * moves the idea into the log's archive view; archived ideas stay editable,
 * markable, searchable, sortable, and deletable.
 */
@Entity(
    tableName = "idea_entries",
    indices = [Index("ideaLogId")],
)
data class IdeaEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ideaLogId: Long,
    val createdAt: String,
    val updatedAt: String? = null,
    val valuesJson: String,
    val marked: Boolean = false,
    val archived: Boolean = false,
)
