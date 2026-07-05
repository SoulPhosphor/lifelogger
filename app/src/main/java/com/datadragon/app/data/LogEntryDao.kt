package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogEntry].
 *
 * Entries in a locked log are create / read / delete only. [update] is used only
 * for entries in an unlocked (editable) log.
 */
@Dao
interface LogEntryDao {

    @Insert
    suspend fun insert(entry: LogEntry): Long

    /** Edit an existing entry — only used for entries in an unlocked log. */
    @Update
    suspend fun update(entry: LogEntry)

    @Delete
    suspend fun delete(entry: LogEntry)

    /** Entries for one log, newest first (docs/UI_SPEC.md §3). */
    @Query(
        "SELECT * FROM log_entries WHERE templateId = :templateId " +
            "ORDER BY createdAt DESC, id DESC"
    )
    fun observeForTemplate(templateId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE id = :id")
    suspend fun getById(id: Long): LogEntry?

    /**
     * One-shot snapshot of every entry for one log. Used when a form edit renames
     * a field label or option and the stored values need re-keying to match.
     */
    @Query("SELECT * FROM log_entries WHERE templateId = :templateId")
    suspend fun getForTemplateOnce(templateId: Long): List<LogEntry>

    /** One-shot snapshot of every entry, for building a backup. */
    @Query("SELECT * FROM log_entries ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<LogEntry>

    /** Clears all entries — used by Restore, which replaces all data. */
    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

    /** Removes every entry for one log — used when deleting the log itself. */
    @Query("DELETE FROM log_entries WHERE templateId = :templateId")
    suspend fun deleteForTemplate(templateId: Long)

    /**
     * Per-log count and most-recent timestamp, so the Home screen can show
     * "N entries · last entry …" without loading every entry (docs/UI_SPEC.md §2).
     */
    @Query(
        "SELECT templateId, COUNT(*) AS count, MAX(createdAt) AS lastCreatedAt " +
            "FROM log_entries GROUP BY templateId"
    )
    fun observeSummaries(): Flow<List<EntrySummary>>
}

/** Aggregate row backing the Home screen's per-log entry summary. */
data class EntrySummary(
    val templateId: Long,
    val count: Int,
    val lastCreatedAt: String?,
)
