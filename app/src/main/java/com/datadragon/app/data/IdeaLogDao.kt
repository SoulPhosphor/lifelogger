package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Data access for [IdeaLog]. An Idea Log's name and fields stay editable. */
@Dao
interface IdeaLogDao {

    @Insert
    suspend fun insert(log: IdeaLog): Long

    /** Idea Logs in creation order — never resorted, like the Forms home list. */
    @Query("SELECT * FROM idea_logs ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<IdeaLog>>

    @Query("SELECT * FROM idea_logs WHERE id = :id")
    suspend fun getById(id: Long): IdeaLog?

    /** Rename an Idea Log from its own screen. */
    @Query("UPDATE idea_logs SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /** Save every editable setting and the field schema together. */
    @Query(
        "UPDATE idea_logs SET name = :name, fieldsJson = :fieldsJson, " +
            "automaticTimestamping = :automaticTimestamping, allowArchiving = :allowArchiving, " +
            "showEntireIdeaCard = :showEntireIdeaCard, previewLines = :previewLines, " +
            "sortTimestampFieldId = :sortTimestampFieldId, " +
            "sortNewestFirst = :sortNewestFirst WHERE id = :id",
    )
    suspend fun updateLog(
        id: Long,
        name: String,
        fieldsJson: String,
        automaticTimestamping: Boolean,
        allowArchiving: Boolean,
        showEntireIdeaCard: Boolean,
        previewLines: Int,
        sortTimestampFieldId: String?,
        sortNewestFirst: Boolean,
    )

    @Delete
    suspend fun delete(log: IdeaLog)
}
