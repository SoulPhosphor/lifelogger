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
    private val checklistDao = db.checklistDao()
    private val calendarDao = db.calendarDao()

    /** Snapshot every log, entry, follow-up note, and list into a [BackupFile]. */
    suspend fun buildFull(): BackupFile {
        val templates = templateDao.getAllOnce()
        val entriesByTemplate = entryDao.getAllOnce().groupBy { it.templateId }
        val notesByEntry = noteDao.getAllOnce().groupBy { it.entryId }
        val calendarsByTemplate = calendarDao.getAllOnce().groupBy { it.templateId }
        val logs = templates.map { template ->
            val entries = entriesByTemplate[template.id].orEmpty()
            val notes = entries.flatMap { notesByEntry[it.id].orEmpty() }
            val calendars = calendarsByTemplate[template.id].orEmpty()
            BackupCodec.logOf(template, entries, notes, calendars)
        }
        val itemsByChecklist = checklistDao.getAllItemsOnce().groupBy { it.checklistId }
        val checklists = checklistDao.getAllChecklistsOnce().map { checklist ->
            BackupCodec.checklistOf(checklist, itemsByChecklist[checklist.id].orEmpty())
        }
        return BackupFile(exportedAt = now(), logs = logs, checklists = checklists)
    }

    /**
     * Load [backup] into the database using [mode], and report how many logs and
     * lists it contained. Runs in one transaction so a failure leaves the
     * database untouched.
     *
     * - [RestoreMode.REPLACE] wipes everything first, then loads only the backup.
     * - [RestoreMode.MERGE] matches each log/list to an existing one by its
     *   permanent uuid: a match is replaced wholesale (the incoming version wins,
     *   contents and all); anything with no match is added; anything not in the
     *   backup is left untouched. A log/list with no uuid (a version-1 file) can
     *   never match, so it always comes in as brand-new.
     */
    suspend fun restore(
        backup: BackupFile,
        mode: RestoreMode,
        forms: Boolean = true,
        lists: Boolean = true,
    ): RestoreCounts = db.withTransaction {
        when (mode) {
            RestoreMode.REPLACE -> {
                if (forms) replaceForms(backup.logs)
                if (lists) replaceLists(backup.checklists)
            }
            RestoreMode.MERGE -> merge(backup, forms, lists)
        }
        RestoreCounts(
            logs = if (forms) backup.logs.size else 0,
            lists = if (lists) backup.checklists.size else 0,
        )
    }

    private suspend fun replaceForms(logs: List<BackupLog>) {
        noteDao.deleteAll()
        entryDao.deleteAll()
        calendarDao.deleteAll()
        templateDao.deleteAll()
        logs.forEach { log ->
            templateDao.insert(BackupCodec.templateOf(log))
            BackupCodec.entriesOf(log).forEach { entryDao.insert(it) }
            BackupCodec.notesOf(log).forEach { noteDao.insert(it) }
            BackupCodec.calendarsOf(log).forEach { calendarDao.insert(it) }
        }
    }

    private suspend fun replaceLists(checklists: List<BackupChecklist>) {
        checklistDao.deleteAllItems()
        checklistDao.deleteAllChecklists()
        checklists.forEach { checklist ->
            checklistDao.insertChecklist(BackupCodec.checklistEntityOf(checklist))
            BackupCodec.itemsOf(checklist).forEach { checklistDao.insertItem(it) }
        }
    }

    /**
     * Undo Last Import: puts forms back to [snapshot]'s form state (its logs,
     * entries, and notes); lists are untouched. Runs in one transaction.
     */
    suspend fun restoreFormsFromSnapshot(snapshot: BackupFile) = db.withTransaction {
        replaceForms(snapshot.logs)
    }

    /**
     * Undo Last Import: puts lists back to [snapshot]'s list state (its
     * checklists and items); forms are untouched. Runs in one transaction.
     */
    suspend fun restoreListsFromSnapshot(snapshot: BackupFile) = db.withTransaction {
        replaceLists(snapshot.checklists)
    }

    private suspend fun merge(backup: BackupFile, forms: Boolean, lists: Boolean) {
        if (forms) backup.logs.forEach { log ->
            val existing = log.uuid.takeIf { it.isNotBlank() }?.let { templateDao.getByUuid(it) }
            if (existing != null) {
                // Same bucket: throw away everything it was, then re-create it.
                noteDao.deleteForTemplate(existing.id)
                entryDao.deleteForTemplate(existing.id)
                calendarDao.deleteForTemplate(existing.id)
                templateDao.delete(existing)
            }
            // Insert with a new local id so ids never collide with other logs;
            // its permanent uuid is preserved so it stays "the same log".
            val newTemplateId = templateDao.insert(BackupCodec.templateOf(log).copy(id = 0))
            log.entries.forEach { entry ->
                val newEntryId = entryDao.insert(
                    LogEntry(
                        templateId = newTemplateId,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                        valuesJson = entry.valuesJson,
                        marked = entry.marked,
                    ),
                )
                entry.notes.forEach { note ->
                    noteDao.insert(EntryNote(entryId = newEntryId, createdAt = note.createdAt, text = note.text))
                }
            }
            // Calendars re-key to the new template id, mirroring entries above.
            log.calendars.forEach { calendar ->
                calendarDao.insert(
                    Calendar(
                        templateId = newTemplateId,
                        position = calendar.position,
                        type = calendar.type,
                        label = calendar.label,
                        description = calendar.description,
                        configJson = calendar.configJson,
                    ),
                )
            }
        }
        if (lists) backup.checklists.forEach { checklist ->
            val existing = checklist.uuid.takeIf { it.isNotBlank() }
                ?.let { checklistDao.getChecklistByUuid(it) }
            if (existing != null) {
                checklistDao.deleteItemsForChecklist(existing.id)
                checklistDao.deleteChecklist(existing.id)
            }
            val newId = checklistDao.insertChecklist(BackupCodec.checklistEntityOf(checklist).copy(id = 0))
            checklist.items.forEach { item ->
                checklistDao.insertItem(
                    ChecklistItem(
                        checklistId = newId,
                        text = item.text,
                        completed = item.completed,
                        indent = item.indent,
                        position = item.position,
                    ),
                )
            }
        }
    }

    companion object {
        fun now(): String = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

/** How a restore applies a backup to the existing data. */
enum class RestoreMode {
    /** Wipe everything first, then load only the backup. */
    REPLACE,

    /** Add new logs/lists, replace matches by uuid, leave everything else. */
    MERGE,
}

/** How many logs and lists a restore loaded from the backup. */
data class RestoreCounts(val logs: Int, val lists: Int)
