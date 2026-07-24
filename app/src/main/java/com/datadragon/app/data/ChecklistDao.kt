package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [Checklist] and its [ChecklistItem]s.
 *
 * Everything here is freely editable — lists and their items are create / read /
 * update / delete, with no locking (unlike [LogTemplate]).
 */
@Dao
interface ChecklistDao {

    // --- Checklists -------------------------------------------------------

    @Insert
    suspend fun insertChecklist(checklist: Checklist): Long

    @Query("UPDATE checklists SET name = :name WHERE id = :id")
    suspend fun renameChecklist(id: Long, name: String)

    /** Lists in creation order — same as forms on the Home screen. */
    @Query("SELECT * FROM checklists ORDER BY createdAt ASC, id ASC")
    fun observeChecklists(): Flow<List<Checklist>>

    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observeChecklist(id: Long): Flow<Checklist?>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklist(id: Long): Checklist?

    /** One-shot snapshot of every list, for building a backup. */
    @Query("SELECT * FROM checklists ORDER BY createdAt ASC, id ASC")
    suspend fun getAllChecklistsOnce(): List<Checklist>

    /** Find a list by its permanent uuid — used to match lists on a Merge restore. */
    @Query("SELECT * FROM checklists WHERE uuid = :uuid LIMIT 1")
    suspend fun getChecklistByUuid(uuid: String): Checklist?

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    /** Clears all lists — used by a Replace restore, which replaces all data. */
    @Query("DELETE FROM checklists")
    suspend fun deleteAllChecklists()

    // --- Items ------------------------------------------------------------

    @Insert
    suspend fun insertItem(item: ChecklistItem): Long

    @Update
    suspend fun updateItem(item: ChecklistItem)

    @Query("UPDATE checklist_items SET text = :text WHERE id = :id")
    suspend fun updateItemText(id: Long, text: String)

    @Query("UPDATE checklist_items SET completed = :completed WHERE id = :id")
    suspend fun setItemCompleted(id: Long, completed: Boolean)

    @Query("UPDATE checklist_items SET indent = :indent WHERE id = :id")
    suspend fun setItemIndent(id: Long, indent: Int)

    @Query("UPDATE checklist_items SET position = :position WHERE id = :id")
    suspend fun setItemPosition(id: Long, position: Int)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    /** Items of one list in display order. */
    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position ASC, id ASC")
    fun observeItems(checklistId: Long): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY position ASC, id ASC")
    suspend fun getItemsOnce(checklistId: Long): List<ChecklistItem>

    /** One-shot snapshot of every list's items, for building a backup. */
    @Query("SELECT * FROM checklist_items ORDER BY checklistId ASC, position ASC, id ASC")
    suspend fun getAllItemsOnce(): List<ChecklistItem>

    /** Removes every item of one list — used when replacing that list on restore. */
    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId")
    suspend fun deleteItemsForChecklist(checklistId: Long)

    /** Clears all list items — used by a Replace restore, which replaces all data. */
    @Query("DELETE FROM checklist_items")
    suspend fun deleteAllItems()

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getItem(id: Long): ChecklistItem?

    @Query("SELECT COALESCE(MAX(position), -1) FROM checklist_items WHERE checklistId = :checklistId")
    suspend fun maxPosition(checklistId: Long): Int

    /** Removes items left completely empty (used when leaving a list). */
    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId AND text = ''")
    suspend fun deleteBlankItems(checklistId: Long)

    /**
     * Persist a new ordering by writing each id's index as its position, so the
     * whole list is renumbered 0..n-1 in one transaction after a drag.
     */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setItemPosition(id, index) }
    }
}
