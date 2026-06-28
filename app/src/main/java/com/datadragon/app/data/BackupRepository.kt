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

    /** Snapshot every log and entry into a [BackupFile]. */
    suspend fun buildFull(): BackupFile {
        val templates = templateDao.getAllOnce()
        val entriesByTemplate = entryDao.getAllOnce().groupBy { it.templateId }
        val logs = templates.map { template ->
            BackupCodec.logOf(template, entriesByTemplate[template.id].orEmpty())
        }
        return BackupFile(exportedAt = now(), logs = logs)
    }

    /**
     * Replace all data with [backup]. Returns the number of logs restored.
     * Runs in one transaction so a failure leaves the database untouched.
     */
    suspend fun restore(backup: BackupFile): Int = db.withTransaction {
        entryDao.deleteAll()
        templateDao.deleteAll()
        backup.logs.forEach { log ->
            templateDao.insert(BackupCodec.templateOf(log))
            BackupCodec.entriesOf(log).forEach { entryDao.insert(it) }
        }
        backup.logs.size
    }

    companion object {
        fun now(): String = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
