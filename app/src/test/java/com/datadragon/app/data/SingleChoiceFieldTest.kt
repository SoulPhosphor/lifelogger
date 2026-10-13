package com.datadragon.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleChoiceFieldTest {

    private val field = FieldDef(
        id = "color",
        label = "Color",
        type = FieldType.DROPDOWN,
        choices = listOf(ChoiceOption("red", "Red"), ChoiceOption("blue", "Blue")),
    )

    @Test
    fun anOptionIsPreselectedOnlyWhenMarked() {
        assertNull(field.preselectedSingleChoice())
        val marked = field.copy(choices = ChoiceEdits.preselected(field.choices, "blue", single = true))
        assertEquals("blue", marked.preselectedSingleChoice())
    }

    @Test
    fun aRetiredOptionIsNeverTheDefault() {
        val marked = field.copy(choices = ChoiceEdits.preselected(field.choices, "blue", single = true))
        val retired = marked.copy(choices = marked.choices.map { if (it.id == "blue") it.copy(retired = true) else it })
        assertNull(retired.preselectedSingleChoice())
    }

    @Test
    fun savedFieldsWithoutTheNewSettingsStayDropdownWithNothingPreselected() {
        val decoded = Json.decodeFromString<List<FieldDef>>(
            """[{"id":"color","label":"Color","type":"DROPDOWN","choices":[{"id":"red","label":"Red"}]}]""",
        ).single()
        assertEquals(ChoiceStyle.DROPDOWN, decoded.choiceStyle)
        assertNull(decoded.preselectedSingleChoice())
    }

    @Test
    fun settingsSurviveAnEncodeRoundTrip() {
        val original = field.copy(
            choiceStyle = ChoiceStyle.DISPLAYED,
            choices = ChoiceEdits.preselected(field.choices, "red", single = true),
        )
        val decoded = Json.decodeFromString<List<FieldDef>>(FormMarkdownParser.encodeFields(listOf(original))).single()
        assertEquals(original, decoded)
    }

    @Test
    fun settingsSurviveAFormMarkdownRoundTrip() {
        val original = field.copy(
            choiceStyle = ChoiceStyle.DISPLAYED,
            choices = ChoiceEdits.preselected(field.choices, "blue", single = true),
        )
        val parsed = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Log", listOf(original))).fields.single()
        // Markdown carries names, not ids: a re-parse is a new form with new ids.
        assertEquals(original.choiceStyle, parsed.choiceStyle)
        assertEquals(listOf("Red", "Blue"), parsed.choices.map { it.label })
        assertEquals(parsed.choices[1].id, parsed.preselectedSingleChoice())
    }

    @Test
    fun defaultSettingsAreLeftOutOfFormMarkdown() {
        val markdown = FormMarkdownGenerator.generate("Log", listOf(field))
        assertEquals(false, markdown.contains("choice_style"))
        assertEquals(false, markdown.contains("preselected_option"))
        assertEquals(false, markdown.contains("top_answer_preselected"))
        val parsed = FormMarkdownParser.parse(markdown).fields.single()
        assertEquals(listOf("Red", "Blue"), parsed.choices.map { it.label })
        assertNull(parsed.preselectedSingleChoice())
    }
}
