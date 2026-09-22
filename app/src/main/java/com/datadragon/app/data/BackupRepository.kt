package com.datadragon.app.data

import androidx.room.withTransaction
import java.time.LocalDate
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
    private val applyPortablePreferences: (BackupPortablePreferences) -> Unit = {},
    private val restoreFailureInjector: ((BackupCategory) -> Unit)? = null,
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

    /** Validate and compare without changing Room or portable preferences. */
    suspend fun preflight(
        backup: BackupFile,
        mode: RestoreMode,
        selectedCategories: Set<BackupCategory> = BackupCategory.entries.toSet(),
    ): RestorePreflight {
        BackupRestoreValidator.validate(backup)
        val selected = selectedCategories.intersect(backup.includedCategories.toSet())
        val conflicts = if (mode == RestoreMode.MERGE) findMergeConflicts(backup, selected) else emptyList()
        return RestorePreflight(conflicts, selected)
    }

    /**
     * Apply an already validated restore. Every Room category is changed inside
     * one transaction. Portable preferences are synchronously applied first and
     * restored to their pre-import state if the Room transaction fails.
     */
    suspend fun restore(
        backup: BackupFile,
        mode: RestoreMode,
        selectedCategories: Set<BackupCategory> = BackupCategory.entries.toSet(),
        conflictChoices: Map<String, RestoreConflictChoice> = emptyMap(),
    ): RestoreCounts {
        BackupRestoreValidator.validate(backup)
        val selected = selectedCategories.intersect(backup.includedCategories.toSet())
        val currentPreferences = portablePreferences()
        val incomingPreferences = backup.payload.portablePreferences
            ?.takeIf { mode == RestoreMode.REPLACE && BackupCategory.PORTABLE_PREFERENCES in selected }
        if (incomingPreferences != null) applyPortablePreferences(incomingPreferences)
        return try {
            db.withTransaction {
                when (mode) {
                    RestoreMode.REPLACE -> replaceSelected(backup, selected)
                    RestoreMode.MERGE -> mergeSelected(backup, selected, conflictChoices)
                }
            }
        } catch (error: Throwable) {
            if (incomingPreferences != null) runCatching { applyPortablePreferences(currentPreferences) }
            throw error
        }
    }

    /** Compatibility entry point retained for existing form/list callers and tests. */
    suspend fun restore(
        backup: BackupFile,
        mode: RestoreMode,
        forms: Boolean,
        lists: Boolean,
    ): RestoreCounts = restore(backup, mode, selectedCategories(forms, lists))

    private fun selectedCategories(forms: Boolean, lists: Boolean): Set<BackupCategory> = when {
        forms && lists -> BackupCategory.entries.toSet()
        forms -> setOf(BackupCategory.FORMS)
        lists -> setOf(BackupCategory.LISTS)
        else -> emptySet()
    }

    private suspend fun replaceSelected(
        backup: BackupFile,
        selected: Set<BackupCategory>,
    ): RestoreCounts {
        val counts = linkedMapOf<BackupCategory, RestoreCategoryCounts>()
        backup.payload.forms?.takeIf { BackupCategory.FORMS in selected }?.let {
            replaceForms(it)
            counts[BackupCategory.FORMS] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.FORMS)
        }
        backup.payload.lists?.takeIf { BackupCategory.LISTS in selected }?.let {
            replaceLists(it)
            counts[BackupCategory.LISTS] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.LISTS)
        }
        backup.payload.ideaLogs?.takeIf { BackupCategory.IDEA_LOGS in selected }?.let {
            replaceIdeas(it)
            counts[BackupCategory.IDEA_LOGS] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.IDEA_LOGS)
        }
        backup.payload.dailyTasks?.takeIf { BackupCategory.DAILY_TASKS in selected }?.let {
            replaceDailyTasks(it)
            counts[BackupCategory.DAILY_TASKS] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.DAILY_TASKS)
        }
        backup.payload.clickerData?.takeIf { BackupCategory.CLICKER_DATA in selected }?.let {
            replaceClickerData(it)
            counts[BackupCategory.CLICKER_DATA] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.CLICKER_DATA)
        }
        backup.payload.savedColorPresets?.takeIf { BackupCategory.SAVED_COLOR_PRESETS in selected }?.let {
            replaceColorPresets(it)
            counts[BackupCategory.SAVED_COLOR_PRESETS] = RestoreCategoryCounts(replaced = it.size)
            inject(BackupCategory.SAVED_COLOR_PRESETS)
        }
        if (BackupCategory.PORTABLE_PREFERENCES in selected && backup.payload.portablePreferences != null) {
            counts[BackupCategory.PORTABLE_PREFERENCES] = RestoreCategoryCounts(replaced = 1)
        }
        return RestoreCounts(counts)
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

    private suspend fun replaceIdeas(logs: List<BackupIdeaLog>) {
        ideaEntryDao.deleteAll()
        ideaLogDao.deleteAll()
        logs.forEach { insertIdeaLog(it) }
    }

    private suspend fun replaceDailyTasks(tasks: List<BackupDailyTask>) {
        dailyListDao.deleteAllItems()
        dailyListDao.deleteAllDailyLists()
        tasks.forEach { insertDailyTask(it) }
    }

    private suspend fun replaceClickerData(logs: List<BackupClickerLog>) {
        clickerDao.deleteAllCards()
        clickerDao.deleteAllLogs()
        logs.forEach { insertClickerLog(it) }
    }

    private suspend fun replaceColorPresets(presets: List<BackupColorPreset>) {
        colorPresetDao.deleteAll()
        presets.forEach { colorPresetDao.insert(ColorPreset(uuid = it.uuid, name = it.name, colorsJson = it.colorsJson)) }
    }

    /** Undo uses the same complete Replace implementation and captured category boundary. */
    suspend fun undo(snapshot: UndoSnapshot): RestoreCounts = restore(
        backup = snapshot.data,
        mode = RestoreMode.REPLACE,
        selectedCategories = snapshot.selectedCategories.toSet(),
    )

    private suspend fun mergeSelected(
        backup: BackupFile,
        selected: Set<BackupCategory>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCounts {
        val counts = linkedMapOf<BackupCategory, RestoreCategoryCounts>()
        backup.payload.forms?.takeIf { BackupCategory.FORMS in selected }?.let {
            counts[BackupCategory.FORMS] = mergeForms(it, choices)
            inject(BackupCategory.FORMS)
        }
        backup.payload.lists?.takeIf { BackupCategory.LISTS in selected }?.let {
            counts[BackupCategory.LISTS] = mergeLists(it, choices)
            inject(BackupCategory.LISTS)
        }
        backup.payload.ideaLogs?.takeIf { BackupCategory.IDEA_LOGS in selected }?.let {
            counts[BackupCategory.IDEA_LOGS] = mergeIdeas(it, choices)
            inject(BackupCategory.IDEA_LOGS)
        }
        backup.payload.dailyTasks?.takeIf { BackupCategory.DAILY_TASKS in selected }?.let {
            counts[BackupCategory.DAILY_TASKS] = mergeDailyTasks(it, choices)
            inject(BackupCategory.DAILY_TASKS)
        }
        backup.payload.clickerData?.takeIf { BackupCategory.CLICKER_DATA in selected }?.let {
            counts[BackupCategory.CLICKER_DATA] = mergeClickerData(it, choices)
            inject(BackupCategory.CLICKER_DATA)
        }
        backup.payload.savedColorPresets?.takeIf { BackupCategory.SAVED_COLOR_PRESETS in selected }?.let {
            counts[BackupCategory.SAVED_COLOR_PRESETS] = mergeColorPresets(it, choices)
            inject(BackupCategory.SAVED_COLOR_PRESETS)
        }
        if (BackupCategory.PORTABLE_PREFERENCES in selected && backup.payload.portablePreferences != null) {
            counts[BackupCategory.PORTABLE_PREFERENCES] = RestoreCategoryCounts(skipped = 1)
        }
        return RestoreCounts(counts)
    }

    private suspend fun mergeForms(
        forms: List<BackupLog>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts {
        var result = RestoreCategoryCounts()
        forms.forEach { incoming ->
            val existing = incoming.uuid.takeIf(String::isNotBlank)?.let { templateDao.getByUuid(it) }
            if (existing == null) {
                insertForm(incoming)
                result = result.copy(added = result.added + 1)
            } else {
                val current = snapshotForm(existing)
                if (semantic(current) == semantic(incoming)) {
                    result = result.copy(skipped = result.skipped + 1)
                } else {
                    val id = conflictId(BackupCategory.FORMS, incoming.uuid)
                    requireChoice(choices, id).also { choice ->
                        if (choice == RestoreConflictChoice.USE_BACKUP) {
                            deleteForm(existing)
                            insertForm(incoming)
                            result = result.copy(replaced = result.replaced + 1, conflicted = result.conflicted + 1)
                        } else {
                            result = result.copy(skipped = result.skipped + 1, conflicted = result.conflicted + 1)
                        }
                    }
                }
            }
        }
        return result
    }

    private suspend fun mergeLists(
        lists: List<BackupChecklist>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts = mergeWholeGroups(
        incoming = lists,
        uuid = { it.uuid },
        category = BackupCategory.LISTS,
        current = { uuid -> checklistDao.getChecklistByUuid(uuid)?.let { snapshotList(it) } },
        same = { a, b -> semantic(a) == semantic(b) },
        insert = ::insertList,
        delete = { value -> checklistDao.getChecklistByUuid(value.uuid)?.let { checklistDao.deleteItemsForChecklist(it.id); checklistDao.deleteChecklist(it.id) } },
        choices = choices,
    )

    private suspend fun mergeIdeas(
        logs: List<BackupIdeaLog>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts = mergeWholeGroups(
        incoming = logs,
        uuid = { it.uuid },
        category = BackupCategory.IDEA_LOGS,
        current = { uuid -> ideaLogDao.getByUuid(uuid)?.let { snapshotIdea(it) } },
        same = { a, b -> a == b },
        insert = ::insertIdeaLog,
        delete = { value -> ideaLogDao.getByUuid(value.uuid)?.let { ideaEntryDao.deleteForLog(it.id); ideaLogDao.delete(it) } },
        choices = choices,
    )

    private suspend fun mergeClickerData(
        logs: List<BackupClickerLog>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts = mergeWholeGroups(
        incoming = logs,
        uuid = { it.uuid },
        category = BackupCategory.CLICKER_DATA,
        current = { uuid -> clickerDao.getLogByUuid(uuid)?.let { snapshotClicker(it) } },
        same = { a, b -> a == b },
        insert = ::insertClickerLog,
        delete = { value -> clickerDao.getLogByUuid(value.uuid)?.let { clickerDao.deleteLogWithCards(it) } },
        choices = choices,
    )

    private suspend fun mergeColorPresets(
        presets: List<BackupColorPreset>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts {
        var result = RestoreCategoryCounts()
        presets.forEach { incoming ->
            val existing = colorPresetDao.getByUuid(incoming.uuid)
            when {
                existing == null -> {
                    colorPresetDao.insert(ColorPreset(uuid = incoming.uuid, name = incoming.name, colorsJson = incoming.colorsJson))
                    result = result.copy(added = result.added + 1)
                }
                existing.name == incoming.name && existing.colorsJson == incoming.colorsJson ->
                    result = result.copy(skipped = result.skipped + 1)
                requireChoice(choices, conflictId(BackupCategory.SAVED_COLOR_PRESETS, incoming.uuid)) ==
                    RestoreConflictChoice.USE_BACKUP -> {
                    colorPresetDao.deleteById(existing.id)
                    colorPresetDao.insert(ColorPreset(uuid = incoming.uuid, name = incoming.name, colorsJson = incoming.colorsJson))
                    result = result.copy(replaced = result.replaced + 1, conflicted = result.conflicted + 1)
                }
                else -> result = result.copy(skipped = result.skipped + 1, conflicted = result.conflicted + 1)
            }
        }
        return result
    }

    private suspend fun mergeDailyTasks(
        tasks: List<BackupDailyTask>,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts {
        var result = RestoreCategoryCounts()
        tasks.forEach { incoming ->
            val byDate = dailyListDao.getByDate(incoming.date)
            val byUuid = dailyListDao.getByUuid(incoming.uuid)
            if (byDate == null && byUuid == null) {
                val targetId = insertDailyTaskCard(incoming)
                result = result.copy(added = result.added + 1)
                result += mergeDailyItems(targetId, incoming, choices)
                return@forEach
            }
            if (byDate == null && byUuid != null) {
                val id = dailyDateConflictId(incoming)
                val choice = requireChoice(choices, id)
                if (choice == RestoreConflictChoice.USE_BACKUP) {
                    dailyListDao.setDate(byUuid.id, incoming.date)
                    result += mergeDailyItems(byUuid.id, incoming, choices)
                    result = result.copy(replaced = result.replaced + 1, conflicted = result.conflicted + 1)
                } else {
                    result = result.copy(skipped = result.skipped + 1, conflicted = result.conflicted + 1)
                }
                return@forEach
            }

            checkNotNull(byDate)
            if (byDate.uuid != incoming.uuid) {
                val id = dailyDateConflictId(incoming)
                val choice = requireChoice(choices, id)
                if (choice == RestoreConflictChoice.KEEP_CURRENT) {
                    result = result.copy(skipped = result.skipped + 1, conflicted = result.conflicted + 1)
                    return@forEach
                }
                result = result.copy(conflicted = result.conflicted + 1)
            }
            result += mergeDailyItems(byDate.id, incoming, choices)
        }
        return result
    }

    private suspend fun mergeDailyItems(
        targetCardId: Long,
        incoming: BackupDailyTask,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts {
        var counts = RestoreCategoryCounts()
        val initialTarget = dailyListDao.getItemsOnce(targetCardId).sortedBy { it.position }
        val insertionsByTop = linkedMapOf<String, MutableList<DailyListItem>>()
        val appendedSequences = mutableListOf<List<DailyListItem>>()

        suspend fun accept(item: BackupDailyTaskItem): Pair<DailyListItem?, Boolean> {
            val globalMatch = dailyListDao.getItemByUuid(item.uuid)
            if (globalMatch == null) {
                counts = counts.copy(added = counts.added + 1)
                return item.toEntity(targetCardId) to true
            }
            if (sameDailyItem(globalMatch, item) && globalMatch.dailyListId == targetCardId) {
                counts = counts.copy(skipped = counts.skipped + 1)
                return globalMatch to false
            }
            val choice = requireChoice(choices, dailyItemConflictId(incoming.date, item.uuid))
            if (choice == RestoreConflictChoice.KEEP_CURRENT) {
                counts = counts.copy(skipped = counts.skipped + 1, conflicted = counts.conflicted + 1)
                return globalMatch.takeIf { it.dailyListId == targetCardId } to false
            }
            val moved = globalMatch.dailyListId != targetCardId
            val updated = globalMatch.copy(
                dailyListId = targetCardId,
                text = item.text,
                completed = item.completed,
                indent = item.indent,
                sourceUuid = item.sourceUuid,
            )
            dailyListDao.updateItem(updated)
            counts = counts.copy(replaced = counts.replaced + 1, conflicted = counts.conflicted + 1)
            return updated to moved
        }

        val sequences = mutableListOf<MutableList<BackupDailyTaskItem>>()
        incoming.items.sortedBy { it.position }.forEach { item ->
            if (item.indent == 0) sequences += mutableListOf(item)
            else sequences.last().add(item)
        }
        sequences.forEach { sequence ->
            val (top, topIsNewToTarget) = accept(sequence.first())
            if (top == null) {
                counts = counts.copy(skipped = counts.skipped + sequence.size - 1)
                return@forEach
            }
            val acceptedSubItems = sequence.drop(1).mapNotNull { child ->
                val (accepted, isNewToTarget) = accept(child)
                accepted?.takeIf { isNewToTarget }
            }
            if (topIsNewToTarget) {
                appendedSequences += listOf(top) + acceptedSubItems
            } else if (acceptedSubItems.isNotEmpty()) {
                insertionsByTop.getOrPut(top.uuid) { mutableListOf() }.addAll(acceptedSubItems)
            }
        }

        // Preserve every current relative position. New sub-items are placed at
        // the end of their matching top-level sequence; wholly new sequences are
        // appended in incoming order.
        val refreshedTarget = dailyListDao.getItemsOnce(targetCardId).sortedBy { it.position }
        val appendedIds = appendedSequences.flatten().mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        val base = refreshedTarget.filterNot { it.id in appendedIds }
        val ordered = buildList {
            var index = 0
            while (index < base.size) {
                val top = base[index]
                add(top)
                index++
                while (index < base.size && base[index].indent == 1) add(base[index++])
                addAll(insertionsByTop[top.uuid].orEmpty())
            }
            appendedSequences.forEach(::addAll)
        }.distinctBy { it.uuid }
        ordered.forEachIndexed { index, item ->
            if (item.id == 0L) dailyListDao.insertItem(item.copy(position = index))
            else if (item.position != index) dailyListDao.setItemPosition(item.id, index)
        }
        return counts
    }

    private suspend fun <T> mergeWholeGroups(
        incoming: List<T>,
        uuid: (T) -> String,
        category: BackupCategory,
        current: suspend (String) -> T?,
        same: (T, T) -> Boolean,
        insert: suspend (T) -> Unit,
        delete: suspend (T) -> Unit,
        choices: Map<String, RestoreConflictChoice>,
    ): RestoreCategoryCounts {
        var result = RestoreCategoryCounts()
        incoming.forEach { value ->
            val identity = uuid(value)
            val existing = identity.takeIf(String::isNotBlank)?.let { current(it) }
            when {
                existing == null -> {
                    insert(value)
                    result = result.copy(added = result.added + 1)
                }
                same(existing, value) -> result = result.copy(skipped = result.skipped + 1)
                requireChoice(choices, conflictId(category, identity)) == RestoreConflictChoice.USE_BACKUP -> {
                    delete(existing)
                    insert(value)
                    result = result.copy(replaced = result.replaced + 1, conflicted = result.conflicted + 1)
                }
                else -> result = result.copy(skipped = result.skipped + 1, conflicted = result.conflicted + 1)
            }
        }
        return result
    }

    private suspend fun findMergeConflicts(
        backup: BackupFile,
        selected: Set<BackupCategory>,
    ): List<RestoreConflict> = db.withTransaction {
        buildList {
            backup.payload.forms?.takeIf { BackupCategory.FORMS in selected }?.forEach { incoming ->
                incoming.uuid.takeIf(String::isNotBlank)?.let { templateDao.getByUuid(it) }?.let { existing ->
                    val current = snapshotForm(existing)
                    if (semantic(current) != semantic(incoming)) add(groupConflict(BackupCategory.FORMS, incoming.uuid, incoming.name, current.entries.size, incoming.entries.size))
                }
            }
            backup.payload.lists?.takeIf { BackupCategory.LISTS in selected }?.forEach { incoming ->
                checklistDao.getChecklistByUuid(incoming.uuid)?.let { existing ->
                    val current = snapshotList(existing)
                    if (semantic(current) != semantic(incoming)) add(groupConflict(BackupCategory.LISTS, incoming.uuid, incoming.name, current.items.size, incoming.items.size))
                }
            }
            backup.payload.ideaLogs?.takeIf { BackupCategory.IDEA_LOGS in selected }?.forEach { incoming ->
                ideaLogDao.getByUuid(incoming.uuid)?.let { existing ->
                    val current = snapshotIdea(existing)
                    if (current != incoming) add(groupConflict(BackupCategory.IDEA_LOGS, incoming.uuid, incoming.name, current.entries.size, incoming.entries.size))
                }
            }
            backup.payload.clickerData?.takeIf { BackupCategory.CLICKER_DATA in selected }?.forEach { incoming ->
                clickerDao.getLogByUuid(incoming.uuid)?.let { existing ->
                    val current = snapshotClicker(existing)
                    if (current != incoming) add(groupConflict(BackupCategory.CLICKER_DATA, incoming.uuid, incoming.title, current.cards.size, incoming.cards.size))
                }
            }
            backup.payload.savedColorPresets?.takeIf { BackupCategory.SAVED_COLOR_PRESETS in selected }?.forEach { incoming ->
                colorPresetDao.getByUuid(incoming.uuid)?.let { existing ->
                    if (existing.name != incoming.name || existing.colorsJson != incoming.colorsJson) {
                        add(RestoreConflict(conflictId(BackupCategory.SAVED_COLOR_PRESETS, incoming.uuid), BackupCategory.SAVED_COLOR_PRESETS, incoming.name, existing.name, incoming.name, RestoreConflictKind.SAVED_COLOR_PRESET))
                    }
                }
            }
            backup.payload.dailyTasks?.takeIf { BackupCategory.DAILY_TASKS in selected }?.forEach { incoming ->
                val byDate = dailyListDao.getByDate(incoming.date)
                val byUuid = dailyListDao.getByUuid(incoming.uuid)
                if ((byDate != null && byDate.uuid != incoming.uuid) || (byDate == null && byUuid != null)) {
                    add(RestoreConflict(dailyDateConflictId(incoming), BackupCategory.DAILY_TASKS, incoming.date, byDate?.title.orEmpty(), incoming.title, RestoreConflictKind.DAILY_DATE_CARD))
                }
                val target = byDate ?: byUuid
                incoming.items.forEach { item ->
                    val existing = dailyListDao.getItemByUuid(item.uuid)
                    if (existing != null && (target == null || existing.dailyListId != target.id || !sameDailyItem(existing, item))) {
                        add(RestoreConflict(dailyItemConflictId(incoming.date, item.uuid), BackupCategory.DAILY_TASKS, item.text, existing.text, item.text, RestoreConflictKind.DAILY_ITEM))
                    }
                }
            }
        }.distinctBy { it.id }
    }

    private fun groupConflict(
        category: BackupCategory,
        uuid: String,
        title: String,
        currentChildren: Int,
        backupChildren: Int,
    ) = RestoreConflict(
        id = conflictId(category, uuid),
        category = category,
        title = title,
        currentDescription = "$currentChildren current items",
        backupDescription = "$backupChildren backup items",
        kind = RestoreConflictKind.WHOLE_GROUP,
    )

    private suspend fun snapshotForm(template: LogTemplate): BackupLog {
        val entries = entryDao.getForTemplateOnce(template.id)
        return BackupCodec.logOf(template, entries, entries.flatMap { noteDao.getForEntryOnce(it.id) }, calendarDao.getForTemplateOnce(template.id))
    }

    private suspend fun snapshotList(list: Checklist): BackupChecklist =
        BackupCodec.checklistOf(list, checklistDao.getItemsOnce(list.id))

    private suspend fun snapshotIdea(log: IdeaLog): BackupIdeaLog {
        val entries = ideaEntryDao.getAllOnce().filter { it.ideaLogId == log.id }
        return BackupIdeaLog(log.uuid, log.name, log.createdAt, log.fieldsJson, log.automaticTimestamping, log.allowArchiving, log.showEntireIdeaCard, log.previewLines, log.sortTimestampFieldId, log.sortNewestFirst, entries.map { BackupIdeaEntry(it.createdAt, it.updatedAt, it.valuesJson, it.marked, it.archived) })
    }

    private suspend fun snapshotClicker(log: ClickerLog): BackupClickerLog = BackupClickerLog(
        log.uuid, log.title, log.createdAt, log.lastAccessedAt, log.lastModifiedAt, log.fieldsJson,
        log.displayOnlyClickerDateTime, log.autoDateStamp, log.autoTimeStamp, log.allowFollowUp,
        clickerDao.getCardsForLog(log.id).map { BackupClickerCard(it.uuid, it.createdAt, it.displayDate, it.displayTime, it.valuesJson) }.sortedWith(compareBy({ it.createdAt }, { it.uuid })),
    )

    private suspend fun insertForm(log: BackupLog) {
        val templateId = templateDao.insert(BackupCodec.templateOf(log).copy(id = 0))
        log.entries.forEach { entry ->
            val entryId = entryDao.insert(LogEntry(templateId = templateId, createdAt = entry.createdAt, updatedAt = entry.updatedAt, valuesJson = entry.valuesJson, marked = entry.marked))
            entry.notes.forEach { noteDao.insert(EntryNote(entryId = entryId, createdAt = it.createdAt, text = it.text)) }
        }
        log.calendars.forEach { calendarDao.insert(Calendar(templateId = templateId, position = it.position, type = it.type, label = it.label, description = it.description, configJson = it.configJson)) }
    }

    private suspend fun deleteForm(existing: LogTemplate) {
        noteDao.deleteForTemplate(existing.id)
        entryDao.deleteForTemplate(existing.id)
        calendarDao.deleteForTemplate(existing.id)
        templateDao.delete(existing)
    }

    private suspend fun insertList(list: BackupChecklist) {
        val id = checklistDao.insertChecklist(BackupCodec.checklistEntityOf(list).copy(id = 0))
        list.items.forEach { checklistDao.insertItem(ChecklistItem(checklistId = id, text = it.text, completed = it.completed, indent = it.indent, position = it.position)) }
    }

    private suspend fun insertIdeaLog(log: BackupIdeaLog) {
        val id = ideaLogDao.insert(IdeaLog(uuid = log.uuid, name = log.name, createdAt = log.createdAt, fieldsJson = log.fieldsJson, automaticTimestamping = log.automaticTimestamping, allowArchiving = log.allowArchiving, showEntireIdeaCard = log.showEntireIdeaCard, previewLines = log.previewLines, sortTimestampFieldId = log.sortTimestampFieldId, sortNewestFirst = log.sortNewestFirst))
        log.entries.forEach { ideaEntryDao.insert(IdeaEntry(ideaLogId = id, createdAt = it.createdAt, updatedAt = it.updatedAt, valuesJson = it.valuesJson, marked = it.marked, archived = it.archived)) }
    }

    private suspend fun insertDailyTask(task: BackupDailyTask) {
        val id = insertDailyTaskCard(task)
        task.items.forEach { dailyListDao.insertItem(it.toEntity(id)) }
    }

    private suspend fun insertDailyTaskCard(task: BackupDailyTask): Long {
        val id = dailyListDao.insertDailyList(DailyList(uuid = task.uuid, date = LocalDate.parse(task.date), title = task.title, favorited = task.favorited, genuinelyCompleted = task.genuinelyCompleted, completionBlockedByCleanup = task.completionBlockedByCleanup, maintenanceRunOn = task.maintenanceRunOn?.let(LocalDate::parse), renewalRunOn = task.renewalRunOn?.let(LocalDate::parse), createdAt = task.createdAt))
        check(id != -1L) { "Daily Task date already exists." }
        return id
    }

    private suspend fun insertClickerLog(log: BackupClickerLog) {
        val id = clickerDao.insertLog(ClickerLog(uuid = log.uuid, title = log.title, createdAt = log.createdAt, lastAccessedAt = log.lastAccessedAt, lastModifiedAt = log.lastModifiedAt, fieldsJson = log.fieldsJson, displayOnlyClickerDateTime = log.displayOnlyClickerDateTime, autoDateStamp = log.autoDateStamp, autoTimeStamp = log.autoTimeStamp, allowFollowUp = log.allowFollowUp))
        log.cards.forEach { clickerDao.insertCard(ClickerCard(clickerLogId = id, uuid = it.uuid, createdAt = it.createdAt, displayDate = it.displayDate, displayTime = it.displayTime, valuesJson = it.valuesJson)) }
    }

    private fun BackupDailyTaskItem.toEntity(cardId: Long) = DailyListItem(
        dailyListId = cardId, uuid = uuid, text = text, completed = completed,
        indent = indent, position = position, sourceUuid = sourceUuid,
    )

    private fun sameDailyItem(current: DailyListItem, incoming: BackupDailyTaskItem): Boolean =
        current.text == incoming.text && current.completed == incoming.completed &&
            current.indent == incoming.indent && current.sourceUuid == incoming.sourceUuid

    private fun semantic(log: BackupLog): BackupLog = log.copy(
        id = 0,
        entries = log.entries.map { it.copy(id = 0) },
        calendars = log.calendars.map { it.copy(id = 0) },
    )

    private fun semantic(list: BackupChecklist): BackupChecklist =
        list.copy(id = 0, items = list.items.map { it.copy(id = 0) })

    private fun conflictId(category: BackupCategory, uuid: String): String = "${category.name}:$uuid"
    private fun dailyDateConflictId(task: BackupDailyTask): String = "DAILY_DATE:${task.date}:${task.uuid}"
    private fun dailyItemConflictId(date: String, uuid: String): String = "DAILY_ITEM:$date:$uuid"

    private fun requireChoice(
        choices: Map<String, RestoreConflictChoice>,
        id: String,
    ): RestoreConflictChoice = choices[id]
        ?: throw IllegalStateException("A merge conflict must be resolved before restore starts.")

    private fun inject(category: BackupCategory) {
        restoreFailureInjector?.invoke(category)
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
