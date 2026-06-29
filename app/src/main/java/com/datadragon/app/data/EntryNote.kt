package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An append-only follow-up note attached to a [LogEntry] after it was created.
 *
 * Only offered when the entry's log has `allowAppendedNotes` set. Notes are
 * insert-only — never edited — so each one is a fixed, separately time-stamped
 * addition that never alters the original entry. This keeps a locked log honest:
 * later information shows up as a dated addendum, not a silent change. `createdAt`
 * is ISO-8601 with offset, matching [LogEntry.createdAt].
 */
@Entity(
    tableName = "entry_notes",
    indices = [Index("entryId")],
)
data class EntryNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val createdAt: String,
    val text: String,
)
