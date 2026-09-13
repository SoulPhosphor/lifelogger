package com.datadragon.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One configured calendar view attached to a form ([LogTemplate.integrateCalendar]).
 *
 * A form can have more than one calendar; [position] keeps their order (the order
 * they were added / appear at the bottom of Edit Form and in the View Calendar
 * dropdown). [type] is a [CalendarType] token.
 *
 * [configJson] holds the type-specific configuration (data source, calculation
 * rule and its optional condition, color-value count, preset, per-range colors,
 * Yes/No option rows, etc.). It is kept as a JSON blob so the calendar's shape can
 * grow through the later phases without a schema change each time. Empty until the
 * type-specific controls are configured.
 */
@Entity(
    tableName = "calendars",
    indices = [Index("templateId")],
)
data class Calendar(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int = 0,
    val type: String,
    val label: String,
    val description: String = "",
    val configJson: String = "",
)

/**
 * The calendar types the user picks in "Choose Calendar Type". The token is the
 * stable value persisted in [Calendar.type]; the display names are the exact
 * owner-facing labels ("Heat Map", "Yes/No") and live with the UI.
 *
 * There is no separate Min/Max type: Highest Value and Lowest Value are rules in
 * the shared Calculation Rule dropdown for a numeric/scale source.
 */
enum class CalendarType(val token: String) {
    HEAT_MAP("heat_map"),
    YES_NO("yes_no");

    companion object {
        fun fromToken(token: String): CalendarType? =
            entries.firstOrNull { it.token == token }
    }
}
