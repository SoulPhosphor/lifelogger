package com.datadragon.app.data

/** The bounds every "Number of Digits" box on a Ratios field accepts. */
const val MIN_RATIO_DIGITS = 1
const val MAX_RATIO_DIGITS = 10

/**
 * Helpers for the Ratios field type. A ratio is stored as one string, the two
 * numbers joined by "/" (e.g. "120/90"); the unit of measurement belongs to the
 * field, not the value, and is only added when the value is displayed.
 */
object RatioValues {

    /** Split a stored ratio into its two sides; a missing side comes back empty. */
    fun split(stored: String): Pair<String, String> {
        val slash = stored.indexOf('/')
        return if (slash < 0) stored to "" else stored.substring(0, slash) to stored.substring(slash + 1)
    }

    /** Join two sides for storage; two blank sides store nothing. */
    fun join(left: String, right: String): String =
        if (left.isEmpty() && right.isEmpty()) "" else "$left/$right"

    /**
     * A ratio blocks Save unless both sides carry a number no longer than their
     * digit limit, or (when the field is optional) both sides are blank.
     */
    fun acceptable(value: String?, required: Boolean, leftDigits: Int, rightDigits: Int): Boolean {
        val (left, right) = split(value.orEmpty())
        if (left.isEmpty() && right.isEmpty()) return !required
        fun valid(side: String, limit: Int) = side.length in 1..limit && side.all(Char::isDigit)
        return valid(left, leftDigits) && valid(right, rightDigits)
    }

    /** The stored ratio followed by the field's unit, e.g. "120/90 mmHg". */
    fun display(stored: String, unit: String): String =
        if (unit.isBlank()) stored else "$stored ${unit.trim()}"

    /**
     * The text a "Number of Digits" box keeps from [input]: digits only, and
     * never above [MAX_RATIO_DIGITS]. Anything larger leaves [previous] in place.
     */
    fun digitsSetting(input: String, previous: String): String {
        val digits = input.filter { it.isDigit() }.take(2)
        val n = digits.toIntOrNull() ?: return digits
        return if (n > MAX_RATIO_DIGITS) previous else digits
    }

    /** A typed digit count, or null when blank or outside 1–10. */
    fun parseDigits(text: String): Int? =
        text.toIntOrNull()?.takeIf { it in MIN_RATIO_DIGITS..MAX_RATIO_DIGITS }
}
