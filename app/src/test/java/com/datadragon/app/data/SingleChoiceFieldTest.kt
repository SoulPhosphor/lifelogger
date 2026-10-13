package com.datadragon.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SingleChoiceFieldTest {

    private val field = FieldDef(label = "Color", type = FieldType.DROPDOWN, options = listOf("Red", "Blue"))

    @Test
    fun topAnswerIsPreselectedOnlyWhenTurnedOn() {
        assertNull(field.preselectedSingleChoice())
        assertEquals("Red", field.copy(topAnswerPreselected = true).preselectedSingleChoice())
    }

    @Test
    fun savedFieldsWithoutTheNewSettingsStayDropdownWithNothingPreselected() {
        val decoded = Json.decodeFromString<List<FieldDef>>(
            """[{"label":"Color","type":"DROPDOWN","options":["Red","Blue"]}]""",
        ).single()
        assertEquals(ChoiceStyle.DROPDOWN, decoded.choiceStyle)
        assertNull(decoded.preselectedSingleChoice())
    }

    @Test
    fun settingsSurviveAnEncodeRoundTrip() {
        val original = field.copy(choiceStyle = ChoiceStyle.DISPLAYED, topAnswerPreselected = true)
        val decoded = Json.decodeFromString<List<FieldDef>>(FormMarkdownParser.encodeFields(listOf(original))).single()
        assertEquals(original, decoded)
    }

    @Test
    fun settingsSurviveAFormMarkdownRoundTrip() {
        val original = field.copy(choiceStyle = ChoiceStyle.DISPLAYED, topAnswerPreselected = true)
        val parsed = FormMarkdownParser.parse(FormMarkdownGenerator.generate("Log", listOf(original))).fields.single()
        assertEquals(original, parsed)
    }

    @Test
    fun defaultSettingsAreLeftOutOfFormMarkdown() {
        val markdown = FormMarkdownGenerator.generate("Log", listOf(field))
        assertEquals(false, markdown.contains("choice_style"))
        assertEquals(false, markdown.contains("top_answer_preselected"))
        assertEquals(field, FormMarkdownParser.parse(markdown).fields.single())
    }
}
