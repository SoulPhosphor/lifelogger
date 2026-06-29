package com.datadragon.app.data

import androidx.room.withTransaction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Builds full backups and restores them. Restore replaces all current data with
 * the backup contents in a single transaction (docs/BUILD_PHASES.md Phase 6).
 */
class BackupRepository(private val db: AppDatabase) {

    private val templateDao = db.logTemplateDao()
    private val entryDao = db.logEntryDao()
    private val noteDao = db.entryNoteDao()

    /** Snapshot every log, entry, and follow-up note into a [BackupFile]. */
    suspend fun buildFull(): BackupFile {
        val templates = templateDao.getAllOnce()
        val entriesByTemplate = entryDao.getAllOnce().groupBy { it.templateId }
        val notesByEntry = noteDao.getAllOnce().groupBy { it.entryId }
        val logs = templates.map { template ->
            val entries = entriesByTemplate[template.id].orEmpty()
            val notes = entries.flatMap { notesByEntry[it.id].orEmpty() }
            BackupCodec.logOf(template, entries, notes)
        }
        return BackupFile(exportedAt = now(), logs = logs)
    }

    /**
     * Replace all data with [backup]. Returns the number of logs restored.
     * Runs in one transaction so a failure leaves the database untouched.
     */
    suspend fun restore(backup: BackupFile): Int = db.withTransaction {
        noteDao.deleteAll()
        entryDao.deleteAll()
        templateDao.deleteAll()
        backup.logs.forEach { log ->
            templateDao.insert(BackupCodec.templateOf(log))
            BackupCodec.entriesOf(log).forEach { entryDao.insert(it) }
            BackupCodec.notesOf(log).forEach { noteDao.insert(it) }
        }
        backup.logs.size
    }

    companion object {
        fun now(): String = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
