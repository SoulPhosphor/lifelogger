package com.datadragon.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One parsed field in a log's schema. Serialized as part of the template's
 * `schemaJson` (a JSON array of these).
 *
 * The supported types and their variables mirror docs/FORM_MARKDOWN_SPEC.md
 * exactly. Variables that don't apply to a given type stay null/empty.
 *
 * [id] is the field's permanent identity and the key its values are stored
 * under in each entry, so renaming or reordering a field never disconnects it
 * from its saved answers. It is generated once when the field is created and
 * never changes; it is never shown to the user. It is blank only in schema JSON
 * written before field identities existed — [FieldIdentityMigration] assigns
 * it once and persists it; nothing generates one while decoding.
 *
 * A Single Choice (`dropdown`), Multiple Choice (`multiple`), Comparison
 * (`comparison`) or List Item Quantities (`list_item_quantities`) field keeps
 * its options in [choices], each with its own permanent id; answers store
 * those ids.
 *
 * The `legacy…` properties hold the pre-identity shape (option text, the
 * Multiple Choice `*` switch, and the Single Choice "top answer" switch). They
 * are read only by [FieldIdentityMigration] and are empty/false afterwards.
 */
@Serializable
data class FieldDef(
    val id: String = "",
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /** multiline: visible height. */
    val lines: Int? = null,
    /** number / comparison / list item quantities: max digits allowed. */
    val digits: Int? = null,
    /** scale: inclusive range bounds. */
    val from: Int? = null,
    val to: Int? = null,
    /** dropdown / multiple / comparison / list item quantities: the options, active and retired, in display order. */
    val choices: List<ChoiceOption> = emptyList(),
    /** Legacy (pre-identity) dropdown / multiple option text. */
    @SerialName("options")
    val legacyOptions: List<String> = emptyList(),
    /** Legacy multiple: options beginning with `*` were pre-selected. */
    @SerialName("allowPreselectedMultipleChoices")
    val legacyAllowPreselectedMultipleChoices: Boolean = false,
    /** datetime: pre-fill with the current time (`default: now`). */
    val defaultNow: Boolean = false,
    /**
     * date / time / datetime: where a new entry's value auto-fills from — the
     * entry's timestamp ([AutoFill.TIMESTAMP]) or another date / time field's
     * permanent id — or null when Auto Fill is off. See [AutoFill].
     */
    val autoFillSource: String? = null,
    /** date / datetime: offer this label as an order-sorting category. */
    val allowOrderFiltering: Boolean = false,
    /** yesno: show an "Unknown" radio alongside Yes and No. */
    val allowUnknown: Boolean = false,
    /** scale: render as a dropdown of numbers instead of tappable chips. */
    val makeDropdown: Boolean = false,
    /** dropdown (Single Choice): show as a dropdown or as displayed chips. */
    val choiceStyle: ChoiceStyle = ChoiceStyle.DROPDOWN,
    /** Legacy dropdown (Single Choice): the first option was pre-selected. */
    @SerialName("topAnswerPreselected")
    val legacyTopAnswerPreselected: Boolean = false,
    /** ratio: the most digits allowed on each side of the "/". */
    val leftDigits: Int? = null,
    val rightDigits: Int? = null,
    /** ratio: the unit of measurement shown after the value (blank for none). */
    val unit: String = "",
    /**
     * comparison: the column and total captions; blank shows the default. List
     * Item Quantities uses [itemLabel], [firstLabel] (its one number column)
     * and [totalLabel].
     */
    val itemLabel: String = "",
    val firstLabel: String = "",
    val secondLabel: String = "",
    val totalLabel: String = "",
    /** comparison / list item quantities: the typed number columns take whole numbers only. */
    val wholeNumbersOnly: Boolean = true,
    /**
     * comparison: the second column counts with a tracker button instead of a
     * typed box ("Use Clicker Style Counting"). Switching it never touches saved
     * rows: both columns keep their stored numbers either way.
     */
    val clickerCounting: Boolean = false,
    /** comparison clicker counting: the button caption; blank shows "Add". */
    val clickerButtonLabel: String = "",
    /** comparison clicker counting: the value a row's second column starts at. */
    val clickerStartingNumber: Int = 0,
    /** comparison clicker counting: how much each press changes the value. */
    val clickerIncrement: Int = 1,
    /** comparison clicker counting: whether a press adds or subtracts. */
    val clickerDirection: ClickerIncrementDirection = ClickerIncrementDirection.ADD,
    /**
     * list item quantities: "Add Item Quantities" — show the total of the
     * amount column under the rows. Off shows no total line.
     */
    val addQuantities: Boolean = false,
    /**
     * list item quantities: "Add Checkbox" — each row gets a check box right
     * after its amount box, which a press checks and a second press clears.
     */
    val addCheckbox: Boolean = false,
    /**
     * list item quantities: "Show Total Amount of Check Marks" — with Add
     * Checkbox on, count the checked rows under the table.
     */
    val showCheckTotal: Boolean = false,
    /** list item quantities: "Show Average of Numbers" — average the amounts under the table. */
    val showAverage: Boolean = false,
)

/** How a Single Choice (`dropdown`) field presents its options on the entry form. */
@Serializable
enum class ChoiceStyle {
    @SerialName("dropdown") DROPDOWN,
    @SerialName("displayed") DISPLAYED,
}

/** True for the two field types that hold a fixed list of [FieldDef.choices]. */
val FieldType.hasChoices: Boolean
    get() = this == FieldType.DROPDOWN || this == FieldType.MULTIPLE

/**
 * True for the field types whose answer is rows of an item picked from
 * [FieldDef.choices] plus numbers: Comparison and List Item Quantities.
 */
val FieldType.hasItemRows: Boolean
    get() = this == FieldType.COMPARISON || this == FieldType.LIST_ITEM_QUANTITIES

/**
 * True for every field type whose items live in [FieldDef.choices] with
 * permanent ids: the two choice types plus the item-row types.
 */
val FieldType.hasOptionList: Boolean
    get() = hasChoices || hasItemRows

/** The options still offered for new answers, in display order. */
val FieldDef.activeChoices: List<ChoiceOption>
    get() = choices.activeChoices

/** The retired options, in display order. */
val FieldDef.retiredChoices: List<ChoiceOption>
    get() = choices.retiredChoices

/** The option with permanent id [id], active or retired, or null. */
fun FieldDef.choice(id: String): ChoiceOption? = choices.firstOrNull { it.id == id }

/** The Single Choice option id selected automatically when a new entry opens, if any. */
fun FieldDef.preselectedSingleChoice(): String? =
    if (type == FieldType.DROPDOWN) activeChoices.firstOrNull { it.preselected }?.id else null

/** The Multiple Choice option ids selected automatically when a new entry opens. */
fun FieldDef.preselectedMultipleChoices(): Set<String> =
    if (type == FieldType.MULTIPLE) {
        activeChoices.filter { it.preselected }.map { it.id }.toSet()
    } else {
        emptySet()
    }

/**
 * The closed set of field types from docs/FORM_MARKDOWN_SPEC.md §2. The
 * serialized name is the lowercase token the user writes after `type:`.
 */
@Serializable
enum class FieldType(val token: String) {
    TEXT("text"),
    MULTILINE("multiline"),
    NUMBER("number"),
    DROPDOWN("dropdown"),
    MULTIPLE("multiple"),
    SCALE("scale"),
    YESNO("yesno"),
    DATE("date"),
    TIME("time"),
    DATETIME("datetime"),
    RATIO("ratio"),
    TAGS("tags"),
    WEBPAGE("webpage"),
    BLOOD_PRESSURE("blood_pressure"),
    COMPARISON("comparison"),
    LIST_ITEM_QUANTITIES("list_item_quantities");

    companion object {
        fun fromToken(token: String): FieldType? =
            entries.firstOrNull { it.token == token.trim().lowercase() }

        /**
         * The order the form builders' Type drop-down lists the types in: the
         * item-row types sit just below Yes / No, with the date and time types
         * and the rest after them.
         */
        val pickerOrder: List<FieldType> = listOf(
            TEXT,
            MULTILINE,
            NUMBER,
            DROPDOWN,
            MULTIPLE,
            SCALE,
            YESNO,
            COMPARISON,
            LIST_ITEM_QUANTITIES,
            DATE,
            TIME,
            DATETIME,
            RATIO,
            TAGS,
            WEBPAGE,
            BLOOD_PRESSURE,
        )
    }
}

/**
 * Whether a field type can take part in entry ordering. Anything carrying a date
 * is eligible; a time-only field never is, because two times with no date can't
 * be placed chronologically against each other. Eligible only means the user may
 * opt the field in — sorting is never turned on automatically.
 */
val FieldType.sortEligible: Boolean
    get() = this == FieldType.DATE || this == FieldType.DATETIME
