package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A List Item Quantities field keeps rows of an item and its one amount: no
 * second number, no difference, and a total of the amounts only with "Add
 * Item Quantities" on.
 */
class ListItemQuantitiesTest {

    private val field = FieldDef(
        id = "groceries",
        label = "Groceries",
        type = FieldType.LIST_ITEM_QUANTITIES,
        choices = listOf(ChoiceOption("soda", "Soda"), ChoiceOption("juice", "Juice")),
    )
    private val rows = listOf(ComparisonRow("soda", "5"), ComparisonRow("juice", "10"))

    @Test
    fun theCaptionsDefaultToItemAmountAndTotalQuantity() {
        assertEquals("Item", field.comparisonItemLabel)
        assertEquals("Amount", field.comparisonFirstLabel)
        assertEquals("Total Quantity", field.comparisonTotalLabel)
        assertTrue(field.wholeNumbersOnly)
        assertFalse(field.addQuantities)
        assertEquals("Count", field.copy(firstLabel = "Count").comparisonFirstLabel)
        // Comparison keeps its own defaults.
        val comparison = field.copy(type = FieldType.COMPARISON)
        assertEquals("Expected", comparison.comparisonFirstLabel)
        assertEquals("Total Difference", comparison.comparisonTotalLabel)
    }

    @Test
    fun theTotalShowsOnlyWithAddItemQuantitiesOn() {
        assertNull(ComparisonValues.total(field, rows))
        assertEquals("Soda: Amount 5; Juice: Amount 10", ComparisonValues.display(field, rows))

        val adding = field.copy(addQuantities = true)
        assertEquals("15", ComparisonValues.format(ComparisonValues.total(adding, rows)!!))
        assertEquals(
            "Soda: Amount 5; Juice: Amount 10; Total Quantity: 15",
            ComparisonValues.display(adding, rows),
        )
        assertNull(ComparisonValues.total(adding, listOf(ComparisonRow("soda"))))
    }

    @Test
    fun rowsStoreOnlyTheItemAndAmount() {
        val stored = ComparisonValues.encode(rows)!!
        assertEquals("""[{"item":"soda","first":5},{"item":"juice","first":10}]""", stored.toString())
        assertEquals(rows, ComparisonValues.decode(stored))
    }

    @Test
    fun csvExportsTheRowsAsOneLine() {
        val values = EntryValues.encode(mapOf(field.id to ComparisonValues.encode(rows)!!))
        val csv = CsvBuilder.build(listOf(field), listOf(LogEntry(templateId = 1, createdAt = "2026-10-22T08:00:00Z", valuesJson = values)))
        assertTrue(csv, csv.contains("Soda: Amount 5; Juice: Amount 10"))
    }

    @Test
    fun formMarkdownRoundTripsTheField() {
        val written = field.copy(
            digits = 4,
            itemLabel = "Food",
            firstLabel = "Count",
            totalLabel = "All Food",
            addQuantities = true,
        )
        val markdown = FormMarkdownGenerator.generate("Shopping", listOf(written))
        assertTrue(markdown, markdown.contains("type: list_item_quantities\n"))
        assertTrue(markdown, markdown.contains("add_quantities: true\n"))
        val parsed = FormMarkdownParser.parse(markdown).fields.single()
        assertEquals(FieldType.LIST_ITEM_QUANTITIES, parsed.type)
        assertEquals(listOf("Soda", "Juice"), parsed.choices.map { it.label })
        assertEquals(4, parsed.digits)
        assertEquals("Food", parsed.itemLabel)
        assertEquals("Count", parsed.firstLabel)
        assertEquals("All Food", parsed.totalLabel)
        assertTrue(parsed.addQuantities)
        assertTrue(parsed.wholeNumbersOnly)
        assertFalse(parsed.required)
        assertFalse(parsed.clickerCounting)
    }

    @Test
    fun deletingAnOptionDropsOnlyItsRows() {
        val values = EntryValues.encode(mapOf(field.id to ComparisonValues.encode(rows)!!))
        val left = EntryValues.withoutOption(values, field.id, "soda")
        assertEquals(listOf(ComparisonRow("juice", "10")), ComparisonValues.rows(EntryValues.decode(left), field))
    }

    @Test
    fun theTypeListsBelowComparisonAndAboveTheDateTypes() {
        val order = FieldType.pickerOrder
        assertEquals(FieldType.entries.toSet(), order.toSet())
        assertEquals(FieldType.entries.size, order.size)
        assertEquals(order.indexOf(FieldType.YESNO) + 1, order.indexOf(FieldType.COMPARISON))
        assertEquals(order.indexOf(FieldType.COMPARISON) + 1, order.indexOf(FieldType.LIST_ITEM_QUANTITIES))
        assertEquals(order.indexOf(FieldType.LIST_ITEM_QUANTITIES) + 1, order.indexOf(FieldType.DATE))
    }

    @Test
    fun aListItemQuantitiesFieldNeedsBackupVersion12() {
        val text = BackupCodec.encode(
            BackupFile.single(
                exportedAt = "2026-10-22T12:00:00Z",
                category = BackupCategory.FORMS,
                payload = BackupPayload(
                    forms = listOf(
                        BackupCodec.logOf(
                            LogTemplate(id = 1, uuid = "form", name = "Shopping", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(listOf(field))),
                            emptyList(),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(12, BackupCodec.decode(text).version)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 11"))
        }
    }
}
