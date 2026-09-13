package com.datadragon.app.data

import org.junit.Assert.assertEquals
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
    fun blankOrUnknownConfigDecodesToDefaults() {
        assertEquals(CalendarConfig(), CalendarConfigCodec.decode(""))
        // Unknown keys from a later phase's config don't break an older decode.
        val decoded = CalendarConfigCodec.decode("""{"colorCount":5,"futureField":"x"}""")
        assertEquals(5, decoded.colorCount)
        assertTrue(decoded.colorRows.isEmpty())
    }
}
