package com.datadragon.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.math.floor

/**
 * Turns a form's logs into one daily result per day for a configured calendar,
 * and maps that result to a color. Pure and self-contained so it can be unit
 * tested without Android.
 *
 * Each log is assigned to a day by the configured timestamp
 * ([CalendarConfig.dayTimestampField] — a Date / Date & Time field, or the log's
 * automatic created-at time when null). The day's logs are then reduced to a
 * single number by the [CalendarConfig.calculationRule], and that number is
 * compared against the color ranges to pick the day's color.
 *
 * A day has no result (and so no color) when nothing applicable was logged — for
 * example a count that comes out zero, or a value rule with no numeric values.
 */
object CalendarCalculator {

    /** The source's owner-facing label: the tracked field, or "Log Frequency". */
    fun sourceLabel(config: CalendarConfig): String =
        if (config.sourceLogFrequency) "Log Frequency" else config.sourceField.orEmpty()

    /** One numeric result per day that has one, keyed by the day it falls on. */
    fun dailyValues(
        config: CalendarConfig,
        fields: List<FieldDef>,
        entries: List<LogEntry>,
    ): Map<LocalDate, Double> {
        val rule = config.calculationRule ?: return emptyMap()
        val sourceField = config.sourceField?.let { label -> fields.firstOrNull { it.label == label } }
        val timestampField = config.dayTimestampField?.let { label -> fields.firstOrNull { it.label == label } }

        val byDay = entries.groupBy { dayOf(it, timestampField) }
        val results = LinkedHashMap<LocalDate, Double>()
        for ((day, dayEntries) in byDay) {
            if (day == null) continue
            val value = computeDayValue(config, rule, sourceField, dayEntries) ?: continue
            results[day] = value
        }
        return results
    }

    /** The configured color for [value], or null when no range contains it. */
    fun colorFor(config: CalendarConfig, value: Double): String? {
        for (row in config.colorRows) {
            val min = row.minValue.trim().toDoubleOrNull() ?: Double.NEGATIVE_INFINITY
            val max = row.maxValue.trim().toDoubleOrNull() ?: Double.POSITIVE_INFINITY
            if (value in min..max) return row.colorHex
        }
        return null
    }

    /** The short-press line for a day's [value]: "<source> <rule result>". */
    fun shortPressLine(config: CalendarConfig, value: Double): String {
        val source = sourceLabel(config)
        return when (config.calculationRule) {
            CalendarCalcRules.HIGHEST_VALUE -> "$source Highest: ${formatNumber(value)}"
            CalendarCalcRules.LOWEST_VALUE -> "$source Lowest: ${formatNumber(value)}"
            CalendarCalcRules.AVERAGE -> "$source Average: ${formatNumber(value)}"
            CalendarCalcRules.TOTAL -> "$source Total: ${formatNumber(value)}"
            else -> "$source ${value.toInt()} Times"
        }
    }

    // ---- internals ----------------------------------------------------------

    /** The day a log falls on, per the configured timestamp, or null if unknown. */
    private fun dayOf(entry: LogEntry, timestampField: FieldDef?): LocalDate? {
        if (timestampField == null) {
            return runCatching { OffsetDateTime.parse(entry.createdAt).toLocalDate() }.getOrNull()
        }
        val raw = EntryValues.rawValue(EntryValues.decode(entry.valuesJson), timestampField.label)
            ?: return null
        return runCatching {
            when (timestampField.type) {
                FieldType.DATETIME -> LocalDateTime.parse(raw, EntryValues.DATETIME_STORAGE).toLocalDate()
                else -> LocalDate.parse(raw, EntryValues.DATE_STORAGE)
            }
        }.getOrNull()
    }

    private fun computeDayValue(
        config: CalendarConfig,
        rule: String,
        sourceField: FieldDef?,
        entries: List<LogEntry>,
    ): Double? {
        // Log Frequency needs no field: the day's result is the number of logs.
        if (rule == CalendarCalcRules.COUNT_LOGS) {
            return entries.size.takeIf { it > 0 }?.toDouble()
        }
        if (sourceField == null) return null
        val values = entries.map { EntryValues.decode(it.valuesJson) }

        return when (rule) {
            CalendarCalcRules.COUNT_ENTRIES ->
                values.count { EntryValues.rawValue(it, sourceField.label) != null }
                    .takeIf { it > 0 }?.toDouble()

            CalendarCalcRules.HIGHEST_VALUE -> numericValues(values, sourceField).maxOrNull()
            CalendarCalcRules.LOWEST_VALUE -> numericValues(values, sourceField).minOrNull()
            CalendarCalcRules.AVERAGE ->
                numericValues(values, sourceField).let { if (it.isEmpty()) null else it.average() }
            CalendarCalcRules.TOTAL ->
                numericValues(values, sourceField).let { if (it.isEmpty()) null else it.sum() }

            CalendarCalcRules.COUNT_MATCHING -> countMatching(config, sourceField, values)

            CalendarCalcRules.COUNT_YES -> countResponse(values, sourceField, "Yes")
            CalendarCalcRules.COUNT_NO -> countResponse(values, sourceField, "No")
            CalendarCalcRules.COUNT_UNKNOWN -> countResponse(values, sourceField, "Unknown")

            else -> null
        }
    }

    private fun numericValues(
        values: List<kotlinx.serialization.json.JsonObject>,
        field: FieldDef,
    ): List<Double> = values.mapNotNull { EntryValues.rawValue(it, field.label)?.toDoubleOrNull() }

    private fun countResponse(
        values: List<kotlinx.serialization.json.JsonObject>,
        field: FieldDef,
        response: String,
    ): Double? = values.count { EntryValues.rawValue(it, field.label) == response }
        .takeIf { it > 0 }?.toDouble()

    private fun countMatching(
        config: CalendarConfig,
        field: FieldDef,
        values: List<kotlinx.serialization.json.JsonObject>,
    ): Double? {
        val count = when (field.type) {
            FieldType.DROPDOWN -> {
                val target = config.matchOption ?: return null
                values.count { EntryValues.rawValue(it, field.label) == target }
            }
            FieldType.MULTIPLE -> {
                val target = config.matchOption ?: return null
                values.count { EntryValues.selectedOptions(it, field.label).contains(target) }
            }
            else -> {
                val threshold = config.matchValue.trim().toDoubleOrNull() ?: return null
                numericValues(values, field).count { satisfies(it, config.matchCondition, threshold) }
            }
        }
        return count.takeIf { it > 0 }?.toDouble()
    }

    private fun satisfies(value: Double, condition: String?, threshold: Double): Boolean = when (condition) {
        CalendarConditions.GREATER_THAN -> value > threshold
        CalendarConditions.GREATER_OR_EQUAL -> value >= threshold
        CalendarConditions.EQUAL_TO -> value == threshold
        CalendarConditions.LESS_OR_EQUAL -> value <= threshold
        CalendarConditions.LESS_THAN -> value < threshold
        else -> false
    }

    /** Whole numbers show without a decimal; fractions keep up to two places. */
    private fun formatNumber(value: Double): String =
        if (value == floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            val rounded = Math.round(value * 100.0) / 100.0
            rounded.toString().removeSuffix(".0")
        }
}
