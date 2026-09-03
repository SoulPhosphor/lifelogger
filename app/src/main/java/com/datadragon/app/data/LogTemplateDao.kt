package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogTemplate].
 *
 * A template's title and field schema can be edited after creation. [unlock]
 * remains a one-way flip of `locked` from true to false.
 */
@Dao
interface LogTemplateDao {

    @Insert
    suspend fun insert(template: LogTemplate): Long

    /** One-way unlock: a locked log becomes editable and can never be re-locked. */
    @Query("UPDATE log_templates SET locked = 0 WHERE id = :id")
    suspend fun unlock(id: Long)

    /** Turn follow-up notes on or off for a log (toggleable after creation). */
    @Query("UPDATE log_templates SET allowAppendedNotes = :allow WHERE id = :id")
    suspend fun setAllowAppendedNotes(id: Long, allow: Boolean)

    /** Rename a form while keeping its readable Form Markdown in sync. */
    @Query("UPDATE log_templates SET name = :name, formMarkdown = :formMarkdown WHERE id = :id")
    suspend fun rename(id: Long, name: String, formMarkdown: String)

    /**
     * Replace a log's field schema (and its source Form Markdown). Used by "Edit
     * form", which may only add fields and reorder them — never rename or delete
     * an existing field — so stored entry values stay keyed correctly.
     */
    @Query("UPDATE log_templates SET schemaJson = :schemaJson, formMarkdown = :formMarkdown WHERE id = :id")
    suspend fun updateSchema(id: Long, schemaJson: String, formMarkdown: String)

    /** Save a title and schema edit together from the Edit Form screen. */
    @Query(
        "UPDATE log_templates SET name = :name, schemaJson = :schemaJson, " +
            "formMarkdown = :formMarkdown WHERE id = :id",
    )
    suspend fun updateForm(id: Long, name: String, schemaJson: String, formMarkdown: String)

    /** Templates in creation order — never resorted (docs/UI_SPEC.md §2). */
    @Query("SELECT * FROM log_templates ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<LogTemplate>>

    @Query("SELECT * FROM log_templates WHERE id = :id")
    suspend fun getById(id: Long): LogTemplate?

    /** Find a log by its permanent uuid — used to match logs on a Merge restore. */
    @Query("SELECT * FROM log_templates WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): LogTemplate?

    /** One-shot snapshot of every template, for building a backup. */
    @Query("SELECT * FROM log_templates ORDER BY createdAt ASC, id ASC")
    suspend fun getAllOnce(): List<LogTemplate>

    @Delete
    suspend fun delete(template: LogTemplate)

    /** Clears all templates — used by Restore, which replaces all data. */
    @Query("DELETE FROM log_templates")
    suspend fun deleteAll()
}
