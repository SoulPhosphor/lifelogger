package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [DailyList] and its [DailyListItem]s.
 *
 * The `date` UNIQUE index is the persistence-level guarantee that two saved
 * Daily Lists can never exist for the same date: an insert that would collide
 * aborts (IGNORE returns -1), and nothing in this DAO ever updates a date.
 */
@Dao
interface DailyListDao {

    // --- Cards --------------------------------------------------------------

    /** Insert a new card. Returns -1 if a card for this date already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyList(dailyList: DailyList): Long

    @Update
    suspend fun updateDailyList(dailyList: DailyList)

    /** Every saved card, newest date first. */
    @Query("SELECT * FROM daily_lists ORDER BY date DESC")
    fun observeDailyLists(): Flow<List<DailyList>>

    @Query("SELECT * FROM daily_lists ORDER BY date DESC")
    suspend fun getAllDailyListsOnce(): List<DailyList>

    @Query("SELECT * FROM daily_lists WHERE id = :id")
    fun observeDailyList(id: Long): Flow<DailyList?>

    @Query("SELECT * FROM daily_lists WHERE id = :id")
    suspend fun getDailyList(id: Long): DailyList?

    /** The one card for an exact date, if it has been saved. */
    @Query("SELECT * FROM daily_lists WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: LocalDate): DailyList?

    @Query("SELECT * FROM daily_lists WHERE date = :date LIMIT 1")
    fun observeByDate(date: LocalDate): Flow<DailyList?>

    /** The most recent saved card strictly before [date] — renewal's source. */
    @Query("SELECT * FROM daily_lists WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getPreviousBefore(date: LocalDate): DailyList?

    /** All cards before [date] whose tasks are all completed. */
    @Query(
        "SELECT l.* FROM daily_lists l WHERE l.date < :date AND NOT EXISTS (" +
            "SELECT 1 FROM daily_list_items i WHERE i.dailyListId = l.id AND i.completed = 0)",
    )
    suspend fun getFullyCompletedBefore(date: LocalDate): List<DailyList>

    /** All cards before [date] that still have unfinished tasks. */
    @Query(
        "SELECT l.* FROM daily_lists l WHERE l.date < :date AND EXISTS (" +
            "SELECT 1 FROM daily_list_items i WHERE i.dailyListId = l.id AND i.completed = 0)",
    )
    suspend fun getWithUnfinishedBefore(date: LocalDate): List<DailyList>

    /** Cards strictly before the [n]th newest — the whole-card retention candidates. */
    @Query(
        "SELECT * FROM daily_lists ORDER BY date DESC LIMIT -1 OFFSET :n",
    )
    suspend fun getCardsBeyondCount(n: Int): List<DailyList>

    @Query("DELETE FROM daily_lists WHERE id = :id")
    suspend fun deleteDailyList(id: Long)

    @Query("DELETE FROM daily_lists")
    suspend fun deleteAllDailyLists()

    /** Delete a whole card and its items in one transaction. */
    @Transaction
    suspend fun deleteDailyListWithItems(id: Long) {
        deleteItemsForDailyList(id)
        deleteDailyList(id)
    }

    /** Mark that this card's once-per-day maintenance pass already ran on [runOn]. */
    @Query("UPDATE daily_lists SET maintenanceRunOn = :runOn WHERE id = :id")
    suspend fun setMaintenanceRunOn(id: Long, runOn: LocalDate)

    /** Mark that this card's one-time automatic renewal already ran on [runOn]. */
    @Query("UPDATE daily_lists SET renewalRunOn = :runOn WHERE id = :id")
    suspend fun markRenewalRun(id: Long, runOn: LocalDate)

    /** Record (or re-record) a genuine all-tasks-completed achievement. */
    @Query("UPDATE daily_lists SET genuinelyCompleted = :earned WHERE id = :id")
    suspend fun setGenuinelyCompleted(id: Long, earned: Boolean)

    /**
     * Permanently block a card from newly earning the completion celebration
     * after destructive cleanup removed its unfinished evidence.
     */
    @Query("UPDATE daily_lists SET completionBlockedByCleanup = 1 WHERE id = :id")
    suspend fun setNeverEarnsCompletion(id: Long)

    @Query("UPDATE daily_lists SET favorited = :favorited WHERE id = :id")
    suspend fun setFavorited(id: Long, favorited: Boolean)

    @Query("UPDATE daily_lists SET title = :title WHERE id = :id")
    suspend fun setTitle(id: Long, title: String)

    // --- Items ---------------------------------------------------------------

    @Insert
    suspend fun insertItem(item: DailyListItem): Long

    @Update
    suspend fun updateItem(item: DailyListItem)

    @Query("UPDATE daily_list_items SET text = :text WHERE id = :id")
    suspend fun updateItemText(id: Long, text: String)

    @Query("UPDATE daily_list_items SET completed = :completed WHERE id = :id")
    suspend fun setItemCompleted(id: Long, completed: Boolean)

    @Query("UPDATE daily_list_items SET indent = :indent WHERE id = :id")
    suspend fun setItemIndent(id: Long, indent: Int)

    @Query("UPDATE daily_list_items SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    @Query("DELETE FROM daily_list_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    /** Items of one card in display order. */
    @Query("SELECT * FROM daily_list_items WHERE dailyListId = :dailyListId ORDER BY position ASC, id ASC")
    fun observeItems(dailyListId: Long): Flow<List<DailyListItem>>

    @Query("SELECT * FROM daily_list_items WHERE dailyListId = :dailyListId ORDER BY position ASC, id ASC")
    suspend fun getItemsOnce(dailyListId: Long): List<DailyListItem>

    /** Removes every item of one card. */
    @Query("DELETE FROM daily_list_items WHERE dailyListId = :dailyListId")
    suspend fun deleteItemsForDailyList(dailyListId: Long)

    /** Removes items left completely blank when leaving an editor. */
    @Query("DELETE FROM daily_list_items WHERE dailyListId = :dailyListId AND text = ''")
    suspend fun deleteBlankItems(dailyListId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM daily_list_items WHERE dailyListId = :dailyListId")
    suspend fun maxPosition(dailyListId: Long): Int

    /** Every item of every card, oldest card first — renewal/backfill scans. */
    @Query(
        "SELECT i.* FROM daily_list_items i JOIN daily_lists l ON i.dailyListId = l.id " +
            "ORDER BY l.date ASC, i.position ASC, i.id ASC",
    )
    suspend fun getAllItemsOnce(): List<DailyListItem>

    /** Persist a new ordering by renumbering 0..n-1 in one transaction. */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setItemPosition(id, index) }
    }
}
