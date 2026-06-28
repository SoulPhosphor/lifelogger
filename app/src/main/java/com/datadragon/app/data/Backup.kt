package com.datadragon.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk shape of a backup / single-log export (docs/FORMATTING_SPEC.md §4).
 *
 * A full backup is every log (template + its entries). A single-log export is
 * the same structure with exactly one log, so it can be re-imported the same
 * way. `schemaJson` and `valuesJson` are kept verbatim as their stored strings
 * so a round-trip is lossless; timestamps stay ISO-8601 exactly as stored.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: String,
    val logs: List<BackupLog>,
) {
    companion object {
        const val FORMAT = "datadragon-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupLog(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val schemaJson: String,
    val formMarkdown: String = "",
    val entries: List<BackupEntry> = emptyList(),
)

@Serializable
data class BackupEntry(
    val id: Long,
    val createdAt: String,
    val updatedAt: String? = null,
    val valuesJson: String,
)

/** Encodes / decodes [BackupFile] and converts to and from the Room entities. */
object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    fun logOf(template: LogTemplate, entries: List<LogEntry>): BackupLog =
        BackupLog(
            id = template.id,
            name = template.name,
            createdAt = template.createdAt,
            schemaJson = template.schemaJson,
            formMarkdown = template.formMarkdown,
            entries = entries.map {
                BackupEntry(
                    id = it.id,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    valuesJson = it.valuesJson,
                )
            },
        )

    fun templateOf(log: BackupLog): LogTemplate =
        LogTemplate(
            id = log.id,
            name = log.name,
            createdAt = log.createdAt,
            schemaJson = log.schemaJson,
            formMarkdown = log.formMarkdown,
        )

    fun entriesOf(log: BackupLog): List<LogEntry> =
        log.entries.map {
            LogEntry(
                id = it.id,
                templateId = log.id,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                valuesJson = it.valuesJson,
            )
        }

    /** A one-log export built from already-loaded objects (no database read). */
    fun encodeSingleLog(template: LogTemplate, entries: List<LogEntry>, exportedAt: String): String =
        encode(BackupFile(exportedAt = exportedAt, logs = listOf(logOf(template, entries))))
}
