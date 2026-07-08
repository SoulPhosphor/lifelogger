package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined checklist (a "list" in the UI).
 *
 * Unlike a [LogTemplate], a checklist has no fixed schema and nothing is locked:
 * its [name] (shown as the "Title" at the top of the list) and its items can be
 * edited freely at any time. Its items live in [ChecklistItem].
 */
@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long,
)
