package com.datadragon.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdvancedSearchTest {
    private val text = FieldDef(id = "text", label = "Activity", type = FieldType.TEXT)
    private val multiline = FieldDef(id = "multi", label = "Details", type = FieldType.MULTILINE)
    private val location = FieldDef(id = "location", label = "Location", type = FieldType.MULTIPLE,
        choices = listOf(ChoiceOption("bedroom", "Bedroom"), ChoiceOption("retired", "Old Room", retired = true)))
    private val fields = listOf(text, multiline, location)
    private fun entry(id: Long, values: String) = LogEntry(id = id, templateId = 1,
        createdAt = "2026-10-17T10:00:00Z", valuesJson = values)
    private val records = listOf(
        entry(1, """{"text":"DataDragon","multi":"burning incense","location":["bedroom","retired"],"__notes__":"Vacuum, vacuum"}"""),
        entry(2, """{"text":"drag","multi":"Other details","location":["bedroom"]}"""),
        entry(3, """{"text":"Dragon","multi":"vacuum"}"""),
    )

    @Test fun partialWholeCaseLiteralAndUnicodeMatching() {
        fun matches(q: String, value: String, whole: Boolean = false, case: Boolean = false) =
            AdvancedSearch.matcher(AdvancedSearchCriteria(q, whole, case))(value)
        assertTrue(matches("drag", "DataDragon"))
        assertTrue(matches("drag", "DRAGON"))
        assertTrue(matches("drag", "drag", whole = true))
        assertFalse(matches("drag", "dragon", whole = true))
        assertFalse(matches("drag", "DataDragon", whole = true))
        assertTrue(matches("data dragon", "(DATA DRAGON)!", whole = true))
        assertTrue(matches("(draft)", "read (draft), now", whole = true))
        assertFalse(matches("a.b", "axb"))
        assertTrue(matches("a.b", "a.b"))
        assertFalse(matches("Drag", "drag", case = true))
        assertTrue(matches("κόσμος", "κόσμος!", whole = true))
        assertFalse(matches("κόσμος", "μικρόκόσμος", whole = true))
        assertTrue(matches("café", "cafe\u0301!", whole = true))
        assertFalse(matches("a", "a\u0301", whole = true))
    }

    @Test fun allTextAndNotesAreSearchedRegardlessOfPosition() {
        assertEquals(listOf(1L, 3L), AdvancedSearch.forms(records, fields, AdvancedSearchCriteria("vacuum")).map { it.id })
        assertEquals(listOf(1L), AdvancedSearch.forms(records, fields, AdvancedSearchCriteria("vacuum", textFieldId = EntryValues.NOTES_KEY)).map { it.id })
        assertEquals(listOf(3L), AdvancedSearch.forms(records, fields, AdvancedSearchCriteria("vacuum", textFieldId = "multi")).map { it.id })
        assertEquals(listOf(1L), AdvancedSearch.forms(records, fields, AdvancedSearchCriteria("burning")).map { it.id })
    }

    @Test fun choicesMatchMembershipAndRetainIdentityThroughRenamesAndRetirement() {
        val criteria = AdvancedSearchCriteria(answerFieldId = "location", answer = "bedroom")
        assertEquals(listOf(1L, 2L), AdvancedSearch.forms(records, fields, criteria).map { it.id })
        val changed = location.copy(label = "Renamed Field", choices = location.choices.reversed().map { it.copy(label = "Renamed", retired = true) })
        assertEquals(listOf(1L, 2L), AdvancedSearch.forms(records, listOf(changed), criteria).map { it.id })
        assertEquals(listOf(1L), AdvancedSearch.forms(records, fields, criteria.copy(answer = "retired")).map { it.id })
        assertTrue(AdvancedSearch.answers(location).last().label.contains("Retired"))
        assertEquals(listOf(1L), AdvancedSearch.forms(records, fields, criteria.copy(query = "burning", textFieldId = "multi")).map { it.id })
    }

    @Test fun clearAndNoResultsDoNotModifyOrReorderRecords() {
        assertTrue(AdvancedSearch.forms(records, fields, AdvancedSearchCriteria("absent")).isEmpty())
        assertEquals(records.reversed(), AdvancedSearch.forms(records.reversed(), fields, AdvancedSearchCriteria()))
        assertEquals(3, records.size)
    }

    @Test fun summariesCountCardsAndOnlyExistForActiveFilters() {
        assertNull(AdvancedSearch.summary(AdvancedSearchCriteria(), 3, fields))
        val criteria = AdvancedSearchCriteria("vacuum")
        val matches = AdvancedSearch.forms(records, fields, criteria)
        assertEquals("\"vacuum\" · 2 matching entries", AdvancedSearch.summary(criteria, matches.size, fields))
        assertEquals("Location: Bedroom · 2 matching entries", AdvancedSearch.summary(AdvancedSearchCriteria(answerFieldId = "location", answer = "bedroom"), 2, fields))
    }

    @Test fun dailySearchReturnsWholeDatedCardsIncludingSubitemsAndCompletedTasks() {
        val card = DailyList(id = 1, uuid = "day", date = LocalDate.of(2026, 10, 17), title = "My Day", createdAt = 1)
        val other = card.copy(id = 2, uuid = "other", date = card.date.minusDays(1), title = "Other")
        val task = DailyListItem(id = 1, uuid = "task", dailyListId = 1, text = "Vacuum upstairs", completed = true, indent = 1, position = 0)
        val items = mapOf(1L to listOf(task))
        assertEquals(listOf(card), AdvancedSearch.days(listOf(other, card), items, AdvancedSearchCriteria("vacuum")))
        assertEquals(listOf(card), AdvancedSearch.days(listOf(other, card), items, AdvancedSearchCriteria("my day", wholeWords = true)))
        assertEquals(listOf(card, other), AdvancedSearch.days(listOf(card, other), items, AdvancedSearchCriteria()))
        assertEquals(LocalDate.of(2026, 10, 17), card.date)
        assertTrue(task.completed)
    }
}
