package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Clicker Data Log — one "folder" of daily cards that all share the same set
 * of trackers and fields. Its own persisted domain, like an Idea Log, rather
 * than a flag on another table.
 *
 * `fieldsJson` holds the tracker/field definitions (a JSON array of
 * [ClickerField]). Each [ClickerCard] keeps its own values for those fields.
 *
 * [uuid] is the log's permanent, app-internal identity: generated once and never
 * changed by a rename. It is never shown to the user.
 *
 * [lastAccessedAt] is bumped whenever the log is opened or a card in it changes,
 * so the Clicker home list can show the most recently used log at the top.
 *
 * [lastModifiedAt] is the "Last Saved" time shown on the grouping's Home row. It
 * is bumped only when a card's content changes — a card added, a tracker
 * stepped, a value/date/text edited, or a card deleted — never by merely opening
 * the grouping and never by renaming the grouping's title. That is what
 * separates it from [lastAccessedAt].
 *
 * Display settings:
 * - [displayOnlyClickerDateTime]: when on, the card face shows only the trackers
 *   and the date/time stamp; text and multitext fields are hidden on the face
 *   (still editable from the card's edit menu). When off, those fields also show
 *   on the face, display-only.
 * - [autoDateStamp] / [autoTimeStamp]: whether the created date / time is shown
 *   at the top of each card. The real creation instant is always kept in
 *   [ClickerCard.createdAt] regardless, so cards can be ordered either way.
 * - [allowFollowUp]: cards may carry follow-up notes, added from the edit menu.
 */
@Entity(
    tableName = "clicker_logs",
    indices = [Index(value = ["uuid"], unique = true)],
)
data class ClickerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val title: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val lastModifiedAt: Long = createdAt,
    val fieldsJson: String,
    val displayOnlyClickerDateTime: Boolean = false,
    val autoDateStamp: Boolean = true,
    val autoTimeStamp: Boolean = false,
    val allowFollowUp: Boolean = false,
)

/**
 * One card in a Clicker Data Log. A card is added manually and can be used over
 * any span of days — cards are never auto-created for a date.
 *
 * `valuesJson` holds this card's values for the log's fields, keyed by
 * [ClickerField.id].
 *
 * [createdAt] is the real creation instant, always stored so cards can be
 * ordered chronologically. [displayDate] / [displayTime] are the shown stamps
 * (ISO `yyyy-MM-dd` / `HH:mm`); they start from the creation instant but can be
 * overridden from the card's edit menu, and are null when the log doesn't stamp
 * that part.
 */
@Entity(
    tableName = "clicker_cards",
    indices = [
        Index("clickerLogId"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class ClickerCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clickerLogId: Long,
    val uuid: String,
    val createdAt: Long,
    val displayDate: String? = null,
    val displayTime: String? = null,
    val valuesJson: String,
)
