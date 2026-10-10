package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LuckyListDao {
    @Insert suspend fun insertList(list: LuckyList): Long
    @Insert suspend fun insertItem(item: LuckyListItem): Long
    @Query("SELECT * FROM lucky_lists WHERE draft = 0 ORDER BY createdAt, id")
    fun observeLists(): Flow<List<LuckyList>>
    @Query("SELECT * FROM lucky_lists WHERE id = :id") suspend fun getList(id: Long): LuckyList?
    @Query("SELECT * FROM lucky_lists WHERE uuid = :uuid") suspend fun getByUuid(uuid: String): LuckyList?
    @Query("SELECT * FROM lucky_lists ORDER BY createdAt, id") suspend fun getAllOnce(): List<LuckyList>
    @Query("SELECT * FROM lucky_lists WHERE draft = 1 ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun mostRecentDraft(): LuckyList?
    @Query("SELECT * FROM lucky_list_items WHERE luckyListId = :id ORDER BY position, id")
    suspend fun getItemsOnce(id: Long): List<LuckyListItem>
    @Query("SELECT * FROM lucky_list_items ORDER BY luckyListId, position, id")
    suspend fun getAllItemsOnce(): List<LuckyListItem>
    @Query("UPDATE lucky_lists SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE lucky_lists SET excludePreviouslySelected = :exclude WHERE id = :id")
    suspend fun setExclusion(id: Long, exclude: Boolean)
    @Query("UPDATE lucky_lists SET draft = 0 WHERE id = :id") suspend fun finalizeList(id: Long)
    @Query("UPDATE lucky_list_items SET text = :text WHERE id = :id") suspend fun updateText(id: Long, text: String)
    @Query("UPDATE lucky_list_items SET position = :position WHERE id = :id") suspend fun setPosition(id: Long, position: Int)
    @Query("DELETE FROM lucky_list_items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM lucky_list_items WHERE luckyListId = :id AND text = ''") suspend fun deleteBlankItems(id: Long)
    @Query("DELETE FROM lucky_list_items WHERE luckyListId = :id") suspend fun deleteItems(id: Long)
    @Query("DELETE FROM lucky_lists WHERE id = :id") suspend fun deleteList(id: Long)
    @Query("DELETE FROM lucky_list_items") suspend fun deleteAllItems()
    @Query("DELETE FROM lucky_lists") suspend fun deleteAllLists()
    @Transaction suspend fun deleteWithItems(id: Long) { deleteItems(id); deleteList(id) }
    @Transaction suspend fun applyOrder(ids: List<Long>) { ids.forEachIndexed { index, id -> setPosition(id, index) } }
}
