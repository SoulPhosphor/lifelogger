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

    /**
     * Saved lists in creation order for the Home screen. Drafts (a new list
     * persisted only as crash protection) are excluded — they never appear as
     * ordinary lists until Save finalizes them.
     */
    @Query("SELECT * FROM checklists WHERE draft = 0 ORDER BY createdAt ASC, id ASC")
    fun observeChecklists(): Flow<List<Checklist>>

    @Query("SELECT * FROM checklists WHERE id = :id")
    fun observeChecklist(id: Long): Flow<Checklist?>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklist(id: Long): Checklist?

    /** Finalize a draft into a normal saved list (invoked by Save). */
    @Query("UPDATE checklists SET draft = 0 WHERE id = :id")
    suspend fun finalizeChecklist(id: Long)

    /** The most recent unfinished draft, for the crash-recovery prompt on launch. */
    @Query("SELECT * FROM checklists WHERE draft = 1 ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun mostRecentDraft(): Checklist?

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: Long)

    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId")
    suspend fun deleteItemsForChecklist(checklistId: Long)

    /**
     * Delete a whole list — its items and the list row — in one transaction.
     * Used to discard a draft (there is no foreign-key cascade, so items must be
     * removed explicitly).
     */
    @Transaction
    suspend fun deleteChecklistWithItems(id: Long) {
        deleteItemsForChecklist(id)
        deleteChecklist(id)
    }

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
