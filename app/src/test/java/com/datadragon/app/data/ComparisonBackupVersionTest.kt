package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Backups carrying Comparison content are version 10, so an older release
 * rejects them as newer instead of failing their checksum; a version-9 file
 * without it re-encodes as version 9.
 */
class ComparisonBackupVersionTest {

    private val comparison = FieldDef(
        id = "meals",
        label = "Meals",
        type = FieldType.COMPARISON,
        choices = listOf(ChoiceOption("turkey", "Turkey")),
    )

    private fun formFile(fields: List<FieldDef>): String = BackupCodec.encode(
        BackupFile.single(
            exportedAt = "2026-10-20T12:00:00Z",
            category = BackupCategory.FORMS,
            payload = BackupPayload(
                forms = listOf(
                    BackupCodec.logOf(
                        LogTemplate(id = 1, uuid = "form", name = "Food", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(fields)),
                        emptyList(),
                    ),
                ),
            ),
        ),
    )

    private fun clickerFile(log: ClickerLog): String = BackupCodec.encode(
        BackupFile.single(
            exportedAt = "2026-10-20T12:00:00Z",
            category = BackupCategory.CLICKER_DATA,
            payload = BackupPayload(clickerData = listOf(BackupCodec.clickerOf(log, emptyList()))),
        ),
    )

    @Test
    fun aComparisonFieldNeedsVersion10() {
        val text = formFile(listOf(comparison))
        assertEquals(10, BackupCodec.decode(text).version)
        val asVersion9 = text.replace("\"version\": 10", "\"version\": 9")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(asVersion9) }
    }

    @Test
    fun clickerComparisonAndNewOptionNeedVersion10() {
        val field = ClickerField(id = "f", type = ClickerFieldType.COMPARISON, label = "Meals")
        val withField = ClickerLog(uuid = "a", title = "A", createdAt = 1, lastAccessedAt = 1, fieldsJson = ClickerComparison.encodeFields(listOf(field)))
        val withSetting = ClickerLog(uuid = "b", title = "B", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]", allowAdditionsFromForm = true)
        listOf(withField, withSetting).forEach { log ->
            val text = clickerFile(log)
            assertEquals(10, BackupCodec.decode(text).version)
            assertThrows(IllegalArgumentException::class.java) {
                BackupCodec.decode(text.replace("\"version\": 10", "\"version\": 9"))
            }
        }
    }

    @Test
    fun aVersion9FileWithoutComparisonReEncodesAsVersion9() {
        val text = formFile(listOf(FieldDef(id = "note", label = "Note", type = FieldType.TEXT)))
            .replace("\"version\": 10", "\"version\": 9")
        val decoded = BackupCodec.decode(text)
        assertEquals(9, decoded.version)
        assertEquals(text, BackupCodec.encode(decoded))
    }
}
