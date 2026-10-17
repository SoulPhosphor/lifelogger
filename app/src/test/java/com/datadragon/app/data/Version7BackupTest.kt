package com.datadragon.app.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Version7BackupTest {

    private fun formFile(fields: List<FieldDef>): String = BackupCodec.encode(
        BackupFile.single(
            exportedAt = "2026-10-13T12:00:00Z",
            category = BackupCategory.FORMS,
            payload = BackupPayload(
                forms = listOf(
                    BackupCodec.logOf(
                        LogTemplate(
                            id = 3,
                            uuid = "form-id",
                            name = "Vitals",
                            createdAt = 100,
                            schemaJson = FormMarkdownParser.encodeFields(fields),
                        ),
                        emptyList(),
                    ),
                ),
            ),
        ),
    )

    // A form as version 7 wrote it, before field and option identities.
    private val displayed = FieldDef(
        label = "Mood",
        type = FieldType.DROPDOWN,
        legacyOptions = listOf("Good", "Bad"),
        choiceStyle = ChoiceStyle.DISPLAYED,
        legacyTopAnswerPreselected = true,
    )
    private val ratio = FieldDef(label = "BP", type = FieldType.RATIO, leftDigits = 3, rightDigits = 3, unit = "mmHg")

    @Test
    fun newFieldSettingsRoundTripAsVersion7() {
        val decoded = BackupCodec.decode(formFile(listOf(displayed, ratio)))
        assertEquals(BackupFile.VERSION, decoded.version)
        assertEquals(9, BackupFile.VERSION)
        val restored = kotlinx.serialization.json.Json.decodeFromString<List<FieldDef>>(decoded.logs.single().schemaJson)
        assertEquals(listOf(displayed, ratio), restored)
    }

    @Test
    fun aVersion6FileCannotCarryTheNewSettings() {
        listOf(displayed, ratio).forEach { field ->
            val text = formFile(listOf(field)).replace("\"version\": ${BackupFile.VERSION}", "\"version\": 6")
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
        }
    }
}
