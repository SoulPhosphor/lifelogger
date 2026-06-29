package com.datadragon.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined log system.
 *
 * `schemaJson` holds the parsed field definitions (a JSON array of [FieldDef]).
 * `formMarkdown` keeps the original pasted text so it can be shown again and
 * included in `.json` exports (docs/FORM_MARKDOWN_SPEC.md §6). The template's
 * fields and name themselves are never edited — only created, read, and deleted.
 *
 * [locked] / [allowAppendedNotes] are chosen at creation and govern its entries:
 * - `locked` (default true): entries are create-once and can't be edited. It is
 *   one-way — a locked log can be unlocked, but an unlocked log can never be
 *   re-locked, so a log still showing as locked has never been editable.
 * - `allowAppendedNotes` (default false): entries may have append-only,
 *   time-stamped follow-up notes added later (see [EntryNote]).
 */
@Entity(tableName = "log_templates")
data class LogTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val schemaJson: String,
    val formMarkdown: String = "",
    @ColumnInfo(defaultValue = "1")
    val locked: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val allowAppendedNotes: Boolean = false,
)
