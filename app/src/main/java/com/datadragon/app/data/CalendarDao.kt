package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Data access for [Calendar] — a form's configured calendar views. */
@Dao
interface CalendarDao {

    @Insert
    suspend fun insert(calendar: Calendar): Long

    @Update
    suspend fun update(calendar: Calendar)

    @Delete
    suspend fun delete(calendar: Calendar)

    /** A form's calendars in their configured order, observed for live updates. */
    @Query("SELECT * FROM calendars WHERE templateId = :templateId ORDER BY position ASC, id ASC")
    fun observeForTemplate(templateId: Long): Flow<List<Calendar>>

    /** One-shot snapshot of a form's calendars (e.g. for a backup). */
    @Query("SELECT * FROM calendars WHERE templateId = :templateId ORDER BY position ASC, id ASC")
    suspend fun getForTemplateOnce(templateId: Long): List<Calendar>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getById(id: Long): Calendar?

    /** Remove all of a form's calendars — used when a form is deleted. */
    @Query("DELETE FROM calendars WHERE templateId = :templateId")
    suspend fun deleteForTemplate(templateId: Long)

    /** One-shot snapshot of every calendar, for building a backup. */
    @Query("SELECT * FROM calendars ORDER BY templateId ASC, position ASC, id ASC")
    suspend fun getAllOnce(): List<Calendar>

    /** Clears all calendars — used by Restore, which replaces all data. */
    @Query("DELETE FROM calendars")
    suspend fun deleteAll()
}
