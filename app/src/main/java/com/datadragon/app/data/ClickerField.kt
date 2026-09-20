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
    /** Click Tracker: the value a fresh card's count starts at (up to 5 digits). */
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
    /** Write-In Number: the maximum number of digits the value may have. */
    val maxDigits: Int? = null,
    /** Write-In Number: offer a card-face button that steps the value. */
    val autoIncrement: Boolean = false,
)

/**
 * The set of field types a Clicker Data Log can hold. The serialized [token] is
 * the stable string stored in JSON.
 *
 * The number trackers are editable on the card face for quick changes; the
 * date/time and text types are display-only on the face and are changed only
 * from a card's edit menu.
 */
@Serializable
enum class ClickerFieldType(val token: String) {
    CLICK_TRACKER("click_tracker"),
    WRITE_IN_NUMBER("write_in_number"),
    DATE("date"),
    TIME("time"),
    DATE_TIME("date_time"),
    TEXT("text"),
    MULTITEXT("multitext");

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
 * the card face. The number trackers are the only face-editable types.
 */
val ClickerFieldType.editOnlyFromCardMenu: Boolean
    get() = when (this) {
        ClickerFieldType.CLICK_TRACKER, ClickerFieldType.WRITE_IN_NUMBER -> false
        ClickerFieldType.DATE, ClickerFieldType.TIME, ClickerFieldType.DATE_TIME,
        ClickerFieldType.TEXT, ClickerFieldType.MULTITEXT -> true
    }
