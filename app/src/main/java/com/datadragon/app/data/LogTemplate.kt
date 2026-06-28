package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined log system.
 *
 * Fields are exactly those required by Phase 2 of docs/BUILD_PHASES.md:
 * id, name, description, createdAt, schemaJson.
 *
 * `schemaJson` holds the parsed field definitions. The Form Markdown parser that
 * fills it arrives in Phase 3; until then new templates are stored with an empty
 * schema (`"[]"`).
 *
 * Templates cannot be edited after creation — only created, read, and deleted.
 */
@Entity(tableName = "log_templates")
data class LogTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val createdAt: Long,
    val schemaJson: String,
)
