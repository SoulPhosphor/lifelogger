package com.datadragon.app.data

import androidx.room.ForeignKey
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lucky_lists", indices = [Index(value = ["uuid"], unique = true)])
data class LuckyList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String = "",
    val createdAt: Long,
    val draft: Boolean = false,
    val excludePreviouslySelected: Boolean = false,
)

@Entity(tableName = "lucky_list_items", indices = [Index("luckyListId")], foreignKeys = [ForeignKey(entity = LuckyList::class, parentColumns = ["id"], childColumns = ["luckyListId"], onDelete = ForeignKey.CASCADE)])
data class LuckyListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val luckyListId: Long,
    val text: String = "",
    val position: Int,
)

internal fun LuckyList.asChecklist() = Checklist(id, uuid, name, createdAt, draft)
internal fun LuckyListItem.asChecklistItem() = ChecklistItem(id = id, checklistId = luckyListId, text = text, position = position)
