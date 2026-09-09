package com.datadragon.app.ui

import com.datadragon.app.data.FieldType
import com.datadragon.app.data.IdeaEntry
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
import com.datadragon.app.data.IdeaValues
import com.datadragon.app.data.sortEligible
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Ordering for Idea Logs. This is the same sorting system Forms uses — the same
 * eligibility rule (a field must carry a date), the same "Allow Order Filtering"
 * opt-in, the same default-sort-timestamp and default direction — addressed by
 * a field's stable id instead of its label.
 */

/** One ordering the filter bar can offer. A null [field] is the automatic timestamp. */
data class IdeaSortCategory(val label: String, val field: IdeaFieldDef?)

/** The automatic entry timestamp, which every Idea Log can always sort by. */
val IDEA_TIMESTAMP_CATEGORY = IdeaSortCategory(AUTOMATIC_TIMESTAMP_LABEL, null)

/**
 * The orderings this Idea Log offers: the automatic timestamp first, then every
 * date-bearing field opted in with "Allow Order Filtering", plus the log's own
 * default sort field whether or not it was also opted in.
 */
internal fun ideaSortCategories(fields: List<IdeaFieldDef>, log: IdeaLog?): List<IdeaSortCategory> {
    val default = ideaDefaultSortField(fields, log)
    val opted = fields.filter {
        it.type.sortEligible && (it.allowOrderFiltering || it.id == default?.id)
    }
    return listOf(IDEA_TIMESTAMP_CATEGORY) + opted.map { IdeaSortCategory(it.label, it) }
}

/**
 * The category in force: the user's pick while it still exists, otherwise the
 * log's default sort field, otherwise the automatic entry timestamp.
 */
internal fun resolveIdeaCategory(
    fields: List<IdeaFieldDef>,
    log: IdeaLog?,
    pickedLabel: String?,
): IdeaSortCategory {
    val categories = ideaSortCategories(fields, log)
    pickedLabel?.let { label ->
        categories.firstOrNull { it.label == label }?.let { return it }
    }
    val default = ideaDefaultSortField(fields, log)
    return categories.firstOrNull { it.field?.id == default?.id } ?: categories.first()
}

/**
 * The field the log sorts by, or null when it falls back to the automatic entry
 * timestamp. A stored id only counts while a sort-eligible field still carries
 * it, so deleting that field reverts the log to `createdAt`.
 */
internal fun ideaDefaultSortField(fields: List<IdeaFieldDef>, log: IdeaLog?): IdeaFieldDef? =
    log?.sortTimestampFieldId?.let { id ->
        fields.firstOrNull { it.type.sortEligible && it.id == id }
    }

/**
 * Order ideas by [sortField], or by the automatic entry timestamp when it is
 * null. Ideas with no usable value for the chosen field are never interleaved:
 * they collect at the bottom of the list in both directions.
 */
internal fun sortIdeaEntries(
    entries: List<IdeaEntry>,
    sortField: IdeaFieldDef?,
    newestFirst: Boolean,
): List<IdeaEntry> {
    val (timed, undated) = entries
        .map { it to ideaSortTime(it, sortField) }
        .partition { (_, time) -> time != null }
    val ordered = if (newestFirst) {
        timed.sortedWith(
            compareByDescending<Pair<IdeaEntry, LocalDateTime?>> { it.second }
                .thenByDescending { it.first.id },
        )
    } else {
        timed.sortedWith(
            compareBy<Pair<IdeaEntry, LocalDateTime?>> { it.second }.thenBy { it.first.id },
        )
    }
    val trailing = if (newestFirst) {
        undated.sortedByDescending { it.first.id }
    } else {
        undated.sortedBy { it.first.id }
    }
    return (ordered + trailing).map { it.first }
}

/**
 * The instant one idea sorts at, or null when it has nothing to sort by. A date
 * field with no time sorts at the start of its day, so a Date Only and a
 * Date/Time field order against each other consistently.
 */
private fun ideaSortTime(entry: IdeaEntry, sortField: IdeaFieldDef?): LocalDateTime? {
    if (sortField == null) {
        return runCatching { OffsetDateTime.parse(entry.createdAt).toLocalDateTime() }.getOrNull()
    }
    val values = IdeaValues.decode(entry.valuesJson)
    val raw = IdeaValues.rawValue(values, sortField) ?: return null
    return when (sortField.type) {
        FieldType.DATE ->
            runCatching {
                LocalDate.parse(raw, com.datadragon.app.data.EntryValues.DATE_STORAGE).atStartOfDay()
            }.getOrNull()
        FieldType.DATETIME ->
            runCatching {
                LocalDateTime.parse(raw, com.datadragon.app.data.EntryValues.DATETIME_STORAGE)
            }.getOrNull()
        else -> null
    }
}
