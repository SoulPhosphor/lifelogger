package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowAdditionsFromFormBackupTest {

    private fun template(allow: Boolean) = LogTemplate(
        id = 3,
        uuid = "allow-id",
        name = "Odor Log",
        createdAt = 100,
        schemaJson = "[]",
        allowAdditionsFromForm = allow,
    )

    private fun singleFormFile(allow: Boolean): String = BackupCodec.encode(
        BackupFile.single(
            exportedAt = "2026-10-13T12:00:00Z",
            category = BackupCategory.FORMS,
            payload = BackupPayload(forms = listOf(BackupCodec.logOf(template(allow), emptyList()))),
        ),
    )

    @Test
    fun settingRoundTripsAndKeepsTheUuid() {
        val restored = BackupCodec.templateOf(BackupCodec.logOf(template(true), emptyList()))
        assertTrue(restored.allowAdditionsFromForm)
        assertEquals("allow-id", restored.uuid)

        val off = BackupCodec.logOf(template(false), emptyList())
        assertNull(off.allowAdditionsFromForm)
        assertFalse(BackupCodec.templateOf(off).allowAdditionsFromForm)
    }

    @Test
    fun onIsWrittenAndRestoredThroughAFile() {
        val decoded = BackupCodec.decode(singleFormFile(true))
        assertEquals(BackupFile.VERSION, decoded.version)
        assertTrue(BackupCodec.templateOf(decoded.logs.single()).allowAdditionsFromForm)
    }

    @Test
    fun offIsNeverWrittenSoUnchangedFormsEncodeAsBefore() {
        val text = singleFormFile(false)
        assertFalse(text.contains("allowAdditionsFromForm"))
        assertFalse(BackupCodec.templateOf(BackupCodec.decode(text).logs.single()).allowAdditionsFromForm)
    }

    @Test
    fun olderBackupWithoutTheSettingRestoresItOff() {
        val decoded = BackupCodec.decode(
            """
            {
              "format": "datadragon-backup",
              "version": 1,
              "exportedAt": "2026-09-03T12:00:00Z",
              "logs": [{ "id": 1, "name": "Mood", "createdAt": 100, "schemaJson": "[]" }]
            }
            """.trimIndent()
        )
        assertFalse(BackupCodec.templateOf(decoded.logs.single()).allowAdditionsFromForm)
    }

    @Test
    fun aVersionFiveFileCannotCarryTheSetting() {
        val text = singleFormFile(true).replace("\"version\": ${BackupFile.VERSION}", "\"version\": 5")
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }

    @Test
    fun aVersionFiveFileCannotDeclareTheSettingEvenAsFalse() {
        val text = singleFormFile(false)
            .replace("\"version\": ${BackupFile.VERSION}", "\"version\": 5")
            .replace("\"integrateCalendar\": false,", "\"integrateCalendar\": false,\n            \"allowAdditionsFromForm\": false,")
        assertTrue(text.contains("allowAdditionsFromForm"))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text) }
    }
}
