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
    fun csvHasNoEntryIdColumn() {
        val entry = LogEntry(id = 42, templateId = 1, createdAt = "2026-10-22T08:00:00Z", valuesJson = "{}")
        val lines = CsvBuilder.build(listOf(field), listOf(entry)).split("\r\n")
        assertEquals("created_at,Groceries,notes", lines[0])
        assertEquals("2026-10-22T08:00:00Z,,", lines[1])
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

    private fun formFile(fieldDef: FieldDef, rows: List<ComparisonRow> = emptyList()): String {
        val entries = if (rows.isEmpty()) {
            emptyList()
        } else {
            listOf(
                LogEntry(
                    id = 1,
                    templateId = 1,
                    createdAt = "2026-10-22T08:00:00Z",
                    valuesJson = EntryValues.encode(mapOf(fieldDef.id to ComparisonValues.encode(rows)!!)),
                ),
            )
        }
        return BackupCodec.encode(
            BackupFile.single(
                exportedAt = "2026-10-22T12:00:00Z",
                category = BackupCategory.FORMS,
                payload = BackupPayload(
                    forms = listOf(
                        BackupCodec.logOf(
                            LogTemplate(id = 1, uuid = "form", name = "Shopping", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(listOf(fieldDef))),
                            entries,
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun aListItemQuantitiesFieldNeedsBackupVersion12() {
        val text = formFile(field)
        assertEquals(BackupFile.VERSION, BackupCodec.decode(text).version)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 11"))
        }
        // A version-12 file without check boxes or averages re-encodes as version 12.
        val asVersion12 = text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 12")
        val decoded = BackupCodec.decode(asVersion12)
        assertEquals(12, decoded.version)
        assertEquals(asVersion12, BackupCodec.encode(decoded))
    }

    @Test
    fun checkBoxesAveragesAndSavedCheckMarksNeedBackupVersion13() {
        val files = listOf(
            formFile(field.copy(addCheckbox = true)),
            formFile(field.copy(showCheckTotal = true)),
            formFile(field.copy(showAverage = true)),
            // A check mark saved before Add Checkbox was turned off again.
            formFile(field, listOf(ComparisonRow("soda", "5", checked = true))),
        )
        files.forEach { text ->
            assertEquals(13, BackupCodec.decode(text).version)
            assertThrows(IllegalArgumentException::class.java) {
                BackupCodec.decode(text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 12"))
            }
        }
    }

    @Test
    fun theLinesUnderTheTableFollowTheirToggles() {
        val checked = listOf(ComparisonRow("soda", "5", checked = true), ComparisonRow("juice", "10"))
        assertEquals(emptyList<ComparisonSummaryLine>(), ComparisonValues.summaryLines(field, checked))

        val all = field.copy(addQuantities = true, showAverage = true, addCheckbox = true, showCheckTotal = true)
        assertEquals(
            listOf("Total Quantity" to "15", "Average of Numbers" to "7.5", "Total Amount of Check Marks" to "1"),
            ComparisonValues.summaryLines(all, checked).map { it.label to ComparisonValues.format(it.value!!) },
        )
        assertEquals(
            "Soda: Amount 5, Checked; Juice: Amount 10; Total Quantity: 15; Average of Numbers: 7.5; Total Amount of Check Marks: 1",
            ComparisonValues.display(all, checked),
        )

        // Only the check mark count: no Total Quantity line.
        val checksOnly = field.copy(addCheckbox = true, showCheckTotal = true)
        assertEquals(
            listOf("Total Amount of Check Marks"),
            ComparisonValues.summaryLines(checksOnly, checked).map { it.label },
        )
        // The count needs Add Checkbox on; with it off a saved check mark is kept but not shown.
        assertEquals(emptyList<ComparisonSummaryLine>(), ComparisonValues.summaryLines(field.copy(showCheckTotal = true), checked))
        assertEquals("Soda: Amount 5; Juice: Amount 10", ComparisonValues.display(field, checked))
    }

    @Test
    fun theAverageRoundsToTwoPlaces() {
        val rows = listOf(ComparisonRow("soda", "1"), ComparisonRow("juice", "1"), ComparisonRow("water", "2"))
        assertEquals("1.33", ComparisonValues.format(ComparisonValues.average(rows)!!))
        assertEquals("5", ComparisonValues.format(ComparisonValues.average(listOf(ComparisonRow("soda", "5")))!!))
        assertNull(ComparisonValues.average(listOf(ComparisonRow("soda"))))
    }

    @Test
    fun aCheckMarkIsStoredAndReadBack() {
        val rows = listOf(ComparisonRow("soda", "5", checked = true), ComparisonRow("juice", checked = false))
        val stored = ComparisonValues.encode(rows)!!
        assertEquals("""[{"item":"soda","first":5,"checked":true},{"item":"juice"}]""", stored.toString())
        assertEquals(rows, ComparisonValues.decode(stored))
        // A check mark without an item blocks Save like a number does.
        assertTrue(ComparisonValues.needsItem(ComparisonRow(checked = true)))
    }

    @Test
    fun formMarkdownRoundTripsTheCheckBoxAndAverageToggles() {
        val written = field.copy(addCheckbox = true, showCheckTotal = true, showAverage = true)
        val parsed = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Shopping", listOf(written))).fields.single()
        assertTrue(parsed.addCheckbox)
        assertTrue(parsed.showCheckTotal)
        assertTrue(parsed.showAverage)
    }
}
