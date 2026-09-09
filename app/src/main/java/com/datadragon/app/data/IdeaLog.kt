package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-defined Idea Log — the Ideas counterpart of a form, with its own
 * persisted domain rather than a mode flag on [LogTemplate]. Ideas deliberately
 * has no locking and no follow-up notes, and carries display settings a form
 * doesn't have, so the two stay separate.
 *
 * `fieldsJson` holds the field definitions (a JSON array of [IdeaFieldDef]).
 *
 * [uuid] is the log's permanent, app-internal identity: generated once at
 * creation and never changed by a rename. It is never shown to the user.
 *
 * The display settings:
 * - [automaticTimestamping]: show the automatic entry timestamp on idea cards.
 * - [allowArchiving]: ideas can be archived and unarchived. Archiving never
 *   replaces deletion — an archived idea can still be permanently deleted.
 * - [showEntireIdeaCard]: cards show every populated field at full length
 *   (Entire Idea Card mode) instead of only the Preview Mode fields.
 * - [previewLines]: how many rendered lines of a multiline field a card shows
 *   while in Preview Mode. Display only — stored text is never truncated.
 *
 * [sortTimestampFieldId] names the field (by its stable [IdeaFieldDef.id]) used
 * as the default sort timestamp, or null to fall back to `createdAt`.
 */
@Entity(tableName = "idea_logs")
data class IdeaLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long,
    val fieldsJson: String,
    val automaticTimestamping: Boolean = false,
    val allowArchiving: Boolean = false,
    val showEntireIdeaCard: Boolean = false,
    val previewLines: Int = DEFAULT_IDEA_LINES,
    val sortTimestampFieldId: String? = null,
    val sortNewestFirst: Boolean = true,
)
