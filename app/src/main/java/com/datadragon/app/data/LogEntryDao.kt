package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogEntry].
 *
 * Entries are create / read / delete only — they are never edited (only added or
 * deleted), so there is deliberately no update. Delete is wired in Phase 7.
 */
@Dao
interface LogEntryDao {

    @Insert
    suspend fun insert(entry: LogEntry): Long

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

    /** One-shot snapshot of every entry, for building a backup. */
    @Query("SELECT * FROM log_entries ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<LogEntry>

    /** Clears all entries — used by Restore, which replaces all data. */
    @Query("DELETE FROM log_entries")
    suspend fun deleteAll()

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
