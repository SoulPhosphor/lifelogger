package com.datadragon.app.data

import com.datadragon.app.ui.screens.offeredChoices
import org.junit.Assert.*
import org.junit.Test

class AlphabeticalChoicesTest {
    private val options = listOf(
        ChoiceOption("z", "Zed", preselected = true),
        ChoiceOption("a", "alpha"),
        ChoiceOption("r", "Retired", retired = true),
    )
    private val field = FieldDef(id = "choice", label = "Choice", type = FieldType.MULTIPLE, choices = options, orderOptionsAlphabetically = true)

    @Test fun activeOfferedAndCopiedValuesUseLabelsWithoutChangingIdentities() {
        assertEquals(listOf("a", "z"), field.activeChoices.map { it.id })
        assertEquals(listOf("a", "r", "z"), field.offeredChoices(listOf("r")).map { it.id })
        assertEquals("alpha, Zed", EntryValues.displayValue(field, EntryValues.decode("""{"choice":["z","a"]}""")))
        val sorted = ChoiceEdits.alphabetical(options)
        assertEquals(listOf("a", "z", "r"), sorted.map { it.id })
        assertTrue(sorted.first { it.id == "z" }.preselected)
        assertTrue(sorted.last().retired)
        val manual = field.copy(choices = sorted, orderOptionsAlphabetically = false)
        assertEquals(listOf("a", "z"), manual.activeChoices.map { it.id })
        assertEquals(listOf("z", "a"), manual.copy(choices = ChoiceEdits.movedActive(sorted, "z", 0)).activeChoices.map { it.id })
    }

    @Test fun settingSurvivesMarkdownAndBackupAndOldDefaultsKeepEncoding() {
        val source = listOf(field, field.copy(id = "single", label = "Single", type = FieldType.DROPDOWN))
        val markdown = FormMarkdownGenerator.generate("Test", source)
        val parsed = FormMarkdownParser.parse(markdown)
        assertTrue(parsed.fields.all { it.orderOptionsAlphabetically })
        assertEquals(listOf("alpha", "Zed"), parsed.fields.first().activeChoices.map { it.label })
        assertFalse(FormMarkdownParser.encodeFields(listOf(field.copy(orderOptionsAlphabetically = false))).contains("orderOptionsAlphabetically"))
        val template = LogTemplate(uuid = "form", name = "Test", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(source))
        val encoded = BackupCodec.encode(BackupFile.single(exportedAt = "2026-11-01T12:00:00Z", category = BackupCategory.FORMS,
            payload = BackupPayload(forms = listOf(BackupCodec.logOf(template, emptyList())))))
        val decoded = BackupCodec.decode(encoded)
        assertEquals(BackupFile.VERSION, decoded.version)
        assertEquals(encoded, BackupCodec.encode(decoded))
        assertEquals(template.schemaJson, decoded.logs.single().schemaJson)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(encoded.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 13")) }
    }
}
