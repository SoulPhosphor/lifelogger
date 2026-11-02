package com.datadragon.app.data

import org.junit.Assert.*
import org.junit.Test

class CardCopyTextTest {
    @Test fun listsUseBulletsAndOmitEmptySections() {
        val rows = listOf(CardCopyText.Item("water"), CardCopyText.Item("basil", true), CardCopyText.Item("salt", indent = 1))
        assertEquals("Recipe\n\nUnfinished Items\n• water\n  • salt\n\nCompleted Items\n• basil", CardCopyText.list("Recipe", rows))
        assertEquals("Unfinished Items\n• water", CardCopyText.list("", rows.take(1)))
        assertEquals("Completed Items\n• basil", CardCopyText.list("", rows.filter { it.completed }))
        assertEquals("Recipe", CardCopyText.list("Recipe", listOf(CardCopyText.Item(" "))))
        assertEquals("Recipe\n\n• water\n• basil\n  • salt", CardCopyText.list("Recipe", rows, lucky = true))
    }

    @Test fun formsUseLabelsAndReadableAnswersInFieldOrder() {
        val entry = LogEntry(templateId = 1, createdAt = "2026-11-01T10:00:00Z", valuesJson = """{"ingredients":["water","basil"],"temperature":"100","choice":"id1"}""")
        val fields = listOf(
            FieldDef(id = "ingredients", label = "Ingredients", type = FieldType.TAGS),
            FieldDef(id = "temperature", label = "Temperature", type = FieldType.NUMBER),
            FieldDef(id = "choice", label = "Choice", type = FieldType.DROPDOWN, choices = listOf(ChoiceOption(id = "id1", label = "Saved Answer"))),
        )
        assertEquals("Ingredients: water, basil\nTemperature: 100\nChoice: Saved Answer", CardCopyText.form(entry, fields, emptyList(), false))
    }

    @Test fun ideasCopyFullTextAndFieldsHiddenByPreview() {
        val entry = IdeaEntry(ideaLogId = 1, createdAt = "2026-11-01T10:00:00Z", valuesJson = """{"text":"first\nsecond\nthird","hidden":"saved"}""")
        val fields = listOf(
            IdeaFieldDef(id = "text", label = "Text", type = FieldType.MULTILINE, truncateInEntireCard = true, entireCardLines = 1),
            IdeaFieldDef(id = "hidden", label = "Other", type = FieldType.TEXT, includeInPreview = false),
        )
        assertEquals("Text: first\nsecond\nthird\nOther: saved", CardCopyText.idea(entry, fields, false))
    }

    @Test fun clickersReadCurrentNumbersAndUntouchedStartingNumbers() {
        val fields = listOf(ClickerField("count", ClickerFieldType.CLICK_TRACKER, "Count", startingNumber = 5))
        val log = ClickerLog(uuid = "group", title = "Counters", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]", autoDateStamp = false)
        val card = ClickerCard(clickerLogId = 1, uuid = "card", createdAt = 1, valuesJson = "{}")
        assertEquals("Count: 5", CardCopyText.clicker(log, card, fields))
        assertEquals("Count: 9", CardCopyText.clicker(log, card.copy(valuesJson = """{"count":"9"}"""), fields))
    }
}
