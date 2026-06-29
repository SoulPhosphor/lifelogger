package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [EntryNote]. Notes are append-only: insert and read, no update.
 * They are removed only when their entry or whole log is deleted.
 */
@Dao
interface EntryNoteDao {

    @Insert
    suspend fun insert(note: EntryNote): Long

    /** All follow-up notes for one log's entries, oldest first, for display. */
    @Query(
        "SELECT n.* FROM entry_notes n " +
            "JOIN log_entries e ON n.entryId = e.id " +
            "WHERE e.templateId = :templateId " +
            "ORDER BY n.createdAt ASC, n.id ASC"
    )
    fun observeForTemplate(templateId: Long): Flow<List<EntryNote>>

    /** One-shot snapshot of every note, for building a backup. */
    @Query("SELECT * FROM entry_notes ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<EntryNote>

    /** Remove the notes for one entry — used when that entry is deleted. */
    @Query("DELETE FROM entry_notes WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)

    /** Remove notes for one log's entries — used when deleting the log itself. */
    @Query(
        "DELETE FROM entry_notes WHERE entryId IN " +
            "(SELECT id FROM log_entries WHERE templateId = :templateId)"
    )
    suspend fun deleteForTemplate(templateId: Long)

    /** Clears all notes — used by Restore, which replaces all data. */
    @Query("DELETE FROM entry_notes")
    suspend fun deleteAll()
}
