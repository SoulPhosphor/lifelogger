package com.datadragon.app.data

import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WeekdayTest {
    private val days = Weekday.newChoices()
    private val settings = WeekdaySettings(weekdayIds = Weekday.identities(days))
    private val monday = days.first { it.weekday == 1 }
    private val tuesday = days.first { it.weekday == 2 }
    private val date = LocalDate.of(2026, 11, 2)
    private val field = FieldDef(id = StableUuid.createNew(), label = Weekday.LABEL, type = FieldType.DAY_OF_WEEK, choices = days, weekday = settings)

    @Test fun spellingOrderRetirementAndRestoreKeepEveryIdentityAndTheWeekdayAssociation() {
        var edited = ChoiceEdits.renamed(days, monday.id, "My Munday")
        edited = ChoiceEdits.movedActive(edited, monday.id, 6)
        edited = ChoiceEdits.preselected(edited, tuesday.id, single = false)
        assertEquals(setOf(monday.id), Weekday.defaults(edited, settings, date))
        edited = ChoiceEdits.retired(edited, monday.id)
        val restored = Weekday.restoreWeekdays(edited, Weekday.seeds(settings))
        assertEquals(days.map { it.id }.toSet(), restored.map { it.id }.toSet())
        assertEquals("My Munday", restored.first { it.id == monday.id }.label)
        assertEquals(1, restored.first { it.id == monday.id }.weekday)
        assertEquals(setOf(monday.id), Weekday.defaults(restored, settings, date))
        assertEquals(field.id, field.copy(label = "Spelling changed", choices = restored).id)
    }

    @Test fun singleCurrentDayOverridesManualDefaultsAndMultipleIncludesBoth() {
        val selected = days.map { it.copy(preselected = it.id == tuesday.id) }
        assertEquals(setOf(monday.id), Weekday.defaults(selected, settings, date))
        assertEquals(setOf(monday.id, tuesday.id), Weekday.defaults(selected, settings.copy(onlyOne = false), date))
        assertEquals(setOf(tuesday.id), Weekday.defaults(selected, settings.copy(defaultEnteredDay = false), date))
        assertTrue(Weekday.defaults(days, settings.copy(defaultEnteredDay = false), date).isEmpty())
    }

    @Test fun unsavedOptionsAreRemovedAndSavedOptionsRetireWithoutChangingIds() {
        val custom = ChoiceOption.createNew("Odd Day")
        val withNew = ChoiceEdits.withNew(days, custom)
        assertFalse(ChoiceEdits.removed(withNew, custom.id, days.map { it.id }.toSet()).any { it.id == custom.id })
        val retired = ChoiceEdits.removed(withNew, custom.id, withNew.map { it.id }.toSet())
        assertTrue(retired.single { it.id == custom.id }.retired)
        assertEquals(custom.id, ChoiceEdits.restored(retired, custom.id).last().id)
        val removedNewWeekday = ChoiceEdits.removed(days, monday.id, emptySet())
        assertFalse(removedNewWeekday.any { it.retired })
        assertEquals(monday.id, Weekday.restoreWeekdays(removedNewWeekday, Weekday.seeds(settings)).single { it.weekday == 1 }.id)
    }

    @Test fun jsonRoundTripsNeverInventOrReplaceIdsAndHistoricMultipleAnswersSurviveModeChanges() {
        val source = field.copy(weekday = settings.copy(onlyOne = false), choices = ChoiceEdits.retired(days, tuesday.id))
        val encoded = FormMarkdownParser.encodeFields(listOf(source))
        repeat(3) { assertEquals(source, Json.decodeFromString<List<FieldDef>>(encoded).single()) }
        val raw = EntryValues.stringArray(listOf(monday.id, tuesday.id))
        assertEquals("Monday, Tuesday", EntryValues.displayValue(source.copy(weekday = settings), EntryValues.decode(EntryValues.encode(mapOf(field.id to raw)))))
        assertEquals(listOf(monday.id, tuesday.id), Weekday.selected(raw))
        assertEquals(listOf(monday.id, tuesday.id), Weekday.fromClicker(Weekday.clickerValue(listOf(monday.id, tuesday.id))))
    }

    @Test fun exportsUseLabelsAndMarkdownKeepsRenamedWeekdayMeaningAndCustomDefaults() {
        val custom = ChoiceOption.createNew("Odd Day", preselected = true)
        val choices = ChoiceEdits.withNew(ChoiceEdits.renamed(days, monday.id, "My Munday"), custom)
        val source = field.copy(choices = choices, weekday = settings.copy(onlyOne = false))
        val markdown = FormMarkdownGenerator.generate("Test", listOf(source))
        val parsed = FormMarkdownParser.parse(markdown)
        assertTrue(parsed.issues.toString(), parsed.issues.isEmpty())
        val recreated = parsed.fields.single()
        val renamed = recreated.choices.first { it.label == "My Munday" }
        assertEquals(1, renamed.weekday)
        assertEquals(setOf(renamed.id, recreated.choices.first { it.label == "Odd Day" }.id), Weekday.defaults(recreated.choices, recreated.weekday, date))
        val entry = LogEntry(templateId = 1, createdAt = "2026-11-02T12:00:00Z", valuesJson = EntryValues.encode(mapOf(source.id to EntryValues.stringArray(listOf(monday.id, custom.id)))))
        assertTrue(CsvBuilder.build(listOf(source), listOf(entry)).contains("My Munday; Odd Day"))
        assertEquals("My Munday, Odd Day", EntryValues.displayValue(source, EntryValues.decode(entry.valuesJson)))
        assertTrue(CleanJsonBuilder.build(LogTemplate(uuid = "test", name = "Test", createdAt = 1, schemaJson = "[]"), listOf(source), listOf(entry)).contains("My Munday"))
    }

    @Test fun requiredClickerWeekdaysBlockOnlyWhenTheyHaveNoAnswer() {
        val required = ClickerField(id = "weekday", label = Weekday.LABEL, type = ClickerFieldType.DAY_OF_WEEK, required = true, choices = days, weekday = settings)
        assertTrue(Weekday.missingRequired(listOf(required), Weekday.clickerDefaults(listOf(required), date)).isEmpty())
        assertEquals(listOf("weekday"), Weekday.missingRequired(listOf(required), emptyMap()))
        assertTrue(Weekday.missingRequired(listOf(required.copy(required = false)), emptyMap()).isEmpty())
    }
    @Test fun crossingMidnightUpdatesOnlyUntouchedWeekdayDefaults() {
        val original = mapOf(field.id to EntryValues.stringArray(listOf(monday.id)), "date" to EntryValues.string("2026-10-31"), "time" to EntryValues.string("23:59"))
        val fields = listOf(Triple(field.id, days, settings))
        val afterMidnight = Weekday.atCreation(original, fields, date.plusDays(1), setOf(field.id))
        assertEquals(listOf(tuesday.id), Weekday.selected(afterMidnight[field.id]))
        assertEquals(original["date"], afterMidnight["date"])
        assertEquals(original["time"], afterMidnight["time"])
        assertEquals(original, Weekday.atCreation(original, fields, date.plusDays(1), emptySet()))
        val manual = mapOf(field.id to EntryValues.stringArray(listOf(tuesday.id)))
        assertEquals(manual, Weekday.atCreation(manual, listOf(Triple(field.id, days, settings.copy(defaultEnteredDay = false))), date, setOf(field.id)))
    }

}
