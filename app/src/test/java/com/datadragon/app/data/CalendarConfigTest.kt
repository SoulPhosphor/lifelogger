package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The color-preset selection math and the config JSON round-trip. */
class CalendarConfigTest {

    @Test
    fun gradiatedAlwaysRunsBlueToRed() {
        // Full set is the exact ordered list.
        val ten = ColorPresets.colorsFor(ColorPresets.GRADIATED, 10)
        assertEquals(10, ten.size)
        assertEquals("#0033FF", ten.first())
        assertEquals("#FF0000", ten.last())

        // Fewer colors are spread evenly, still deep blue → pure red at the ends.
        val three = ColorPresets.colorsFor(ColorPresets.GRADIATED, 3)
        assertEquals(listOf("#0033FF", "#FFF280", "#FF0000"), three)

        val five = ColorPresets.colorsFor(ColorPresets.GRADIATED, 5)
        assertEquals(5, five.size)
        assertEquals("#0033FF", five.first())
        assertEquals("#FF0000", five.last())
    }

    @Test
    fun primaryKeepsItsOrderAndEndpoints() {
        val ten = ColorPresets.colorsFor(ColorPresets.PRIMARY, 10)
        assertEquals(10, ten.size)
        assertEquals("#4A35A8", ten.first())
        assertEquals("#FF5722", ten.last())

        val three = ColorPresets.colorsFor(ColorPresets.PRIMARY, 3)
        assertEquals(3, three.size)
        assertEquals("#4A35A8", three.first())
        assertEquals("#FF5722", three.last())
    }

    @Test
    fun configRoundTripsThroughJson() {
        val config = CalendarConfig(
            colorCount = 3,
            colorPreset = ColorPresets.PRIMARY,
            colorRows = listOf(
                CalendarColorRow("#4A35A8", "1", "2"),
                CalendarColorRow("#009688", "3", "4"),
                CalendarColorRow("#FF5722", "5", "6"),
            ),
        )
        val decoded = CalendarConfigCodec.decode(CalendarConfigCodec.encode(config))
        assertEquals(config, decoded)
    }

    @Test
    fun calcRulesDependOnTheSource() {
        // Log Frequency's only rule is counting the day's logs.
        assertEquals(listOf(CalendarCalcRules.COUNT_LOGS), CalendarCalcRules.forLogFrequency())

        // Numbers and scales take the six numeric rules, ending in Count Matching.
        val numberRules = CalendarCalcRules.forField(FieldType.NUMBER, allowUnknown = false)
        assertEquals(
            listOf(
                CalendarCalcRules.HIGHEST_VALUE,
                CalendarCalcRules.LOWEST_VALUE,
                CalendarCalcRules.AVERAGE,
                CalendarCalcRules.TOTAL,
                CalendarCalcRules.COUNT_ENTRIES,
                CalendarCalcRules.COUNT_MATCHING,
            ),
            numberRules,
        )
        assertEquals(numberRules, CalendarCalcRules.forField(FieldType.SCALE, allowUnknown = false))

        // Yes/No counts a response; Unknown only when the field allows it.
        assertEquals(
            listOf(CalendarCalcRules.COUNT_YES, CalendarCalcRules.COUNT_NO),
            CalendarCalcRules.forField(FieldType.YESNO, allowUnknown = false),
        )
        assertEquals(
            listOf(CalendarCalcRules.COUNT_YES, CalendarCalcRules.COUNT_NO, CalendarCalcRules.COUNT_UNKNOWN),
            CalendarCalcRules.forField(FieldType.YESNO, allowUnknown = true),
        )

        // A field type without a defined daily rule offers none and isn't mappable.
        assertTrue(CalendarCalcRules.forField(FieldType.TEXT, allowUnknown = false).isEmpty())
        assertFalse(FieldType.TEXT.heatMapApplicable())
        assertTrue(FieldType.NUMBER.heatMapApplicable())
        assertTrue(FieldType.YESNO.heatMapApplicable())

        // Only Count Matching reveals the condition + value controls.
        assertTrue(CalendarCalcRules.requiresCondition(CalendarCalcRules.COUNT_MATCHING))
        assertFalse(CalendarCalcRules.requiresCondition(CalendarCalcRules.AVERAGE))
        assertFalse(CalendarCalcRules.requiresCondition(null))
    }

    @Test
    fun heatMapConfigRoundTrips() {
        val config = CalendarConfig(
            sourceField = "Pain Level",
            calculationRule = CalendarCalcRules.COUNT_MATCHING,
            matchCondition = CalendarConditions.GREATER_OR_EQUAL,
            matchValue = "6",
        )
        assertEquals(config, CalendarConfigCodec.decode(CalendarConfigCodec.encode(config)))
    }

    @Test
    fun colorPresetColorsRoundTrip() {
        val colors = listOf("#0033FF", "#FFF280", "#FF0000")
        assertEquals(colors, ColorPresetCodec.decode(ColorPresetCodec.encode(colors)))
        // A blank or bad value decodes to an empty list rather than throwing.
        assertTrue(ColorPresetCodec.decode("").isEmpty())
        assertTrue(ColorPresetCodec.decode("not json").isEmpty())
    }

    @Test
    fun blankOrUnknownConfigDecodesToDefaults() {
        assertEquals(CalendarConfig(), CalendarConfigCodec.decode(""))
        // Unknown keys from a later phase's config don't break an older decode.
        val decoded = CalendarConfigCodec.decode("""{"colorCount":5,"futureField":"x"}""")
        assertEquals(5, decoded.colorCount)
        assertTrue(decoded.colorRows.isEmpty())
    }
}
