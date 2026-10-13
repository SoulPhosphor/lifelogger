package com.datadragon.app.ui.screens

import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import org.junit.Assert.assertEquals
import org.junit.Test

class NewEntryValidationTest {

    @Test
    fun missingRequiredFieldsStayInVisibleFormOrder() {
        val fields = listOf(
            FieldDef(id = "Optional", label = "Optional", type = FieldType.TEXT),
            FieldDef(id = "First missing", label = "First missing", type = FieldType.TEXT, required = true),
            FieldDef(id = "Completed", label = "Completed", type = FieldType.NUMBER, required = true),
            FieldDef(id = "Second missing", label = "Second missing", type = FieldType.MULTIPLE, required = true),
        )

        val missing = missingRequiredFieldIds(
            fields = fields,
            textValues = mapOf("Completed" to "4"),
            multiValues = emptyMap(),
        )

        assertEquals(listOf("First missing", "Second missing"), missing)
    }

    @Test
    fun whitespaceDoesNotCompleteARequiredTextField() {
        val fields = listOf(FieldDef(id = "Required", label = "Required", type = FieldType.TEXT, required = true))

        val missing = missingRequiredFieldIds(
            fields = fields,
            textValues = mapOf("Required" to "   "),
            multiValues = emptyMap(),
        )

        assertEquals(listOf("Required"), missing)
    }
}
