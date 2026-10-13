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
}
