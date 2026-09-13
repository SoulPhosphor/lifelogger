package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The daily-result engine: each Calculation Rule, day assignment by timestamp,
 * the "no applicable result → no day" rule, color mapping, and the short-press
 * line wording.
 */
class CalendarCalculatorTest {

    private val pain = FieldDef(label = "Pain Level", type = FieldType.NUMBER)
    private val meds = FieldDef(label = "Took Meds", type = FieldType.YESNO, allowUnknown = true)
    private val odor = FieldDef(
        label = "Odor",
        type = FieldType.DROPDOWN,
        options = listOf("Onion", "Coffee", "Smoke"),
    )
    private val whenField = FieldDef(label = "When", type = FieldType.DATE)
    private val fields = listOf(pain, meds, odor, whenField)

    private val day = LocalDate.of(2026, 9, 10)

    /** Four logs on 2026-09-10 with Pain Level 2, 7, 8, 4. */
    private val entries = listOf(
        entry("2026-09-10T09:00:00-05:00", "Pain Level" to "2", "Took Meds" to "Yes", "Odor" to "Onion"),
        entry("2026-09-10T10:00:00-05:00", "Pain Level" to "7", "Took Meds" to "No", "Odor" to "Coffee"),
        entry("2026-09-10T11:00:00-05:00", "Pain Level" to "8", "Took Meds" to "Yes", "Odor" to "Onion"),
        entry("2026-09-10T12:00:00-05:00", "Pain Level" to "4", "Took Meds" to "Yes"),
    )

    private fun entry(createdAt: String, vararg values: Pair<String, String>): LogEntry {
        val json = values.joinToString(prefix = "{", postfix = "}") { (k, v) -> "\"$k\":\"$v\"" }
        return LogEntry(templateId = 1, createdAt = createdAt, valuesJson = json)
    }

    private fun config(
        rule: String,
        sourceField: String? = null,
        logFrequency: Boolean = false,
        matchCondition: String? = null,
        matchValue: String = "",
        matchOption: String? = null,
        dayTimestampField: String? = null,
    ) = CalendarConfig(
        sourceLogFrequency = logFrequency,
        sourceField = sourceField,
        calculationRule = rule,
        matchCondition = matchCondition,
        matchValue = matchValue,
        matchOption = matchOption,
        dayTimestampField = dayTimestampField,
    )

    @Test
    fun logFrequencyCountsTheDaysLogs() {
        val result = CalendarCalculator.dailyValues(
            config(CalendarCalcRules.COUNT_LOGS, logFrequency = true), fields, entries,
        )
        assertEquals(mapOf(day to 4.0), result)
    }

    @Test
    fun numericRulesOverTheDaysValues() {
        fun value(rule: String) =
            CalendarCalculator.dailyValues(config(rule, sourceField = "Pain Level"), fields, entries)[day]

        assertEquals(8.0, value(CalendarCalcRules.HIGHEST_VALUE))
        assertEquals(2.0, value(CalendarCalcRules.LOWEST_VALUE))
        assertEquals(5.25, value(CalendarCalcRules.AVERAGE))
        assertEquals(21.0, value(CalendarCalcRules.TOTAL))
        assertEquals(4.0, value(CalendarCalcRules.COUNT_ENTRIES))
    }

    @Test
    fun countMatchingNumericUsesTheCondition() {
        val result = CalendarCalculator.dailyValues(
            config(
                CalendarCalcRules.COUNT_MATCHING,
                sourceField = "Pain Level",
                matchCondition = CalendarConditions.GREATER_OR_EQUAL,
                matchValue = "6",
            ),
            fields, entries,
        )
        // 7 and 8 meet ">= 6".
        assertEquals(2.0, result[day])
    }

    @Test
    fun yesNoAndChoiceCounts() {
        val yes = CalendarCalculator.dailyValues(
            config(CalendarCalcRules.COUNT_YES, sourceField = "Took Meds"), fields, entries,
        )
        assertEquals(3.0, yes[day])

        val onion = CalendarCalculator.dailyValues(
            config(CalendarCalcRules.COUNT_MATCHING, sourceField = "Odor", matchOption = "Onion"),
            fields, entries,
        )
        assertEquals(2.0, onion[day])
    }

    @Test
    fun aZeroCountLeavesTheDayWithNoResult() {
        // No entry is "Unknown", so the day has no result at all.
        val result = CalendarCalculator.dailyValues(
            config(CalendarCalcRules.COUNT_UNKNOWN, sourceField = "Took Meds"), fields, entries,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun daysComeFromTheChosenDateField() {
        val dated = listOf(
            entry("2026-01-01T09:00:00-05:00", "Pain Level" to "3", "When" to "2026-09-05"),
            entry("2026-01-01T09:00:00-05:00", "Pain Level" to "9", "When" to "2026-09-06"),
            entry("2026-01-01T09:00:00-05:00", "Pain Level" to "5"), // no When → skipped
        )
        val result = CalendarCalculator.dailyValues(
            config(CalendarCalcRules.HIGHEST_VALUE, sourceField = "Pain Level", dayTimestampField = "When"),
            fields, dated,
        )
        assertEquals(mapOf(LocalDate.of(2026, 9, 5) to 3.0, LocalDate.of(2026, 9, 6) to 9.0), result)
    }

    @Test
    fun colorMapsFromTheRanges() {
        val cfg = CalendarConfig(
            colorRows = listOf(
                CalendarColorRow("#00FF00", "1", "3"),
                CalendarColorRow("#FFFF00", "4", "7"),
                CalendarColorRow("#FF0000", "8", "10"),
            ),
        )
        assertEquals("#00FF00", CalendarCalculator.colorFor(cfg, 2.0))
        assertEquals("#FF0000", CalendarCalculator.colorFor(cfg, 8.0))
        assertNull(CalendarCalculator.colorFor(cfg, 100.0))
    }

    @Test
    fun shortPressLinesLeadWithTheSourceLabel() {
        assertEquals(
            "Pain Level Highest: 8",
            CalendarCalculator.shortPressLine(config(CalendarCalcRules.HIGHEST_VALUE, sourceField = "Pain Level"), 8.0),
        )
        assertEquals(
            "Pain Level Average: 5.25",
            CalendarCalculator.shortPressLine(config(CalendarCalcRules.AVERAGE, sourceField = "Pain Level"), 5.25),
        )
        assertEquals(
            "Took Meds 3 Times",
            CalendarCalculator.shortPressLine(config(CalendarCalcRules.COUNT_YES, sourceField = "Took Meds"), 3.0),
        )
        assertEquals(
            "Log Frequency 4 Times",
            CalendarCalculator.shortPressLine(config(CalendarCalcRules.COUNT_LOGS, logFrequency = true), 4.0),
        )
    }
}
