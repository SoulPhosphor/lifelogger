package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Permanently deleting an option removes exactly that option's answers: other
 * selections, other fields, the Notes and the entries themselves all stay.
 */
class ChoiceDeletionTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val pet = FieldDef(
        id = "pet",
        label = "Pet",
        type = FieldType.MULTIPLE,
        choices = listOf(ChoiceOption("cat", "Cat"), ChoiceOption("dog", "Dog")),
    )
    private val best = FieldDef(
        id = "best",
        label = "Best",
        type = FieldType.DROPDOWN,
        choices = listOf(ChoiceOption("b-cat", "Cat"), ChoiceOption("b-dog", "Dog", retired = true)),
    )
    private val note = FieldDef(id = "note", label = "Note", type = FieldType.TEXT)
    private val schema = FormMarkdownParser.encodeFields(listOf(pet, best, note))

    @Test
    fun deletingAMultipleChoiceOptionKeepsTheOtherSelections() {
        val result = ChoiceDeletion.fromForm(
            schemaJson = schema,
            entryValues = listOf(
                """{"pet":["cat","dog"],"best":"b-cat","note":"hi","__notes__":"n"}""",
                """{"pet":["cat"]}""",
                """{"note":"untouched"}""",
            ),
            calendarConfigs = emptyList(),
            fieldId = "pet",
            optionId = "cat",
        )!!
        val fields = json.decodeFromString(ListSerializer(FieldDef.serializer()), result.schemaJson)
        assertEquals(listOf("dog"), fields[0].choices.map { it.id })
        assertEquals(best.choices, fields[1].choices)

        val first = EntryValues.decode(result.entryValues[0])
        assertEquals(listOf("dog"), EntryValues.stringList(first, pet))
        assertEquals("b-cat", EntryValues.rawValue(first, best))
        assertEquals("hi", EntryValues.rawValue(first, note))
        assertEquals("n", EntryValues.notes(first))
        // The only selection was the deleted one: the field is now unanswered.
        assertFalse("pet" in EntryValues.decode(result.entryValues[1]))
        assertEquals("""{"note":"untouched"}""", result.entryValues[2])
        assertEquals(3, result.entryValues.size)
    }

    @Test
    fun deletingASingleChoiceAnswerLeavesTheFieldUnanswered() {
        val result = ChoiceDeletion.fromForm(
            schemaJson = schema,
            entryValues = listOf("""{"best":"b-dog","note":"keep"}"""),
            calendarConfigs = emptyList(),
            fieldId = "best",
            optionId = "b-dog",
        )!!
        val values = EntryValues.decode(result.entryValues.single())
        assertNull(EntryValues.rawValue(values, best))
        assertEquals("keep", values["note"]!!.jsonPrimitive.content)
    }

    @Test
    fun onlyTheNamedFieldsOptionIsRemovedEvenWhenAnotherFieldSharesTheName() {
        val result = ChoiceDeletion.fromForm(
            schemaJson = schema,
            entryValues = listOf("""{"pet":["cat"],"best":"b-cat"}"""),
            calendarConfigs = emptyList(),
            fieldId = "best",
            optionId = "b-cat",
        )!!
        val values = EntryValues.decode(result.entryValues.single())
        assertEquals(listOf("cat"), EntryValues.stringList(values, pet))
        assertNull(EntryValues.rawValue(values, best))
    }

    @Test
    fun aCalendarCountingTheOptionStopsPointingAtIt() {
        val counting = CalendarConfigCodec.encode(CalendarConfig(sourceField = "pet", matchOption = "cat"))
        val other = CalendarConfigCodec.encode(CalendarConfig(sourceField = "pet", matchOption = "dog"))
        val result = ChoiceDeletion.fromForm(schema, emptyList(), listOf(counting, other, ""), "pet", "cat")!!
        assertNull(CalendarConfigCodec.decode(result.calendarConfigs[0]).matchOption)
        assertEquals(other, result.calendarConfigs[1])
        assertEquals("", result.calendarConfigs[2])
    }

    @Test
    fun anUnreadableSchemaWritesNothing() {
        assertNull(ChoiceDeletion.fromForm("oops", listOf("{}"), emptyList(), "pet", "cat"))
    }

    @Test
    fun deletingACategoryClearsOnlyThatAnswer() {
        val kind = IdeaFieldDef(
            id = "kind",
            label = "Kind",
            type = FieldType.DROPDOWN,
            choices = listOf(ChoiceOption("home", "Home"), ChoiceOption("work", "Work")),
        )
        val fieldsJson = json.encodeToString(ListSerializer(IdeaFieldDef.serializer()), listOf(kind))
        val result = ChoiceDeletion.fromIdeaLog(
            fieldsJson,
            listOf("""{"kind":"home","text":"a"}""", """{"kind":"work"}"""),
            "kind",
            "home",
        )!!
        val fields = json.decodeFromString(ListSerializer(IdeaFieldDef.serializer()), result.fieldsJson)
        assertEquals(listOf("work"), fields.single().choices.map { it.id })
        val first = IdeaValues.decode(result.entryValues[0])
        assertNull(IdeaValues.rawValue(first, kind))
        assertEquals("a", first["text"]!!.jsonPrimitive.content)
        assertEquals("""{"kind":"work"}""", result.entryValues[1])
    }
}
