package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A follow-up note attached to a [LogEntry] after it was created.
 *
 * Offered when the entry's log has `allowAppendedNotes` set. Each note is a
 * separately time-stamped addition that lives alongside the original entry
 * rather than changing it, so later information shows up as a dated addendum.
 * A note's text can be edited at any time (regardless of whether the log is
 * locked); its `createdAt` timestamp is kept when the text changes. `createdAt`
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
