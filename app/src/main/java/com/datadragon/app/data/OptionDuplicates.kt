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

/** True when two options share a name under the duplicate rule. */
fun List<ChoiceOption>.hasDuplicateLabels(): Boolean =
    map { OptionDuplicates.key(it.label) }.let { it.size != it.distinct().size }

/**
 * The option in this list whose label is the same as [label] under the
 * duplicate rule (trimmed, ignoring capitalization), skipping [exceptId] — the
 * option being renamed. Active and retired options are both searched, so a name
 * can never be reused while its retired option still exists.
 */
fun List<ChoiceOption>.matchingLabel(label: String, exceptId: String? = null): ChoiceOption? {
    val wanted = OptionDuplicates.key(label)
    return firstOrNull { it.id != exceptId && OptionDuplicates.key(it.label) == wanted }
}

/**
 * What adding or renaming to a label would collide with: nothing, an active
 * option (refused with the duplicate notice), or a retired option (offered for
 * restoring instead).
 */
sealed interface ChoiceNameCheck {
    data object Available : ChoiceNameCheck
    data class ActiveDuplicate(val option: ChoiceOption) : ChoiceNameCheck
    data class RetiredDuplicate(val option: ChoiceOption) : ChoiceNameCheck
}

/** Classify [label] against this option list, ignoring the option [exceptId]. */
fun List<ChoiceOption>.checkName(label: String, exceptId: String? = null): ChoiceNameCheck =
    when (val match = matchingLabel(label, exceptId)) {
        null -> ChoiceNameCheck.Available
        else -> if (match.retired) {
            ChoiceNameCheck.RetiredDuplicate(match)
        } else {
            ChoiceNameCheck.ActiveDuplicate(match)
        }
    }
