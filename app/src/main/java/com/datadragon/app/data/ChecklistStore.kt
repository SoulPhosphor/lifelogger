package com.datadragon.app.data

import androidx.room.withTransaction

/**
 * Persistence boundary for the list editor. It exists so the editing logic in
 * [ChecklistDraftManager] can be exercised against a fake in tests, with the
 * real implementation ([RoomChecklistStore]) delegating to [ChecklistDao].
 */
interface ChecklistStore {
    suspend fun getChecklist(id: Long): Checklist?
    suspend fun getItemsOnce(checklistId: Long): List<ChecklistItem>
    suspend fun renameChecklist(id: Long, name: String)
    suspend fun insertItem(item: ChecklistItem): Long
    suspend fun updateItemText(id: Long, text: String)
    suspend fun setItemCompleted(id: Long, completed: Boolean)
    suspend fun deleteItem(id: Long)
    suspend fun applyOrder(orderedIds: List<Long>)
    suspend fun deleteBlankItems(checklistId: Long)

    /**
     * Create a **draft** list named [name] with [items] (in the given order) in a
     * single transaction. Returns the new list id and the new item ids in the same
     * order, so the caller can map its in-memory rows to their database rows. The
     * list is created with `draft = true` — this is crash protection only, not a
     * user Save.
     */
    suspend fun createDraftWithItems(name: String, createdAt: Long, items: List<ChecklistItem>): CreatedList

    /** Turn a draft into a normal saved list (invoked by Save). */
    suspend fun finalizeChecklist(id: Long)

    /** Delete a list and all its items in one transaction (invoked by Discard). */
    suspend fun deleteChecklistWithItems(id: Long)

    /** The most recent unfinished draft, for the crash-recovery prompt on launch. */
    suspend fun mostRecentDraft(): Checklist?
}

/** Result of [ChecklistStore.createDraftWithItems]: the new list id and its item ids, in order. */
data class CreatedList(val listId: Long, val itemIds: List<Long>)

/** Room-backed [ChecklistStore]. First-list creation runs in one transaction. */
class RoomChecklistStore(private val db: AppDatabase) : ChecklistStore {

    private val dao = db.checklistDao()

    override suspend fun getChecklist(id: Long): Checklist? = dao.getChecklist(id)

    override suspend fun getItemsOnce(checklistId: Long): List<ChecklistItem> = dao.getItemsOnce(checklistId)

    override suspend fun renameChecklist(id: Long, name: String) = dao.renameChecklist(id, name)

    override suspend fun insertItem(item: ChecklistItem): Long = dao.insertItem(item)

    override suspend fun updateItemText(id: Long, text: String) = dao.updateItemText(id, text)

    override suspend fun setItemCompleted(id: Long, completed: Boolean) = dao.setItemCompleted(id, completed)

    override suspend fun deleteItem(id: Long) = dao.deleteItem(id)

    override suspend fun applyOrder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)

    override suspend fun deleteBlankItems(checklistId: Long) = dao.deleteBlankItems(checklistId)

    override suspend fun createDraftWithItems(
        name: String,
        createdAt: Long,
        items: List<ChecklistItem>,
    ): CreatedList = db.withTransaction {
        val listId = dao.insertChecklist(Checklist(name = name, createdAt = createdAt, draft = true))
        val itemIds = items.mapIndexed { index, item ->
            dao.insertItem(item.copy(id = 0, checklistId = listId, position = index))
        }
        CreatedList(listId, itemIds)
    }

    override suspend fun finalizeChecklist(id: Long) = dao.finalizeChecklist(id)

    override suspend fun deleteChecklistWithItems(id: Long) = dao.deleteChecklistWithItems(id)

    override suspend fun mostRecentDraft(): Checklist? = dao.mostRecentDraft()
}
