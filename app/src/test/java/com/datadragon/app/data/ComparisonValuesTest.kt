package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Comparison answer stores each row's item by its option's permanent id, so a
 * rename never disconnects it; numbers follow the field's digit and decimal
 * rules; and the difference and total are worked out, never stored.
 */
class ComparisonValuesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val meals = FieldDef(
        id = "meals",
        label = "Meals",
        type = FieldType.COMPARISON,
        choices = listOf(ChoiceOption("turkey", "Turkey Dinner"), ChoiceOption("ham", "Ham Dinner")),
    )

    @Test
    fun wholeNumberBoxesRefuseADecimalPoint() {
        assertEquals("34", ComparisonValues.acceptInput("34", "3", wholeNumbersOnly = true, maxDigits = null))
        assertEquals("34", ComparisonValues.acceptInput("34.", "34", wholeNumbersOnly = true, maxDigits = null))
        assertEquals("-3", ComparisonValues.acceptInput("-3", "-", wholeNumbersOnly = true, maxDigits = null))
    }

    @Test
    fun theDigitLimitCountsDigitsOnlyNeverThePointOrSign() {
        assertEquals("34.4", ComparisonValues.acceptInput("34.4", "34.", wholeNumbersOnly = false, maxDigits = 3))
        assertEquals("-34.4", ComparisonValues.acceptInput("-34.4", "-34.", wholeNumbersOnly = false, maxDigits = 3))
        assertEquals("34.4", ComparisonValues.acceptInput("34.45", "34.4", wholeNumbersOnly = false, maxDigits = 3))
        assertEquals("999", ComparisonValues.acceptInput("9999", "999", wholeNumbersOnly = true, maxDigits = 3))
    }

    @Test
    fun onlyOneLeadingMinusAndOnePointAreKept() {
        assertEquals("3.5", ComparisonValues.acceptInput("3.5.", "3.5", wholeNumbersOnly = false, maxDigits = null))
        assertEquals("35", ComparisonValues.acceptInput("3-5", "35", wholeNumbersOnly = false, maxDigits = null))
        assertEquals("3.5", ComparisonValues.acceptInput("3,5", "3", wholeNumbersOnly = false, maxDigits = null))
    }

    @Test
    fun aValueSavedBeforeTheRulesTightenedCanStillBeEditedDown() {
        assertEquals("12.", ComparisonValues.acceptInput("12.", "12.5", wholeNumbersOnly = true, maxDigits = 2))
        assertEquals("1234", ComparisonValues.acceptInput("1234", "12345", wholeNumbersOnly = true, maxDigits = 3))
    }

    @Test
    fun theDifferenceIsSecondMinusFirstAndNeedsBothNumbers() {
        assertEquals("-2", ComparisonValues.difference(ComparisonRow("turkey", "3", "1"))!!.let(ComparisonValues::format))
        assertEquals("0.3", ComparisonValues.difference(ComparisonRow("turkey", "0.1", "0.4"))!!.let(ComparisonValues::format))
        assertNull(ComparisonValues.difference(ComparisonRow("turkey", "3", "")))
        assertNull(ComparisonValues.difference(ComparisonRow("turkey", "-", "4")))
    }

    @Test
    fun theTotalCountsOnlyRowsWithBothNumbers() {
        val rows = listOf(
            ComparisonRow("turkey", "3", "1"),
            ComparisonRow("ham", "2", ""),
            ComparisonRow("beef", "1.5", "4"),
        )
        assertEquals("0.5", ComparisonValues.format(ComparisonValues.total(rows)!!))
        assertNull(ComparisonValues.total(listOf(ComparisonRow("ham", "2", ""))))
    }

    @Test
    fun aRowWithANumberButNoItemBlocksSave() {
        assertTrue(ComparisonValues.needsItem(ComparisonRow("", "3", "")))
        assertFalse(ComparisonValues.needsItem(ComparisonRow("", "", "")))
        assertFalse(ComparisonValues.needsItem(ComparisonRow("", "-", "")))
        assertFalse(ComparisonValues.needsItem(ComparisonRow("ham", "", "")))
    }

    @Test
    fun rowsRoundTripKeepingItemIdsOrderAndBlankNumbers() {
        val rows = listOf(
            ComparisonRow("ham", "2", ""),
            ComparisonRow("", "", ""),
            ComparisonRow("turkey", "-3.25", "1"),
        )
        val stored = ComparisonValues.encode(rows)!! as JsonArray
        assertEquals(2, stored.size)
        assertFalse("second" in (stored[0] as JsonObject))
        assertEquals("ham", (stored[0] as JsonObject)["item"]!!.jsonPrimitive.content)
        // Numbers are stored as JSON numbers, not strings.
        assertFalse((stored[1] as JsonObject)["first"]!!.jsonPrimitive.isString)

        val values = EntryValues.decode(EntryValues.encode(mapOf("meals" to stored)))
        assertEquals(
            listOf(ComparisonRow("ham", "2", ""), ComparisonRow("turkey", "-3.25", "1")),
            ComparisonValues.rows(values, meals),
        )
        assertNull(ComparisonValues.encode(listOf(ComparisonRow())))
    }

    @Test
    fun renamingAnOptionKeepsItsSavedRowsAndTheirIds() {
        val stored = EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("turkey", "3", "1")))!!))
        val renamed = meals.copy(choices = ChoiceEdits.renamed(meals.choices, "turkey", "Roast Turkey"))
        val values = EntryValues.decode(stored)

        assertEquals("turkey", ComparisonValues.rows(values, renamed).single().item)
        assertEquals("Roast Turkey: Expected 3, Actual 1, Δ -2; Total Difference: -2", EntryValues.displayValue(renamed, values))
    }

    @Test
    fun customCaptionsReplaceTheDefaultsInText() {
        val field = meals.copy(firstLabel = "Planned", secondLabel = "Eaten", totalLabel = "Net")
        val values = EntryValues.decode(
            EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("ham", "2", "5")))!!)),
        )
        assertEquals("Ham Dinner: Planned 2, Eaten 5, Δ 3; Net: 3", EntryValues.displayValue(field, values))
    }

    @Test
    fun deletingAnOptionDropsOnlyTheRowsThatPickedIt() {
        val schema = FormMarkdownParser.encodeFields(listOf(meals))
        val entry = EntryValues.encode(
            mapOf(
                "meals" to ComparisonValues.encode(
                    listOf(ComparisonRow("turkey", "3", "1"), ComparisonRow("ham", "2", "2")),
                )!!,
                EntryValues.NOTES_KEY to EntryValues.string("keep"),
            ),
        )
        val onlyTurkey = EntryValues.encode(
            mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("turkey", "1", "")))!!),
        )
        val result = ChoiceDeletion.fromForm(schema, listOf(entry, onlyTurkey), emptyList(), "meals", "turkey")!!

        val fields = json.decodeFromString(ListSerializer(FieldDef.serializer()), result.schemaJson)
        assertEquals(listOf("ham"), fields.single().choices.map { it.id })
        val first = EntryValues.decode(result.entryValues[0])
        assertEquals(listOf(ComparisonRow("ham", "2", "2")), ComparisonValues.rows(first, meals))
        assertEquals("keep", EntryValues.notes(first))
        assertFalse("meals" in EntryValues.decode(result.entryValues[1]))
    }

    @Test
    fun deletingAnOptionNoRowPickedLeavesTheEntryByteForByte() {
        val entry = EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("ham", "2", "")))!!))
        assertEquals(entry, EntryValues.withoutOption(entry, "meals", "turkey"))
    }

    @Test
    fun formMarkdownRoundTripsEverySetting() {
        val markdown = """
            # Food
            ## Meals
            type: comparison
            options:
            - Turkey Dinner
            - Ham Dinner
            digits: 3
            whole_numbers: false
            item_label: Meal
            first_label: Planned
            second_label: Eaten
            total_label: Net
            required
        """.trimIndent()
        val field = FormMarkdownParser.parse(markdown).fields.single()
        assertEquals(FieldType.COMPARISON, field.type)
        assertEquals(listOf("Turkey Dinner", "Ham Dinner"), field.choices.map { it.label })
        assertEquals(3, field.digits)
        assertFalse(field.wholeNumbersOnly)
        assertEquals("Meal", field.comparisonItemLabel)
        assertEquals("Planned", field.comparisonFirstLabel)
        assertEquals("Eaten", field.comparisonSecondLabel)
        assertEquals("Net", field.comparisonTotalLabel)
        // A Comparison field is never required.
        assertFalse(field.required)

        val again = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Food", listOf(field))).fields.single()
        assertEquals(field.copy(id = "", choices = emptyList()), again.copy(id = "", choices = emptyList()))
        assertEquals(field.choices.map { it.label }, again.choices.map { it.label })
    }

    private val counted = meals.copy(clickerCounting = true, clickerStartingNumber = 5, clickerIncrement = 2)

    @Test
    fun clickerPressesStepFromTheStartingNumber() {
        var row = ComparisonRow("turkey")
        val shown = mutableListOf(ComparisonValues.format(ComparisonValues.clickerValue(counted, row)))
        repeat(3) {
            row = ComparisonValues.clickerPressed(counted, row)
            shown += row.second
        }
        assertEquals(listOf("5", "7", "9", "11"), shown)
    }

    @Test
    fun subtractingClickerPressesGoBelowZero() {
        val field = counted.copy(clickerDirection = ClickerIncrementDirection.SUBTRACT)
        var row = ComparisonRow("turkey")
        val shown = mutableListOf(ComparisonValues.format(ComparisonValues.clickerValue(field, row)))
        repeat(3) {
            row = ComparisonValues.clickerPressed(field, row)
            shown += row.second
        }
        assertEquals(listOf("5", "3", "1", "-1"), shown)
    }

    @Test
    fun theStartingNumberCountsOnceARowHasAnItemWithoutAPress() {
        val rows = ComparisonValues.withStartingNumbers(counted, listOf(ComparisonRow("turkey", "3", ""), ComparisonRow()))
        assertEquals(ComparisonRow("turkey", "3", "5"), rows[0])
        // The placeholder row has no item, so its Starting Number is not a value.
        assertEquals(ComparisonRow(), rows[1])
        assertEquals("2", ComparisonValues.format(ComparisonValues.difference(rows[0])!!))
        val stored = ComparisonValues.encode(rows)!! as JsonArray
        assertEquals(1, stored.size)
        assertEquals("5", (stored[0] as JsonObject)["second"]!!.jsonPrimitive.content)
    }

    @Test
    fun typedFieldsNeverGainAStartingNumber() {
        val rows = listOf(ComparisonRow("turkey", "3", ""))
        assertEquals(rows, ComparisonValues.withStartingNumbers(meals.copy(clickerStartingNumber = 5), rows))
    }

    @Test
    fun switchingCountingOnOrOffKeepsTheSavedNumbers() {
        val stored = EntryValues.decode(
            EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("turkey", "3", "300")))!!)),
        )
        assertEquals(ComparisonValues.rows(stored, meals), ComparisonValues.rows(stored, counted))
        assertEquals("300", ComparisonValues.rows(stored, counted).single().second)
    }

    @Test
    fun maxDigitsCannotGoBelowTheLongestSavedNumber() {
        val entries = listOf(
            EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("turkey", "-3.5", "300")))!!)),
            EntryValues.encode(mapOf("meals" to ComparisonValues.encode(listOf(ComparisonRow("ham", "12", "")))!!)),
        )
        assertEquals(3, meals.savedComparisonDigits(entries))
        assertNull(meals.savedComparisonDigits(emptyList()))
    }

    @Test
    fun aDecimalPointInAWholeNumberBoxIsReported() {
        assertTrue(ComparisonValues.refusesDecimalPoint("3.", "3", wholeNumbersOnly = true))
        assertTrue(ComparisonValues.refusesDecimalPoint("3,", "3", wholeNumbersOnly = true))
        assertFalse(ComparisonValues.refusesDecimalPoint("3.", "3", wholeNumbersOnly = false))
        assertFalse(ComparisonValues.refusesDecimalPoint("34", "3", wholeNumbersOnly = true))
    }

    @Test
    fun clickerSettingsRoundTripThroughFormMarkdown() {
        val field = counted.copy(clickerButtonLabel = "Ate One", clickerDirection = ClickerIncrementDirection.SUBTRACT)
        val again = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Food", listOf(field))).fields.single()
        assertTrue(again.clickerCounting)
        assertEquals("Ate One", again.clickerButtonLabel)
        assertEquals(5, again.clickerStartingNumber)
        assertEquals(2, again.clickerIncrement)
        assertEquals(ClickerIncrementDirection.SUBTRACT, again.clickerDirection)
    }

    @Test
    fun maxDigitsAboveTheLargestIsRefused() {
        assertEquals(15, ComparisonValues.parseMaxDigits("15"))
        assertNull(ComparisonValues.parseMaxDigits("100000000"))
        assertNull(ComparisonValues.parseMaxDigits("0"))
        val result = FormMarkdownParser.parse(
            "## Meals\ntype: comparison\noptions:\n- Turkey\ndigits: 100000000\n",
        )
        assertNull(result.fields.single().digits)
        assertTrue(result.issues.any { "digits must be from 1 to 15" in it })
    }

    @Test
    fun blankCaptionsShowTheDefaults() {
        assertEquals("Item", meals.comparisonItemLabel)
        assertEquals("Expected", meals.comparisonFirstLabel)
        assertEquals("Actual", meals.comparisonSecondLabel)
        assertEquals("Total Difference", meals.comparisonTotalLabel)
        assertTrue(meals.wholeNumbersOnly)
        assertFalse(meals.clickerCounting)
        assertEquals("Add", meals.comparisonButtonLabel)
    }

    @Test
    fun theSchemaKeepsEveryOptionIdThroughEncoding() {
        val decoded = json.decodeFromString(
            ListSerializer(FieldDef.serializer()),
            FormMarkdownParser.encodeFields(listOf(meals.copy(wholeNumbersOnly = false, firstLabel = "Planned"))),
        ).single()
        assertEquals(listOf("turkey", "ham"), decoded.choices.map { it.id })
        assertFalse(decoded.wholeNumbersOnly)
        assertEquals("Planned", decoded.firstLabel)
    }

    @Test
    fun `a stored number too long to spell out is skipped`() {
        val raw = json.parseToJsonElement(
            """[{"item": "turkey", "first": 1e100000000, "second": 1e-100000000}, {"item": "ham", "first": 2.5e3}]""",
        )

        val rows = ComparisonValues.decode(raw)

        assertEquals(listOf(ComparisonRow("turkey"), ComparisonRow("ham", first = "2500")), rows)
    }
}
