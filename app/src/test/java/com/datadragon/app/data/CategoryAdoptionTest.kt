package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive

/**
 * Changing a saved Idea Log field to Categories points its ideas' text answers
 * at the matching categories, keeping unmatched text as retired categories.
 */
class CategoryAdoptionTest {

    private var next = 0
    private val newId = { "new-${++next}" }

    private val asText = IdeaFieldDef(id = "kind", label = "Kind", type = FieldType.TEXT)
    private val asCategories = asText.copy(
        type = FieldType.DROPDOWN,
        choices = listOf(ChoiceOption("home-id", "Home"), ChoiceOption("work-id", "Work")),
    )
    private val note = IdeaFieldDef(id = "note", label = "Note", type = FieldType.TEXT)

    @Test
    fun textAnswersPointAtMatchingCategoriesAndUnmatchedTextIsRetired() {
        val result = CategoryAdoption.adopt(
            previous = listOf(asText, note),
            fields = listOf(asCategories, note),
            entryValues = listOf(
                """{"kind":"Home","note":"Home"}""",
                """{"kind":" work "}""",
                """{"kind":"Garden"}""",
                """{"kind":"garden"}""",
                """{"note":"x"}""",
            ),
            newId = newId,
        )
        val kind = result.fields.first()
        assertEquals(listOf("Home", "Work"), kind.choices.activeChoices.map { it.label })
        val garden = kind.choices.single { it.retired }
        assertEquals("Garden", garden.label)
        assertEquals(
            listOf("home-id", "work-id", garden.id, garden.id, null),
            result.entryValues.map { (IdeaValues.decode(it)["kind"] as? JsonPrimitive)?.content },
        )
        // Other fields are untouched, and an idea with nothing to convert keeps its stored text.
        assertEquals("Home", IdeaValues.rawValue(IdeaValues.decode(result.entryValues[0]), note))
        assertEquals("""{"note":"x"}""", result.entryValues.last())
        assertEquals(note, result.fields[1])
    }

    @Test
    fun idsAlreadyStoredAreKeptAndUnconvertedFieldsAreLeftAlone() {
        val keptChoices = asCategories.copy(type = FieldType.TEXT)
        val result = CategoryAdoption.adopt(
            previous = listOf(keptChoices),
            fields = listOf(asCategories),
            entryValues = listOf("""{"kind":"work-id"}""", """{"kind":"Home"}"""),
            newId = newId,
        )
        assertEquals("""{"kind":"work-id"}""", result.entryValues[0])
        assertEquals("""{"kind":"home-id"}""", result.entryValues[1])
        assertEquals(asCategories, result.fields.single())

        // A field that was already Categories, or is new, is never rewritten.
        val values = listOf("""{"kind":"Home"}""")
        val unchanged = CategoryAdoption.adopt(listOf(asCategories), listOf(asCategories), values, newId)
        assertSame(values, unchanged.entryValues)
        val added = CategoryAdoption.adopt(emptyList(), listOf(asCategories), values, newId)
        assertSame(values, added.entryValues)
        assertTrue(next == 0)
    }
}
