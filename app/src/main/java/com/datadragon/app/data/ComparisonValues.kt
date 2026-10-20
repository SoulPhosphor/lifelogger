package com.datadragon.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/**
 * One row of a Comparison field as the entry form edits it: the chosen item's
 * permanent [ChoiceOption.id] (blank for none) and the text in its two number
 * boxes (blank for none).
 */
@Serializable
data class ComparisonRow(
    val item: String = "",
    val first: String = "",
    val second: String = "",
)

/**
 * Helpers for the Comparison field type. An answer is stored as a JSON array of
 * row objects, `{"item": <option id>, "first": <number>, "second": <number>}`,
 * in the order the rows were entered. The item is the option's permanent id, so
 * renaming an option never disconnects a saved row; a number left blank is
 * absent from its row. Each row's difference (second − first) and the total are
 * worked out when shown, never stored.
 *
 * With clicker style counting ([FieldDef.clickerCounting]) the second column is
 * stepped by a button. Its Starting Number is a real value as soon as the row
 * has an item — no press is needed — so [withStartingNumbers] fills it in before
 * a row is shown, totalled or stored. A row with no item yet (the placeholder
 * "Choose Item" row) only shows the Starting Number; it is never stored.
 */
object ComparisonValues {

    const val DEFAULT_ITEM_LABEL = "Item"
    const val DEFAULT_FIRST_LABEL = "Expected"
    const val DEFAULT_SECOND_LABEL = "Actual"
    const val DEFAULT_TOTAL_LABEL = "Total Difference"
    const val DIFFERENCE_LABEL = "Δ"
    const val DEFAULT_BUTTON_LABEL = "Add"

    /** Shown when a decimal point is typed into a whole-number box. */
    const val WHOLE_NUMBERS_ONLY_NOTICE = "When only whole numbers is selected. Decimal points are not allowed."

    private const val KEY_ITEM = "item"
    private const val KEY_FIRST = "first"
    private const val KEY_SECOND = "second"

    /**
     * The text a number box keeps from [input]: digits, one leading minus sign
     * and, unless [wholeNumbersOnly], one decimal point (a typed comma counts as
     * the point). [maxDigits] counts digits only — never the point or the sign.
     * Input breaking those rules leaves [previous] in place. A stored value that
     * already exceeds a rule tightened later can still be edited down.
     */
    fun acceptInput(input: String, previous: String, wholeNumbersOnly: Boolean, maxDigits: Int?): String {
        val text = input.replace(',', '.').filter { it.isDigit() || it == '.' || it == '-' }
        if (text.isEmpty()) return ""
        if (text.lastIndexOf('-') > 0) return previous
        val allowedPoints = maxOf(if (wholeNumbersOnly) 0 else 1, previous.count { it == '.' })
        if (text.count { it == '.' } > allowedPoints) return previous
        if (maxDigits != null) {
            val allowedDigits = maxOf(maxDigits, previous.count(Char::isDigit))
            if (text.count(Char::isDigit) > allowedDigits) return previous
        }
        return text
    }

    /** The number in a box, or null when it is blank or not yet a number ("-", "."). */
    fun parse(text: String): BigDecimal? =
        text.takeIf { it.any(Char::isDigit) }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    /** Second minus first, or null unless both numbers are present. */
    fun difference(row: ComparisonRow): BigDecimal? {
        val first = parse(row.first) ?: return null
        val second = parse(row.second) ?: return null
        return second.subtract(first)
    }

    /** The sum of every row's difference, or null when no row has both numbers. */
    fun total(rows: List<ComparisonRow>): BigDecimal? =
        rows.mapNotNull(::difference)
            .takeIf { it.isNotEmpty() }
            ?.fold(BigDecimal.ZERO) { sum, difference -> sum.add(difference) }

    /** A number as shown: plain digits, no exponent, no trailing zeros after the point. */
    fun format(value: BigDecimal): String =
        if (value.signum() == 0) "0" else value.stripTrailingZeros().toPlainString()

    /** True when [input] tried to add a decimal point that a whole-number box refuses. */
    fun refusesDecimalPoint(input: String, previous: String, wholeNumbersOnly: Boolean): Boolean =
        wholeNumbersOnly && input.count { it == '.' || it == ',' } > previous.count { it == '.' }

    /**
     * [rows] as they count for [field]: with clicker style counting, a row that
     * has an item but no second number yet holds the Starting Number. Rows
     * without an item, and fields that type both numbers, are unchanged.
     */
    fun withStartingNumbers(field: FieldDef, rows: List<ComparisonRow>): List<ComparisonRow> =
        if (!field.clickerCounting) {
            rows
        } else {
            rows.map { row ->
                if (row.item.isNotBlank() && parse(row.second) == null) {
                    row.copy(second = field.clickerStartingNumber.toString())
                } else {
                    row
                }
            }
        }

    /** The second-column value a clicker row shows: its number, or the Starting Number. */
    fun clickerValue(field: FieldDef, row: ComparisonRow): BigDecimal =
        parse(row.second) ?: BigDecimal(field.clickerStartingNumber)

    /** [row] after one press of its clicker button: the shown value plus or minus the increment. */
    fun clickerPressed(field: FieldDef, row: ComparisonRow): ComparisonRow {
        val step = BigDecimal(field.clickerIncrement)
        val current = clickerValue(field, row)
        val next = if (field.clickerDirection == ClickerIncrementDirection.SUBTRACT) {
            current.subtract(step)
        } else {
            current.add(step)
        }
        return row.copy(second = next.toPlainString())
    }

    /**
     * [rows] with items just added to the field placed into them: each fills the
     * last row when that row has no item yet, and otherwise goes into a new row
     * at the end. An item some row already holds is left where it is.
     */
    fun placeItems(rows: List<ComparisonRow>, itemIds: List<String>): List<ComparisonRow> {
        var placed = rows
        itemIds.forEach { id ->
            if (placed.any { it.item == id }) return@forEach
            val last = placed.lastOrNull()
            placed = if (last != null && last.item.isBlank()) {
                placed.dropLast(1) + last.copy(item = id)
            } else {
                placed + ComparisonRow(item = id)
            }
        }
        return placed
    }

    /** True when the row holds nothing at all. */
    fun isBlank(row: ComparisonRow): Boolean =
        row.item.isBlank() && parse(row.first) == null && parse(row.second) == null

    /** True when the row has a number but no item, which blocks Save. */
    fun needsItem(row: ComparisonRow): Boolean =
        row.item.isBlank() && (parse(row.first) != null || parse(row.second) != null)

    /** The stored form of [rows], or null when there is nothing to store. */
    fun encode(rows: List<ComparisonRow>): JsonElement? {
        val stored = rows.filter { it.item.isNotBlank() }.map { row ->
            JsonObject(
                buildMap {
                    put(KEY_ITEM, JsonPrimitive(row.item))
                    parse(row.first)?.let { put(KEY_FIRST, JsonPrimitive(it)) }
                    parse(row.second)?.let { put(KEY_SECOND, JsonPrimitive(it)) }
                },
            )
        }
        return if (stored.isEmpty()) null else JsonArray(stored)
    }

    /** The saved rows of [field] in [values], in stored order. */
    fun rows(values: JsonObject, field: FieldDef): List<ComparisonRow> = decode(values[field.id])

    /** The rows of a stored Comparison answer; anything unreadable is skipped. */
    fun decode(raw: JsonElement?): List<ComparisonRow> =
        (raw as? JsonArray)?.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val item = (row[KEY_ITEM] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }
                ?: return@mapNotNull null
            ComparisonRow(item = item, first = numberText(row[KEY_FIRST]), second = numberText(row[KEY_SECOND]))
        }.orEmpty()

    private fun numberText(element: JsonElement?): String {
        val content = (element as? JsonPrimitive)?.contentOrNull ?: return ""
        return runCatching { BigDecimal(content).toPlainString() }.getOrDefault(content)
    }

    /**
     * Drop every row that picked [optionId] — the data side of permanently
     * deleting that option. Returns null when no row is left.
     */
    fun withoutItem(stored: JsonArray, optionId: String): JsonArray? {
        val kept = stored.filterNot { element ->
            ((element as? JsonObject)?.get(KEY_ITEM) as? JsonPrimitive)?.contentOrNull == optionId
        }
        return if (kept.isEmpty()) null else JsonArray(kept)
    }

    /**
     * One line of text for a saved answer, used wherever a field is shown as
     * text (reports, exports): each row as "Item: Expected 3, Actual 1, Δ -2",
     * rows joined by "; ", then the total. Null when nothing was saved.
     */
    fun display(field: FieldDef, stored: List<ComparisonRow>): String? {
        if (stored.isEmpty()) return null
        val rows = withStartingNumbers(field, stored)
        val parts = rows.map { row ->
            val numbers = listOfNotNull(
                parse(row.first)?.let { "${field.comparisonFirstLabel} ${format(it)}" },
                parse(row.second)?.let { "${field.comparisonSecondLabel} ${format(it)}" },
                difference(row)?.let { "$DIFFERENCE_LABEL ${format(it)}" },
            )
            val name = field.choice(row.item)?.label ?: row.item
            if (numbers.isEmpty()) name else "$name: ${numbers.joinToString(", ")}"
        }
        val total = total(rows)?.let { "${field.comparisonTotalLabel}: ${format(it)}" }
        return (parts + listOfNotNull(total)).joinToString("; ")
    }
}

/** The captions a Comparison field shows, falling back to the defaults when left blank. */
val FieldDef.comparisonItemLabel: String
    get() = itemLabel.ifBlank { ComparisonValues.DEFAULT_ITEM_LABEL }
val FieldDef.comparisonFirstLabel: String
    get() = firstLabel.ifBlank { ComparisonValues.DEFAULT_FIRST_LABEL }
val FieldDef.comparisonSecondLabel: String
    get() = secondLabel.ifBlank { ComparisonValues.DEFAULT_SECOND_LABEL }
val FieldDef.comparisonTotalLabel: String
    get() = totalLabel.ifBlank { ComparisonValues.DEFAULT_TOTAL_LABEL }
val FieldDef.comparisonButtonLabel: String
    get() = clickerButtonLabel.ifBlank { ComparisonValues.DEFAULT_BUTTON_LABEL }

/**
 * The most digits any saved number of a Comparison field has, across both
 * columns of every entry in [entryValues] — the lowest Max Digits it may be
 * given. Null when nothing is saved.
 */
fun FieldDef.savedComparisonDigits(entryValues: List<String>): Int? =
    entryValues
        .flatMap { ComparisonValues.rows(EntryValues.decode(it), this) }
        .flatMap { listOf(it.first, it.second) }
        .maxOfOrNull { number -> number.count(Char::isDigit) }
        ?.takeIf { it > 0 }
