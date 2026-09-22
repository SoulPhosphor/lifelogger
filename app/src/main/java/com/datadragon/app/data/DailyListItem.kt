package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One task row in a [DailyList], mirroring the ordinary list item's shape so
 * the Daily List editor can reuse the ordinary List interaction model.
 *
 * Items are a single flat, ordered sequence by [position]; [indent] (0 or 1)
 * captures the one allowed nesting level exactly as [ChecklistItem] does. For
 * Daily List purposes a *sequence* is one top-level item plus every sub-item
 * directly following it until the next top-level item — sequence completion
 * moves whole sequences, not individual rows.
 *
 * [sourceUuid] marks a row that renewal carried forward: it holds the stable
 * [DailyList.uuid] (not the row id — ids differ across days) of the source card
 * and, after the separator, the source item's stable uuid, so pressing Cycle
 * twice (or re-running renewal) can skip what was already carried without
 * duplicating it and without using visible task text as identity. It is null
 * for a row the user typed on this day.
 */
@Entity(
    tableName = "daily_list_items",
    indices = [
        Index("dailyListId"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class DailyListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dailyListId: Long,
    val uuid: String,
    val text: String = "",
    val completed: Boolean = false,
    val indent: Int = 0,
    val position: Int,
    val sourceUuid: String? = null,
)
