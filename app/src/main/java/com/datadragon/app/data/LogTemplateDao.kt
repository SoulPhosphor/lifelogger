package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogTemplate].
 *
 * Phase 2 needs create, read, and delete only. There is deliberately no update:
 * logs cannot be edited after creation (README / docs/UI_SPEC.md).
 */
@Dao
interface LogTemplateDao {

    @Insert
    suspend fun insert(template: LogTemplate): Long

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
