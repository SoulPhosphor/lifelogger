package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogTemplate].
 *
 * A template's fields and name are never edited after creation. The only
 * permitted mutation is [unlock]: a one-way flip of `locked` from true to false.
 */
@Dao
interface LogTemplateDao {

    @Insert
    suspend fun insert(template: LogTemplate): Long

    /** One-way unlock: a locked log becomes editable and can never be re-locked. */
    @Query("UPDATE log_templates SET locked = 0 WHERE id = :id")
    suspend fun unlock(id: Long)

    /** Templates in creation order — never resorted (docs/UI_SPEC.md §2). */
    @Query("SELECT * FROM log_templates ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<LogTemplate>>

    @Query("SELECT * FROM log_templates WHERE id = :id")
    suspend fun getById(id: Long): LogTemplate?

    /** One-shot snapshot of every template, for building a backup. */
    @Query("SELECT * FROM log_templates ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<LogTemplate>

    @Delete
    suspend fun delete(template: LogTemplate)

    /** Clears all templates — used by Restore, which replaces all data. */
    @Query("DELETE FROM log_templates")
    suspend fun deleteAll()
}
