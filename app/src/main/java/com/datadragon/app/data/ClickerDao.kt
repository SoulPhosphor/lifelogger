package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Data access for [ClickerLog]s and their [ClickerCard]s. */
@Dao
interface ClickerDao {

    // --- Logs ---------------------------------------------------------------

    /** Every log, most recently used first (newest on top of the Clicker home). */
    @Query("SELECT * FROM clicker_logs ORDER BY lastAccessedAt DESC")
    fun observeLogs(): Flow<List<ClickerLog>>

    @Query("SELECT * FROM clicker_logs WHERE id = :id")
    fun observeLog(id: Long): Flow<ClickerLog?>

    @Query("SELECT * FROM clicker_logs WHERE id = :id")
    suspend fun getLog(id: Long): ClickerLog?

    /** Deterministic one-shot read used by the complete backup snapshot. */
    @Query("SELECT * FROM clicker_logs ORDER BY createdAt ASC, uuid ASC")
    suspend fun getAllLogsOnce(): List<ClickerLog>

    @Insert
    suspend fun insertLog(log: ClickerLog): Long

    @Update
    suspend fun updateLog(log: ClickerLog)

    @Delete
    suspend fun deleteLog(log: ClickerLog)

    @Query("DELETE FROM clicker_cards WHERE clickerLogId = :logId")
    suspend fun deleteCardsForLog(logId: Long)

    /** Delete a log and all of its cards together, so no cards are orphaned. */
    @Transaction
    suspend fun deleteLogWithCards(log: ClickerLog) {
        deleteCardsForLog(log.id)
        deleteLog(log)
    }

    /** Bump a log to the top of the recently-used order (opening the log). */
    @Query("UPDATE clicker_logs SET lastAccessedAt = :time WHERE id = :id")
    suspend fun touchLog(id: Long, time: Long)

    /**
     * Record a card-content change: bump both the recently-used order and the
     * "Last Saved" time. Called on every card add / step / value edit / delete,
     * never on a plain open or a grouping-title rename.
     */
    @Query("UPDATE clicker_logs SET lastAccessedAt = :time, lastModifiedAt = :time WHERE id = :id")
    suspend fun touchModified(id: Long, time: Long)

    /**
     * Per-log card count, so the Clicker Home row can show "N Entries" without
     * loading every card. Mirrors the Forms entry-summary query.
     */
    @Query("SELECT clickerLogId AS logId, COUNT(*) AS count FROM clicker_cards GROUP BY clickerLogId")
    fun observeCardCounts(): Flow<List<ClickerCardCount>>

    // --- Cards --------------------------------------------------------------

    /** A log's cards, newest first. */
    @Query("SELECT * FROM clicker_cards WHERE clickerLogId = :logId ORDER BY createdAt DESC")
    fun observeCards(logId: Long): Flow<List<ClickerCard>>

    @Query("SELECT * FROM clicker_cards WHERE id = :id")
    fun observeCard(id: Long): Flow<ClickerCard?>

    @Query("SELECT * FROM clicker_cards WHERE id = :id")
    suspend fun getCard(id: Long): ClickerCard?

    @Query("SELECT * FROM clicker_cards WHERE clickerLogId = :logId ORDER BY createdAt DESC")
    suspend fun getCardsForLog(logId: Long): List<ClickerCard>

    /** Deterministic one-shot read of every card used by the complete backup snapshot. */
    @Query("SELECT * FROM clicker_cards ORDER BY clickerLogId ASC, createdAt ASC, uuid ASC")
    suspend fun getAllCardsOnce(): List<ClickerCard>

    @Insert
    suspend fun insertCard(card: ClickerCard): Long

    @Update
    suspend fun updateCard(card: ClickerCard)

    @Delete
    suspend fun deleteCard(card: ClickerCard)
}

/** Aggregate row backing the Clicker Home row's "N Entries" count. */
data class ClickerCardCount(
    val logId: Long,
    val count: Int,
)
