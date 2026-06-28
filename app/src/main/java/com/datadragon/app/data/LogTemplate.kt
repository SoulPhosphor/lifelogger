package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined log system.
 *
 * Fields: id, name, createdAt, schemaJson, formMarkdown.
 *
 * `schemaJson` holds the parsed field definitions (a JSON array of [FieldDef]).
 * `formMarkdown` keeps the original pasted text so it can be shown again and
 * included in `.json` exports (docs/FORM_MARKDOWN_SPEC.md §6).
 *
 * Templates cannot be edited after creation — only created, read, and deleted.
 * (Logs have no description.)
 */
@Entity(tableName = "log_templates")
data class LogTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val schemaJson: String,
    val formMarkdown: String = "",
)
