package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row in a [Checklist].
 *
 * The items of a list are a single flat, ordered sequence (by [position]).
 * [indent] captures nesting purely as a visual level — 0 is a top-level item and
 * 1 is a sub-item shown indented under the item above it. Nesting is only ever
 * one level deep, so [indent] is only ever 0 or 1. Keeping the list flat (rather
 * than a real parent/child tree) means dragging a row anywhere "just works": an
 * item keeps its own indent wherever it lands.
 *
 * [completed] drives the check state; how a completed item looks (checkmark vs
 * checked box), whether its text is struck through, and whether it jumps to the
 * bottom are all governed by global settings in [SettingsRepository].
 */
@Entity(
    tableName = "checklist_items",
    indices = [Index("checklistId")],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val text: String = "",
    val completed: Boolean = false,
    val indent: Int = 0,
    val position: Int,
)
