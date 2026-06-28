package com.datadragon.app.data

/**
 * Phase 1 placeholder data.
 *
 * Phase 1 only proves navigation and layout, so these are in-memory stand-ins.
 * Phase 2 replaces them with real Room-backed [com.datadragon.app] entities.
 * Nothing here is persisted and none of it represents real user data.
 */
data class PlaceholderLog(
    val id: String,
    val name: String,
    val entryCount: Int,
    val lastEntryLabel: String,
)

data class PlaceholderEntry(
    val id: String,
    val timestampLabel: String,
    val summary: String,
    val notesPreview: String? = null,
)

object PlaceholderData {

    val logs: List<PlaceholderLog> = listOf(
        PlaceholderLog(
            id = "log-1",
            name = "Sample Log A",
            entryCount = 14,
            lastEntryLabel = "last entry today",
        ),
        PlaceholderLog(
            id = "log-2",
            name = "Sample Log B",
            entryCount = 3,
            lastEntryLabel = "last entry Jun 20",
        ),
    )

    fun logById(id: String?): PlaceholderLog? = logs.firstOrNull { it.id == id }

    fun entriesFor(@Suppress("UNUSED_PARAMETER") logId: String?): List<PlaceholderEntry> = listOf(
        PlaceholderEntry(
            id = "entry-1",
            timestampLabel = "Jun 27, 2:14 PM",
            summary = "Field 1 value · Field 2 value",
            notesPreview = "Notes preview text here…",
        ),
        PlaceholderEntry(
            id = "entry-2",
            timestampLabel = "Jun 26, 11:20 PM",
            summary = "Field 1 value · Field 2 value",
        ),
    )
}
