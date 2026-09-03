package com.datadragon.app.ui.screens

import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import org.junit.Assert.assertEquals
import org.junit.Test

class NewEntryValidationTest {

    @Test
    fun missingRequiredFieldsStayInVisibleFormOrder() {
        val fields = listOf(
            FieldDef("Optional", FieldType.TEXT),
            FieldDef("First missing", FieldType.TEXT, required = true),
            FieldDef("Completed", FieldType.NUMBER, required = true),
            FieldDef("Second missing", FieldType.MULTIPLE, required = true),
        )

        val missing = missingRequiredFieldLabels(
            fields = fields,
            textValues = mapOf("Completed" to "4"),
            multiValues = emptyMap(),
        )

        assertEquals(listOf("First missing", "Second missing"), missing)
    }

    @Test
    fun whitespaceDoesNotCompleteARequiredTextField() {
        val fields = listOf(FieldDef("Required", FieldType.TEXT, required = true))

        val missing = missingRequiredFieldLabels(
            fields = fields,
            textValues = mapOf("Required" to "   "),
            multiValues = emptyMap(),
        )

        assertEquals(listOf("Required"), missing)
    }
}
