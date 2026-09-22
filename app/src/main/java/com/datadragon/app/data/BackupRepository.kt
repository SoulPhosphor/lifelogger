package com.datadragon.app.data

import androidx.room.withTransaction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Builds full backups and restores them. Restore replaces all current data with
 * the backup contents in a single transaction (docs/BUILD_PHASES.md Phase 6).
 */
class BackupRepository(
    private val db: AppDatabase,
    private val portablePreferences: () -> BackupPortablePreferences = { BackupPortablePreferences() },
    private val sourceAppVersion: String = "unknown",
) {

    private val templateDao = db.logTemplateDao()
    private val entryDao = db.logEntryDao()
    private val noteDao = db.entryNoteDao()
    private val checklistDao = db.checklistDao()
    private val calendarDao = db.calendarDao()
    private val ideaLogDao = db.ideaLogDao()
    private val ideaEntryDao = db.ideaEntryDao()
    private val dailyListDao = db.dailyListDao()
    private val clickerDao = db.clickerDao()
    private val colorPresetDao = db.colorPresetDao()

    /** Build every current category from one Room read transaction. */
    suspend fun buildFull(): BackupFile = db.withTransaction {
        val entriesByTemplate = entryDao.getAllOnce().groupBy { it.templateId }
        val notesByEntry = noteDao.getAllOnce().groupBy { it.entryId }
        val calendarsByTemplate = calendarDao.getAllOnce().groupBy { it.templateId }
        val forms = templateDao.getAllOnce().map { template ->
            val entries = entriesByTemplate[template.id].orEmpty()
            BackupCodec.logOf(
                template,
                entries,
                entries.flatMap { notesByEntry[it.id].orEmpty() },
                calendarsByTemplate[template.id].orEmpty(),
            )
        }

        val checklistItems = checklistDao.getAllItemsOnce().groupBy { it.checklistId }
        val lists = checklistDao.getAllChecklistsOnce().map { checklist ->
            BackupCodec.checklistOf(checklist, checklistItems[checklist.id].orEmpty())
        }

        val ideaEntries = ideaEntryDao.getAllOnce().groupBy { it.ideaLogId }
        val ideas = ideaLogDao.getAllOnce().map { log ->
            BackupIdeaLog(
                uuid = log.uuid,
                name = log.name,
                createdAt = log.createdAt,
                fieldsJson = log.fieldsJson,
                automaticTimestamping = log.automaticTimestamping,
                allowArchiving = log.allowArchiving,
                showEntireIdeaCard = log.showEntireIdeaCard,
                previewLines = log.previewLines,
                sortTimestampFieldId = log.sortTimestampFieldId,
                sortNewestFirst = log.sortNewestFirst,
                entries = ideaEntries[log.id].orEmpty().map { entry ->
                    BackupIdeaEntry(
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                        valuesJson = entry.valuesJson,
                        marked = entry.marked,
                        archived = entry.archived,
                    )
                },
            )
        }

        val dailyItems = dailyListDao.getAllItemsOnce().groupBy { it.dailyListId }
        val dailyTasks = dailyListDao.getAllDailyListsOnce().map { task ->
            BackupDailyTask(
                uuid = task.uuid,
                date = task.date.toString(),
                title = task.title,
                favorited = task.favorited,
                genuinelyCompleted = task.genuinelyCompleted,
                completionBlockedByCleanup = task.completionBlockedByCleanup,
                maintenanceRunOn = task.maintenanceRunOn?.toString(),
                renewalRunOn = task.renewalRunOn?.toString(),
                createdAt = task.createdAt,
                items = dailyItems[task.id].orEmpty().map { item ->
                    BackupDailyTaskItem(
                        uuid = item.uuid,
                        text = item.text,
                        completed = item.completed,
                        indent = item.indent,
                        position = item.position,
                        sourceUuid = item.sourceUuid,
                    )
                },
            )
        }

        val clickerCards = clickerDao.getAllCardsOnce().groupBy { it.clickerLogId }
        val clickerData = clickerDao.getAllLogsOnce().map { log ->
            BackupClickerLog(
                uuid = log.uuid,
                title = log.title,
                createdAt = log.createdAt,
                lastAccessedAt = log.lastAccessedAt,
                lastModifiedAt = log.lastModifiedAt,
                fieldsJson = log.fieldsJson,
                displayOnlyClickerDateTime = log.displayOnlyClickerDateTime,
                autoDateStamp = log.autoDateStamp,
                autoTimeStamp = log.autoTimeStamp,
                allowFollowUp = log.allowFollowUp,
                cards = clickerCards[log.id].orEmpty().map { card ->
                    BackupClickerCard(
                        uuid = card.uuid,
                        createdAt = card.createdAt,
                        displayDate = card.displayDate,
                        displayTime = card.displayTime,
                        valuesJson = card.valuesJson,
                    )
                },
            )
        }

        val presets = colorPresetDao.getAllOnce().map { preset ->
            BackupColorPreset(preset.uuid, preset.name, preset.colorsJson)
        }

        BackupFile.full(
            exportedAt = now(),
            sourceAppVersion = sourceAppVersion,
            roomSchemaVersion = AppDatabase.SCHEMA_VERSION,
            payload = BackupPayload(
                forms = forms,
                lists = lists,
                ideaLogs = ideas,
                dailyTasks = dailyTasks,
                clickerData = clickerData,
                savedColorPresets = presets,
                portablePreferences = portablePreferences(),
            ),
        )
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
            val templateId = templateDao.insert(BackupCodec.templateOf(log).copy(id = 0))
            log.entries.forEach { entry ->
                val entryId = entryDao.insert(
                    LogEntry(
                        templateId = templateId,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt,
                        valuesJson = entry.valuesJson,
                        marked = entry.marked,
                    ),
                )
                entry.notes.forEach { note ->
                    noteDao.insert(EntryNote(entryId = entryId, createdAt = note.createdAt, text = note.text))
                }
            }
            log.calendars.forEach { calendar ->
                calendarDao.insert(
                    Calendar(
                        templateId = templateId,
                        position = calendar.position,
                        type = calendar.type,
                        label = calendar.label,
                        description = calendar.description,
                        configJson = calendar.configJson,
                    ),
                )
            }
        }
    }

    private suspend fun replaceLists(checklists: List<BackupChecklist>) {
        checklistDao.deleteAllItems()
        checklistDao.deleteAllChecklists()
        checklists.forEach { checklist ->
            val checklistId = checklistDao.insertChecklist(
                BackupCodec.checklistEntityOf(checklist).copy(id = 0),
            )
            checklist.items.forEach { item ->
                checklistDao.insertItem(
                    ChecklistItem(
                        checklistId = checklistId,
                        text = item.text,
                        completed = item.completed,
                        indent = item.indent,
                        position = item.position,
                    ),
                )
            }
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
