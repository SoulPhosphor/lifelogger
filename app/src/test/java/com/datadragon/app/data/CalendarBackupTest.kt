package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A form's configured calendars survive a backup round-trip (they ride inside the
 * form's `BackupLog`), and older backups with no `calendars` array decode to a
 * form with none.
 */
class CalendarBackupTest {

    @Test
    fun calendarsRoundTrip() {
        val template = LogTemplate(
            id = 5,
            uuid = "stable-id",
            name = "Symptoms",
            createdAt = 100,
            schemaJson = "[]",
            integrateCalendar = true,
        )
        val calendars = listOf(
            Calendar(
                id = 1,
                templateId = 5,
                position = 0,
                type = CalendarType.HEAT_MAP.token,
                label = "Odor Severity",
                description = "How strong the odor was",
                configJson = "",
            ),
            Calendar(
                id = 2,
                templateId = 5,
                position = 1,
                type = CalendarType.YES_NO.token,
                label = "Meds Taken",
                description = "",
                configJson = "",
            ),
        )

        val log = BackupCodec.logOf(template, emptyList(), emptyList(), calendars)
        // Encode + decode so we exercise the serialized form too.
        val decoded = BackupCodec.decode(
            BackupCodec.encode(BackupFile(exportedAt = "2026-09-13T00:00:00Z", logs = listOf(log)))
        )
        val restored = BackupCodec.calendarsOf(decoded.logs.single())

        assertEquals(2, restored.size)
        assertEquals(listOf("Odor Severity", "Meds Taken"), restored.map { it.label })
        assertEquals(listOf("heat_map", "yes_no"), restored.map { it.type })
        assertEquals(listOf(0, 1), restored.map { it.position })
        // Each restored calendar re-keys to the log's id.
        assertTrue(restored.all { it.templateId == 5L })
    }

    @Test
    fun olderBackupHasNoCalendars() {
        val decoded = BackupCodec.decode(
            """
            {
              "exportedAt": "2026-09-03T12:00:00Z",
              "logs": [{
                "id": 1,
                "name": "Mood",
                "createdAt": 100,
                "schemaJson": "[]"
              }]
            }
            """.trimIndent()
        )

        assertTrue(decoded.logs.single().calendars.isEmpty())
        assertTrue(BackupCodec.calendarsOf(decoded.logs.single()).isEmpty())
    }
}
