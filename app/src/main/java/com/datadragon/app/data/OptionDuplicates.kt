package com.datadragon.app.data

/**
 * The one duplicate rule for user-written selectable values inside Forms and
 * Ideas — Dropdown/Multiple options, Idea Categories, and Tags. Two items are the
 * same when they match after trimming, ignoring capitalization.
 *
 * Only *new* duplicates are refused. Values already saved with a historical
 * duplicate are left exactly as they are; see [introducesDuplicate].
 */
object OptionDuplicates {

    /** The owner's exact warning, shown with a single Okay button. */
    const val MESSAGE = "Sorry, that item can't be added as it already exists."

    /**
     * The comparison key for [item]. When [stripPreselectMarker] is true (a
     * Multiple field allowing pre-selected answers), a leading `*` is ignored so
     * `*Red` and `Red` count as the same option.
     */
    fun key(item: String, stripPreselectMarker: Boolean = false): String {
        val trimmed = item.trim()
        val bare = if (stripPreselectMarker && trimmed.startsWith("*")) {
            trimmed.removePrefix("*").trimStart()
        } else {
            trimmed
        }
        return bare.lowercase()
    }

    /** True when [candidate] matches any of [existing]. */
    fun contains(
        existing: Collection<String>,
        candidate: String,
        stripPreselectMarker: Boolean = false,
    ): Boolean {
        val wanted = key(candidate, stripPreselectMarker)
        return existing.any { key(it, stripPreselectMarker) == wanted }
    }

    /**
     * True when [updated] holds more copies of some item than [original] did —
     * i.e. the edit added a duplicate. A duplicate that was already saved (and is
     * kept unchanged) does not count, so historical data stays saveable.
     */
    fun introducesDuplicate(
        original: List<String>,
        updated: List<String>,
        stripPreselectMarker: Boolean = false,
    ): Boolean {
        val before = original.groupingBy { key(it, stripPreselectMarker) }.eachCount()
        return updated.groupingBy { key(it, stripPreselectMarker) }.eachCount()
            .any { (k, count) -> count > 1 && count > (before[k] ?: 0) }
    }
}

/** Whether a leading `*` marker is ignored when comparing this field's options. */
val FieldDef.stripsPreselectMarker: Boolean
    get() = type == FieldType.MULTIPLE && allowPreselectedMultipleChoices

/**
 * True when this Dropdown/Multiple field's options hold a duplicate that
 * [originalOptions] did not already have. Other field types never do.
 */
fun FieldDef.introducesOptionDuplicate(originalOptions: List<String> = emptyList()): Boolean =
    (type == FieldType.DROPDOWN || type == FieldType.MULTIPLE) &&
        OptionDuplicates.introducesDuplicate(originalOptions, options, stripsPreselectMarker)
