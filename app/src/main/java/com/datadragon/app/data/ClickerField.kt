package com.datadragon.app.data

import kotlinx.serialization.Serializable

/**
 * One tracker or field in a Clicker Data Log's setup. The whole set is stored as
 * a JSON array in [ClickerLog.fieldsJson]; each card then keeps its own values
 * for these fields (keyed by [id]) in its own values JSON.
 *
 * A single data class carries every type's settings; the ones that don't apply
 * to a given [type] stay at their defaults (mirroring [FieldDef]).
 *
 * [id] is a stable, app-internal identity assigned once when the field is
 * created, so a card's stored value stays attached to the right field even if
 * the label is renamed later. It is never shown to the user.
 */
@Serializable
data class ClickerField(
    val id: String,
    val type: ClickerFieldType,
    val label: String,
    /**
     * Click Tracker and Write-In auto-increment: the caption on the card-face
     * button. Empty means the type's default is shown ("Add" for a Click
     * Tracker, "Okay" for a Write-In auto-increment button).
     */
    val buttonLabel: String = "",
    /**
     * Click Tracker, and Comparison with [useClickerCounting]: the value a fresh
     * card's count (a Comparison row's second column) starts at (up to 5 digits).
     */
    val startingNumber: Int = 0,
    /**
     * Click Tracker, and Write-In when [autoIncrement] is on: whether the button
     * adds to or subtracts from the running value.
     */
    val incrementDirection: ClickerIncrementDirection = ClickerIncrementDirection.ADD,
    /**
     * Click Tracker, and Write-In when [autoIncrement] is on: how much each
     * button press changes the value (up to 4 digits).
     */
    val incrementAmount: Int = 1,
    /** Write-In Number and Comparison: the maximum number of digits a typed value may have. */
    val maxDigits: Int? = null,
    /** Write-In Number: offer a card-face button that steps the value. */
    val autoIncrement: Boolean = false,
    /** Comparison: the items, active and retired, each with a permanent id ([FieldDef.choices]). */
    val choices: List<ChoiceOption> = emptyList(),
    /** Comparison: the column and total captions; blank shows the default. */
    val itemLabel: String = "",
    val firstLabel: String = "",
    val secondLabel: String = "",
    val totalLabel: String = "",
    /** Comparison: the typed number columns take whole numbers only. */
    val wholeNumbersOnly: Boolean = true,
    /**
     * Comparison: the second column counts with this field's button, set by
     * [buttonLabel], [startingNumber], [incrementDirection] and [incrementAmount].
     */
    val useClickerCounting: Boolean = false,
    /** Comparison: "Show Average of Differences" under the total ([FieldDef.showAverage]). */
    val showAverage: Boolean = false,
)

/**
 * A Comparison field as the shared Comparison controls and values read it. The
 * Clicker field keeps its own id, label and items; its Click Tracker settings
 * double as the Comparison's clicker counting settings.
 */
fun ClickerField.comparisonDef(): FieldDef = FieldDef(
    id = id,
    label = label,
    type = FieldType.COMPARISON,
    digits = maxDigits,
    choices = choices,
    itemLabel = itemLabel,
    firstLabel = firstLabel,
    secondLabel = secondLabel,
    totalLabel = totalLabel,
    wholeNumbersOnly = wholeNumbersOnly,
    clickerCounting = useClickerCounting,
    clickerButtonLabel = buttonLabel,
    clickerStartingNumber = startingNumber,
    clickerIncrement = incrementAmount,
    clickerDirection = incrementDirection,
    showAverage = showAverage,
)

/**
 * The set of field types a Clicker Data Log can hold. The serialized [token] is
 * the stable string stored in JSON.
 *
 * The number trackers and the text types are editable on the card face; the
 * date/time types are display-only on the face and are changed only from a
 * card's edit menu.
 */
@Serializable
enum class ClickerFieldType(val token: String) {
    CLICK_TRACKER("click_tracker"),
    WRITE_IN_NUMBER("write_in_number"),
    DATE("date"),
    TIME("time"),
    DATE_TIME("date_time"),
    TEXT("text"),
    MULTITEXT("multitext"),
    COMPARISON("comparison");

    companion object {
        fun fromToken(token: String): ClickerFieldType? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}

/** Whether a stepping button adds to or subtracts from a tracker's value. */
@Serializable
enum class ClickerIncrementDirection(val token: String) {
    ADD("add"),
    SUBTRACT("subtract");

    companion object {
        fun fromToken(token: String): ClickerIncrementDirection =
            entries.firstOrNull { it.token == token.trim().lowercase() } ?: ADD
    }
}

/**
 * True for the types that can only be changed from a card's edit menu, never on
 * the card face. The number trackers and the text types are face-editable.
 */
val ClickerFieldType.editOnlyFromCardMenu: Boolean
    get() = when (this) {
        ClickerFieldType.CLICK_TRACKER, ClickerFieldType.WRITE_IN_NUMBER,
        ClickerFieldType.TEXT, ClickerFieldType.MULTITEXT, ClickerFieldType.COMPARISON -> false
        ClickerFieldType.DATE, ClickerFieldType.TIME, ClickerFieldType.DATE_TIME -> true
    }
