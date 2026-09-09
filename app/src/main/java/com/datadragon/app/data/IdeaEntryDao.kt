package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [IdeaEntry]. Ideas are always editable, so [update] is a
 * normal everyday operation here (unlike a locked form's entries).
 */
@Dao
interface IdeaEntryDao {

    @Insert
    suspend fun insert(entry: IdeaEntry): Long

    @Update
    suspend fun update(entry: IdeaEntry)

    @Delete
    suspend fun delete(entry: IdeaEntry)

    /** Set (or clear) an idea's manual star. Works archived or not. */
    @Query("UPDATE idea_entries SET marked = :marked WHERE id = :id")
    suspend fun setMarked(id: Long, marked: Boolean)

    /** Move an idea into, or back out of, its log's archive. */
    @Query("UPDATE idea_entries SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    /** Every idea in one log; the ViewModel splits active from archived and orders them. */
    @Query("SELECT * FROM idea_entries WHERE ideaLogId = :ideaLogId ORDER BY createdAt ASC, id ASC")
    fun observeForLog(ideaLogId: Long): Flow<List<IdeaEntry>>

    @Query("SELECT * FROM idea_entries WHERE id = :id")
    suspend fun getById(id: Long): IdeaEntry?

    @Query("SELECT * FROM idea_entries WHERE id = :id")
    fun observeById(id: Long): Flow<IdeaEntry?>

    /** How many archived ideas one log still holds (gates turning archiving off). */
    @Query("SELECT COUNT(*) FROM idea_entries WHERE ideaLogId = :ideaLogId AND archived = 1")
    suspend fun archivedCount(ideaLogId: Long): Int

    /** Removes every idea in one log — used when deleting the log itself. */
    @Query("DELETE FROM idea_entries WHERE ideaLogId = :ideaLogId")
    suspend fun deleteForLog(ideaLogId: Long)

    /**
     * Per-log count and most-recent timestamp for the Ideas Home cards. Archived
     * ideas are excluded: the summary describes the log's active contents, so
     * archiving something must not inflate the count shown on Home.
     */
    @Query(
        "SELECT ideaLogId, COUNT(*) AS count, MAX(createdAt) AS lastCreatedAt " +
            "FROM idea_entries WHERE archived = 0 GROUP BY ideaLogId"
    )
    fun observeActiveSummaries(): Flow<List<IdeaSummary>>
}

/** Aggregate row backing the Ideas Home per-log summary line. */
data class IdeaSummary(
    val ideaLogId: Long,
    val count: Int,
    val lastCreatedAt: String?,
)
