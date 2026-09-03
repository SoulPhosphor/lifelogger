package com.datadragon.app.ui

import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LogEntryOrderingTest {

    @Test
    fun automaticTimestampOrdersOldestFirst() {
        val later = entry(2, "2026-09-03T10:00:00-05:00")
        val earlier = entry(1, "2026-09-03T08:00:00-05:00")

        val sorted = sortLogEntriesChronologically(listOf(later, earlier), null)

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test
    fun selectedTimestampOrdersByUserValue() {
        val firstCreated = entry(
            id = 1,
            createdAt = "2026-09-03T08:00:00-05:00",
            values = mapOf("When" to "2026-09-05T12:00"),
        )
        val secondCreated = entry(
            id = 2,
            createdAt = "2026-09-03T10:00:00-05:00",
            values = mapOf("When" to "2026-09-04T12:00"),
        )

        val sorted = sortLogEntriesChronologically(listOf(firstCreated, secondCreated), "When")

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test
    fun missingSelectedTimestampFallsBackToAutomaticTimestamp() {
        val custom = entry(
            id = 1,
            createdAt = "2026-09-03T08:00:00-05:00",
            values = mapOf("When" to "2026-09-03T11:00"),
        )
        val fallback = entry(
            id = 2,
            createdAt = "2026-09-03T09:00:00-05:00",
        )

        val sorted = sortLogEntriesChronologically(listOf(custom, fallback), "When")

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    private fun entry(
        id: Long,
        createdAt: String,
        values: Map<String, String> = emptyMap(),
    ): LogEntry = LogEntry(
        id = id,
        templateId = 1,
        createdAt = createdAt,
        valuesJson = EntryValues.encode(values.mapValues { EntryValues.string(it.value) }),
    )
}
