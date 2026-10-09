package com.datadragon.app.data

import androidx.room.withTransaction

/** Reuses the ordinary Lists draft/autosave engine without storing completion or nesting. */
class RoomLuckyListStore(private val db: AppDatabase, private val exclusion: () -> Boolean) : ChecklistStore {
    private val dao = db.luckyListDao()
    override suspend fun getChecklist(id: Long) = dao.getList(id)?.asChecklist()
    override suspend fun getItemsOnce(checklistId: Long) = dao.getItemsOnce(checklistId).map { it.asChecklistItem() }
    override suspend fun renameChecklist(id: Long, name: String) = dao.rename(id, name)
    override suspend fun insertItem(item: ChecklistItem) = dao.insertItem(LuckyListItem(luckyListId = item.checklistId, text = item.text, position = item.position))
    override suspend fun updateItemText(id: Long, text: String) = dao.updateText(id, text)
    override suspend fun setItemCompleted(id: Long, completed: Boolean) { error("Lucky items have no completion state") }
    override suspend fun deleteItem(id: Long) = dao.deleteItem(id)
    override suspend fun applyOrder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)
    override suspend fun deleteBlankItems(checklistId: Long) = dao.deleteBlankItems(checklistId)
    override suspend fun finalizeChecklist(id: Long) = dao.finalizeList(id)
    override suspend fun deleteChecklistWithItems(id: Long) = dao.deleteWithItems(id)
    override suspend fun mostRecentDraft() = dao.mostRecentDraft()?.asChecklist()
    override suspend fun createDraftWithItems(name: String, createdAt: Long, items: List<ChecklistItem>): CreatedList = db.withTransaction {
        val id = dao.insertList(LuckyList(uuid = StableUuid.createNew(), name = name, createdAt = createdAt, draft = true, excludePreviouslySelected = exclusion()))
        CreatedList(id, items.mapIndexed { index, item -> dao.insertItem(LuckyListItem(luckyListId = id, text = item.text, position = index)) })
    }
}
