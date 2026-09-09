package com.datadragon.app.ui

import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogEntryOrderingTest {

    @Test
    fun automaticTimestampOrdersNewestFirstByDefault() {
        val later = entry(2, "2026-09-03T10:00:00-05:00")
        val earlier = entry(1, "2026-09-03T08:00:00-05:00")

        val sorted = sortLogEntries(listOf(earlier, later), sortField = null, newestFirst = true)

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun automaticTimestampCanRunOldestFirst() {
        val later = entry(2, "2026-09-03T10:00:00-05:00")
        val earlier = entry(1, "2026-09-03T08:00:00-05:00")

        val sorted = sortLogEntries(listOf(later, earlier), sortField = null, newestFirst = false)

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun selectedDateTimeFieldOrdersByUserValue() {
        val furtherOut = entry(1, "2026-09-03T08:00:00-05:00", mapOf("When" to "2026-09-05T12:00"))
        val nearer = entry(2, "2026-09-03T10:00:00-05:00", mapOf("When" to "2026-09-04T12:00"))

        val sorted = sortLogEntries(
            listOf(nearer, furtherOut),
            sortField = field("When", FieldType.DATETIME),
            newestFirst = true,
        )

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun selectedDateFieldSortsAtStartOfItsDay() {
        val dayBefore = entry(1, "2026-09-03T08:00:00-05:00", mapOf("Day" to "2026-09-04"))
        val dayAfter = entry(2, "2026-09-03T10:00:00-05:00", mapOf("Day" to "2026-09-05"))

        val sorted = sortLogEntries(
            listOf(dayBefore, dayAfter),
            sortField = field("Day", FieldType.DATE),
            newestFirst = true,
        )

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun entriesWithNoValueForTheCategoryGoToTheBottomNewestFirst() {
        val dated = entry(1, "2026-09-03T08:00:00-05:00", mapOf("When" to "2026-09-05T12:00"))
        val blank = entry(2, "2026-09-03T23:00:00-05:00")

        val sorted = sortLogEntries(
            listOf(blank, dated),
            sortField = field("When", FieldType.DATETIME),
            newestFirst = true,
        )

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun entriesWithNoValueForTheCategoryStayAtTheBottomOldestFirst() {
        val dated = entry(1, "2026-09-03T08:00:00-05:00", mapOf("When" to "2026-09-05T12:00"))
        val blank = entry(2, "2026-09-01T01:00:00-05:00")

        val sorted = sortLogEntries(
            listOf(blank, dated),
            sortField = field("When", FieldType.DATETIME),
            newestFirst = false,
        )

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun timeOnlyFieldIsNeverUsedAsTheDefaultSortField() {
        val fields = listOf(field("Woke Up", FieldType.TIME))

        val resolved = defaultSortField(fields, template(sortTimestampLabel = "Woke Up"))

        assertNull(resolved)
    }

    @Test
    fun dateFieldCanBeTheDefaultSortField() {
        val fields = listOf(field("Woke Up", FieldType.DATE))

        val resolved = defaultSortField(fields, template(sortTimestampLabel = "Woke Up"))

        assertEquals("Woke Up", resolved?.label)
    }

    @Test
    fun categoriesAlwaysOfferTheAutomaticTimestamp() {
        val fields = listOf(field("Notes", FieldType.TEXT))

        val categories = sortCategoriesOf(fields, template(sortTimestampLabel = null))

        assertEquals(listOf(AUTOMATIC_TIMESTAMP_LABEL), categories.map { it.label })
    }

    @Test
    fun categoriesIncludeOptedInDateFieldsAndTheDefaultSortField() {
        val fields = listOf(
            field("T Rex Woke Up", FieldType.DATETIME).copy(allowOrderFiltering = true),
            field("Fed", FieldType.DATE),
            field("Alarm", FieldType.TIME).copy(allowOrderFiltering = true),
        )

        val categories = sortCategoriesOf(fields, template(sortTimestampLabel = "Fed"))

        assertEquals(
            listOf(AUTOMATIC_TIMESTAMP_LABEL, "T Rex Woke Up", "Fed"),
            categories.map { it.label },
        )
    }

    @Test
    fun resolvedCategoryStartsAtTheFormDefaultAndFollowsThePick() {
        val fields = listOf(
            field("Woke Up", FieldType.DATETIME),
            field("Fed", FieldType.DATETIME).copy(allowOrderFiltering = true),
        )
        val form = template(sortTimestampLabel = "Woke Up")

        assertEquals("Woke Up", resolveCategory(fields, form, pickedLabel = null)?.label)
        assertEquals("Fed", resolveCategory(fields, form, pickedLabel = "Fed")?.label)
        // A pick that no longer exists falls back to the form's default.
        assertEquals("Woke Up", resolveCategory(fields, form, pickedLabel = "Deleted")?.label)
    }

    private fun field(label: String, type: FieldType) = FieldDef(label = label, type = type)

    private fun template(sortTimestampLabel: String?) = LogTemplate(
        id = 1,
        uuid = "stable-id",
        name = "Mood",
        createdAt = 100,
        schemaJson = "[]",
        sortTimestampLabel = sortTimestampLabel,
    )

    private fun entry(
        id: Long,
        createdAt: String,
        values: Map<String, String> = emptyMap(),
    ): LogEntry = LogEntry(
        id = id,
        templateId = 1,
        createdAt = createdAt,
        valuesJson = EntryValues.encode(values.mapValues { EntryValues.string(it.value) }),
    )
}