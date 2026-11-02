package com.datadragon.app.data

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CardContentTest {
    @Test fun emptyAndWhitespaceValuesDoNotSaveButZeroAndNoDo() {
        assertFalse(CardContent.hasData(emptyMap()))
        assertFalse(CardContent.hasData(mapOf("a" to JsonPrimitive("  "), "b" to JsonArray(emptyList()), "c" to JsonNull)))
        assertFalse(CardContent.hasData(mapOf("a" to JsonArray(listOf(JsonPrimitive(" "))))))
        assertTrue(CardContent.hasData(mapOf("a" to JsonPrimitive("0"))))
        assertTrue(CardContent.hasData(mapOf("a" to JsonPrimitive("No"))))
        assertTrue(CardContent.hasData(mapOf(EntryValues.NOTES_KEY to JsonPrimitive("A note"))))
    }

    @Test fun emptyCompoundInputsAreNotDataButEnteredNumbersAre() {
        val fields = listOf(FieldDef(id = "pressure", label = "Pressure", type = FieldType.BLOOD_PRESSURE))
        assertFalse(CardContent.hasFormData(fields, mapOf("pressure" to JsonPrimitive("/"))))
        assertTrue(CardContent.hasFormData(fields, mapOf("pressure" to JsonPrimitive("120/80"))))
        val ratio = listOf(IdeaFieldDef(id = "ratio", label = "Ratio", type = FieldType.RATIO))
        assertFalse(CardContent.hasIdeaData(ratio, mapOf("ratio" to JsonPrimitive(" / "))))
        assertTrue(CardContent.hasIdeaData(ratio, mapOf("ratio" to JsonPrimitive("0/1"))))
    }
}
