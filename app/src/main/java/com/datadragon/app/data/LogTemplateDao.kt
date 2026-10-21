package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access for [LogTemplate].
 *
 * A template's title and field schema can be edited after creation. [setLocked]
 * turns locking on and off, except on a log created permanently locked.
 */
@Dao
interface LogTemplateDao {

    @Insert
    suspend fun insert(template: LogTemplate): Long

    /**
     * Lock or unlock a log's saved entries. A log created with Never Allow Form
     * Unlocking always stays locked, whatever [locked] asks for.
     */
    @Query(
        "UPDATE log_templates SET locked = CASE WHEN neverAllowUnlocking = 1 THEN 1 ELSE :locked END " +
            "WHERE id = :id",
    )
    suspend fun setLocked(id: Long, locked: Boolean)

    /** Turn follow-up notes on or off for a log (toggleable after creation). */
    @Query("UPDATE log_templates SET allowAppendedNotes = :allow WHERE id = :id")
    suspend fun setAllowAppendedNotes(id: Long, allow: Boolean)

    /** Rename a form while keeping its readable Form Markdown in sync. */
    @Query("UPDATE log_templates SET name = :name, formMarkdown = :formMarkdown WHERE id = :id")
    suspend fun rename(id: Long, name: String, formMarkdown: String)

    /**
     * Replace a log's field schema (and its source Form Markdown). Entry values
     * are keyed by permanent field and option ids, so they stay attached.
     */
    @Query("UPDATE log_templates SET schemaJson = :schemaJson, formMarkdown = :formMarkdown WHERE id = :id")
    suspend fun updateSchema(id: Long, schemaJson: String, formMarkdown: String)

    /** Save a title and schema edit together from the Edit Form screen. */
    @Query(
        "UPDATE log_templates SET name = :name, schemaJson = :schemaJson, " +
            "formMarkdown = :formMarkdown, automaticTimestamping = :automaticTimestamping, " +
            "sortTimestampLabel = :sortTimestampFieldId, " +
            "sortNewestFirst = :sortNewestFirst, " +
            "integrateCalendar = :integrateCalendar, " +
            "allowAdditionsFromForm = :allowAdditionsFromForm, advancedSearchEnabled = :advancedSearchEnabled WHERE id = :id",
    )
    suspend fun updateForm(
        id: Long,
        name: String,
        schemaJson: String,
        formMarkdown: String,
        automaticTimestamping: Boolean,
        sortTimestampFieldId: String?,
        sortNewestFirst: Boolean,
        integrateCalendar: Boolean,
        allowAdditionsFromForm: Boolean,
        advancedSearchEnabled: Boolean = false,
    )

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
