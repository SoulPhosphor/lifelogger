package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTimestampSettingsTest {

    @Test
    fun timestampSettingsRoundTrip() {
        val source = LogTemplate(
            id = 7,
            uuid = "stable-id",
            name = "Mood",
            createdAt = 100,
            schemaJson = "[]",
            automaticTimestamping = true,
            sortTimestampLabel = "Entry timestamp",
        )

        val restored = BackupCodec.templateOf(BackupCodec.logOf(source, emptyList()))

        assertTrue(restored.automaticTimestamping)
        assertEquals("Entry timestamp", restored.sortTimestampLabel)
        assertEquals("stable-id", restored.uuid)
    }

    @Test
    fun olderBackupDefaultsTimestampSettingsOff() {
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

        assertFalse(decoded.logs.single().automaticTimestamping)
        assertNull(decoded.logs.single().sortTimestampLabel)
    }
}
