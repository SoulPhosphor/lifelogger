package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatioValuesTest {

    @Test
    fun bothSidesFilledWithinTheirLimitsIsAcceptable() {
        assertTrue(RatioValues.acceptable("120/90", required = true, leftDigits = 3, rightDigits = 3))
        assertTrue(RatioValues.acceptable("9/8", required = true, leftDigits = 3, rightDigits = 3))
        assertFalse(RatioValues.acceptable("1200/90", required = true, leftDigits = 3, rightDigits = 3))
    }

    @Test
    fun oneSideMissingAlwaysBlocks() {
        assertFalse(RatioValues.acceptable("120/", required = false, leftDigits = 3, rightDigits = 3))
        assertFalse(RatioValues.acceptable("/90", required = false, leftDigits = 3, rightDigits = 3))
    }

    @Test
    fun blankOnlyPassesWhenOptional() {
        assertTrue(RatioValues.acceptable("", required = false, leftDigits = 3, rightDigits = 3))
        assertFalse(RatioValues.acceptable("", required = true, leftDigits = 3, rightDigits = 3))
    }

    @Test
    fun displayAddsTheUnitOnlyWhenThereIsOne() {
        assertEquals("120/90 mmHg", RatioValues.display("120/90", " mmHg "))
        assertEquals("120/90", RatioValues.display("120/90", ""))
    }

    @Test
    fun digitSettingAcceptsOneToTenOnly() {
        assertEquals("10", RatioValues.digitsSetting("10", "1"))
        assertEquals("1", RatioValues.digitsSetting("11", "1"))
        assertEquals("3", RatioValues.digitsSetting("3a", ""))
        assertEquals(null, RatioValues.parseDigits("0"))
        assertEquals(null, RatioValues.parseDigits(""))
    }

    @Test
    fun ratioFieldSurvivesAFormMarkdownRoundTrip() {
        val field = FieldDef(id = "bp", label = "BP", type = FieldType.RATIO, leftDigits = 3, rightDigits = 2, unit = "mmHg")
        val parsed = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Log", listOf(field))).fields.single()
        // Markdown carries no ids; a parsed field is a new field with a new id.
        assertEquals(field, parsed.copy(id = field.id))
    }

    @Test
    fun ratioWithoutDigitLimitsIsSkippedOnImport() {
        val result = FormMarkdownParser.parse("## BP\ntype: ratio\nleft_digits: 3\n")
        assertTrue(result.fields.isEmpty())
    }

    @Test
    fun entryDisplayIncludesTheUnit() {
        val field = FieldDef(id = "bp", label = "BP", type = FieldType.RATIO, leftDigits = 3, rightDigits = 3, unit = "mmHg")
        val values = kotlinx.serialization.json.buildJsonObject {
            put("bp", kotlinx.serialization.json.JsonPrimitive("120/90"))
        }
        assertEquals("120/90 mmHg", EntryValues.displayValue(field, values))
    }
}
