package com.datadragon.app.data

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Categories are ordered here once so manifests and canonical payloads remain stable. */
@Serializable
enum class BackupCategory {
    @SerialName("forms") FORMS,
    @SerialName("lists") LISTS,
    @SerialName("idea_logs") IDEA_LOGS,
    @SerialName("daily_tasks") DAILY_TASKS,
    @SerialName("clicker_data") CLICKER_DATA,
    @SerialName("saved_color_presets") SAVED_COLOR_PRESETS,
    @SerialName("portable_preferences") PORTABLE_PREFERENCES,
}

/**
 * Current in-memory backup representation. Nullable payload properties are intentional:
 * null means the category was absent; an empty list means it was included and empty.
 * [version] records the source file version after legacy conversion.
 */
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val backupId: String? = null,
    val exportedAt: String,
    val sourceAppVersion: String? = null,
    val roomSchemaVersion: Int? = null,
    val includedCategories: List<BackupCategory>,
    val payload: BackupPayload,
    val counts: BackupCounts = BackupCounts.from(payload),
    val checksum: String? = null,
) {
    val logs: List<BackupLog> get() = payload.forms.orEmpty()
    val checklists: List<BackupChecklist> get() = payload.lists.orEmpty()

    companion object {
        const val FORMAT = "datadragon-backup"
        const val VERSION = 3

        fun full(
            exportedAt: String,
            sourceAppVersion: String,
            roomSchemaVersion: Int,
            payload: BackupPayload,
            backupId: String = StableUuid.createNew(),
        ): BackupFile = BackupFile(
            backupId = backupId,
            exportedAt = exportedAt,
            sourceAppVersion = sourceAppVersion,
            roomSchemaVersion = roomSchemaVersion,
            includedCategories = BackupCategory.entries,
            payload = payload,
        )

        fun single(
            exportedAt: String,
            category: BackupCategory,
            payload: BackupPayload,
            sourceAppVersion: String = "unknown",
            roomSchemaVersion: Int = AppDatabase.SCHEMA_VERSION,
        ): BackupFile = BackupFile(
            backupId = StableUuid.createNew(),
            exportedAt = exportedAt,
            sourceAppVersion = sourceAppVersion,
            roomSchemaVersion = roomSchemaVersion,
            includedCategories = listOf(category),
            payload = payload,
        )
    }
}

@Serializable
data class BackupPayload(
    val forms: List<BackupLog>? = null,
    val lists: List<BackupChecklist>? = null,
    val ideaLogs: List<BackupIdeaLog>? = null,
    val dailyTasks: List<BackupDailyTask>? = null,
    val clickerData: List<BackupClickerLog>? = null,
    val savedColorPresets: List<BackupColorPreset>? = null,
    val portablePreferences: BackupPortablePreferences? = null,
)

@Serializable
data class BackupCounts(
    val forms: Int = 0,
    val formEntries: Int = 0,
    val followUpNotes: Int = 0,
    val formCalendars: Int = 0,
    val lists: Int = 0,
    val listItems: Int = 0,
    val listDrafts: Int = 0,
    val ideaLogs: Int = 0,
    val ideaEntries: Int = 0,
    val dailyTasks: Int = 0,
    val dailyTaskItems: Int = 0,
    val clickerLogs: Int = 0,
    val clickerCards: Int = 0,
    val savedColorPresets: Int = 0,
    val portablePreferences: Int = 0,
) {
    companion object {
        fun from(payload: BackupPayload): BackupCounts = BackupCounts(
            forms = payload.forms?.size ?: 0,
            formEntries = payload.forms?.sumOf { it.entries.size } ?: 0,
            followUpNotes = payload.forms?.sumOf { form -> form.entries.sumOf { it.notes.size } } ?: 0,
            formCalendars = payload.forms?.sumOf { it.calendars.size } ?: 0,
            lists = payload.lists?.size ?: 0,
            listItems = payload.lists?.sumOf { it.items.size } ?: 0,
            listDrafts = payload.lists?.count { it.draft } ?: 0,
            ideaLogs = payload.ideaLogs?.size ?: 0,
            ideaEntries = payload.ideaLogs?.sumOf { it.entries.size } ?: 0,
            dailyTasks = payload.dailyTasks?.size ?: 0,
            dailyTaskItems = payload.dailyTasks?.sumOf { it.items.size } ?: 0,
            clickerLogs = payload.clickerData?.size ?: 0,
            clickerCards = payload.clickerData?.sumOf { it.cards.size } ?: 0,
            savedColorPresets = payload.savedColorPresets?.size ?: 0,
            portablePreferences = if (payload.portablePreferences == null) 0 else 26,
        )
    }
}

@Serializable
data class BackupLog(
    @Transient val id: Long = 0,
    val uuid: String = "",
    val name: String,
    val createdAt: Long,
    val schemaJson: String,
    val formMarkdown: String = "",
    val locked: Boolean = true,
    val allowAppendedNotes: Boolean = false,
    val automaticTimestamping: Boolean = false,
    val sortTimestampLabel: String? = null,
    val sortNewestFirst: Boolean = true,
    val integrateCalendar: Boolean = false,
    val entries: List<BackupEntry> = emptyList(),
    val calendars: List<BackupCalendar> = emptyList(),
)

@Serializable
data class BackupCalendar(
    @Transient val id: Long = 0,
    val position: Int = 0,
    val type: String,
    val label: String,
    val description: String = "",
    val configJson: String = "",
)

@Serializable
data class BackupChecklist(
    @Transient val id: Long = 0,
    val uuid: String = "",
    val name: String,
    val createdAt: Long,
    val draft: Boolean = false,
    val items: List<BackupChecklistItem> = emptyList(),
)

@Serializable
data class BackupChecklistItem(
    @Transient val id: Long = 0,
    val text: String = "",
    val completed: Boolean = false,
    val indent: Int = 0,
    val position: Int,
)

@Serializable
data class BackupEntry(
    @Transient val id: Long = 0,
    val createdAt: String,
    val updatedAt: String? = null,
    val valuesJson: String,
    val marked: Boolean = false,
    val notes: List<BackupNote> = emptyList(),
)

@Serializable
data class BackupNote(val createdAt: String, val text: String)

@Serializable
data class BackupIdeaLog(
    val uuid: String,
    val name: String,
    val createdAt: Long,
    val fieldsJson: String,
    val automaticTimestamping: Boolean,
    val allowArchiving: Boolean,
    val showEntireIdeaCard: Boolean,
    val previewLines: Int,
    val sortTimestampFieldId: String?,
    val sortNewestFirst: Boolean,
    val entries: List<BackupIdeaEntry>,
)

@Serializable
data class BackupIdeaEntry(
    val createdAt: String,
    val updatedAt: String?,
    val valuesJson: String,
    val marked: Boolean,
    val archived: Boolean,
)

@Serializable
data class BackupDailyTask(
    val uuid: String,
    val date: String,
    val title: String,
    val favorited: Boolean,
    val genuinelyCompleted: Boolean,
    val completionBlockedByCleanup: Boolean,
    val maintenanceRunOn: String?,
    val renewalRunOn: String?,
    val createdAt: Long,
    val items: List<BackupDailyTaskItem>,
)

@Serializable
data class BackupDailyTaskItem(
    val uuid: String,
    val text: String,
    val completed: Boolean,
    val indent: Int,
    val position: Int,
    val sourceUuid: String?,
)

@Serializable
data class BackupClickerLog(
    val uuid: String,
    val title: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val lastModifiedAt: Long,
    val fieldsJson: String,
    val displayOnlyClickerDateTime: Boolean,
    val autoDateStamp: Boolean,
    val autoTimeStamp: Boolean,
    val allowFollowUp: Boolean,
    val cards: List<BackupClickerCard>,
)

@Serializable
data class BackupClickerCard(
    val uuid: String,
    val createdAt: Long,
    val displayDate: String?,
    val displayTime: String?,
    val valuesJson: String,
)

@Serializable
data class BackupColorPreset(val uuid: String, val name: String, val colorsJson: String)

/** Explicit allowlist of the 26 portable preferences that currently exist. */
@Serializable
data class BackupPortablePreferences(
    val autoCapitalizeLabels: Boolean = true,
    val autoCapitalizeOptions: Boolean = true,
    val lastHomeView: String = HomeView.FORMS.key,
    val navStyle: String = NavStyle.ICONS.key,
    val navUseModeLabel: Boolean = true,
    val modeEnabledForms: Boolean = true,
    val modeEnabledLists: Boolean = true,
    val modeEnabledIdeas: Boolean = true,
    val modeEnabledDailyList: Boolean = true,
    val modeEnabledClicker: Boolean = true,
    val listCompleteIcon: String = CompleteIcon.CHECKED_BOX.key,
    val listCrossOutCompleted: Boolean = false,
    val listMoveCompletedBottom: Boolean = false,
    val dailyListHeading: String = "",
    val dailyListAutoRenew: Boolean = false,
    val dailyListShowCompleted: Boolean = true,
    val dailyListShowCurrentUnfinished: Boolean = true,
    val dailyListShowPastUnfinished: Boolean = false,
    val dailyListAutoTrashPast: Boolean = false,
    val dailyListAutoTrashKeep: Int = 7,
    val dailyListAutoReopen: Boolean = false,
    val dailyListAllowTitle: Boolean = false,
    val dailyListCelebrationEnabled: Boolean = true,
    val dailyListCelebrationIcon: String = CelebrationIcon.CHECK_CIRCLE.key,
    val dailyListProtectFavorited: Boolean = true,
    val dailyListRetention: String = "",
)

data class UndoSnapshot(
    val capturedAt: String,
    val data: BackupFile,
    val selectedCategories: List<BackupCategory> = data.includedCategories,
)

@Serializable
private data class BackupEnvelopeV3(
    val format: String,
    val version: Int,
    val backupId: String,
    val createdAt: String,
    val sourceAppVersion: String,
    val roomSchemaVersion: Int,
    val includedCategories: List<BackupCategory>,
    val payload: BackupPayload,
    val counts: BackupCounts,
    val checksum: String,
)

@Serializable
private data class UndoEnvelopeV3(
    val capturedAt: String,
    val selectedCategories: List<BackupCategory> = BackupCategory.entries,
    val data: BackupEnvelopeV3,
)

@Serializable
private data class LegacyBackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val logs: List<BackupLog>,
    val checklists: List<BackupChecklist> = emptyList(),
)

/** Version-aware deterministic encoder, legacy converter, and integrity verifier. */
object BackupCodec {
    private val diskJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val canonicalJson = Json {
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String =
        diskJson.encodeToString(BackupEnvelopeV3.serializer(), currentEnvelope(backup))

    fun decode(text: String): BackupFile {
        val root = runCatching { diskJson.parseToJsonElement(text) as? JsonObject }
            .getOrNull() ?: throw IllegalArgumentException("This file isn't valid backup JSON.")
        val format = root["format"]?.jsonPrimitive?.content
        require(format == BackupFile.FORMAT) { "This file is not a Data Dragon backup." }
        val version = root["version"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("This backup has no supported format version.")
        require(version in 1..BackupFile.VERSION) {
            if (version > BackupFile.VERSION) {
                "This backup was created by a newer unsupported version of Data Dragon."
            } else {
                "This backup version is not supported."
            }
        }
        return if (version == BackupFile.VERSION) decodeCurrent(text) else decodeLegacy(text, version)
    }

    fun encodeSnapshot(snapshot: UndoSnapshot): String = diskJson.encodeToString(
        UndoEnvelopeV3.serializer(),
        UndoEnvelopeV3(
            capturedAt = snapshot.capturedAt,
            selectedCategories = snapshot.selectedCategories.distinct().sortedBy { it.ordinal },
            data = currentEnvelope(snapshot.data),
        ),
    )

    fun decodeSnapshot(text: String): UndoSnapshot {
        val disk = diskJson.decodeFromString(UndoEnvelopeV3.serializer(), text)
        val data = validateAndConvert(disk.data)
        require(disk.selectedCategories.size == disk.selectedCategories.distinct().size) {
            "Undo category boundary contains duplicates."
        }
        require(disk.selectedCategories.all { it in data.includedCategories }) {
            "Undo category boundary is not present in its snapshot."
        }
        return UndoSnapshot(disk.capturedAt, data, disk.selectedCategories.sortedBy { it.ordinal })
    }

    private fun decodeCurrent(text: String): BackupFile =
        validateAndConvert(diskJson.decodeFromString(BackupEnvelopeV3.serializer(), text))

    private fun decodeLegacy(text: String, declaredVersion: Int): BackupFile {
        val legacy = diskJson.decodeFromString(LegacyBackupFile.serializer(), text)
        require(legacy.version == declaredVersion) { "Backup version changed while decoding." }
        val payload = BackupPayload(
            forms = canonicalForms(legacy.logs),
            lists = if (declaredVersion >= 2) canonicalLists(legacy.checklists) else null,
        )
        val categories = if (declaredVersion == 1) {
            listOf(BackupCategory.FORMS)
        } else {
            listOf(BackupCategory.FORMS, BackupCategory.LISTS)
        }
        return BackupFile(
            version = declaredVersion,
            exportedAt = legacy.exportedAt,
            includedCategories = categories,
            payload = payload,
            counts = BackupCounts.from(payload),
            checksum = checksum(payload),
        )
    }

    private fun currentEnvelope(backup: BackupFile): BackupEnvelopeV3 {
        require(backup.format == BackupFile.FORMAT) { "This file is not a Data Dragon backup." }
        val payload = canonicalPayload(backup.payload)
        validateManifest(backup.includedCategories, payload)
        return BackupEnvelopeV3(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            backupId = backup.backupId ?: StableUuid.createNew(),
            createdAt = backup.exportedAt,
            sourceAppVersion = backup.sourceAppVersion ?: "legacy-import",
            roomSchemaVersion = backup.roomSchemaVersion ?: AppDatabase.SCHEMA_VERSION,
            includedCategories = backup.includedCategories.distinct().sortedBy { it.ordinal },
            payload = payload,
            counts = BackupCounts.from(payload),
            checksum = checksum(payload),
        )
    }

    private fun validateAndConvert(envelope: BackupEnvelopeV3): BackupFile {
        require(envelope.format == BackupFile.FORMAT) { "This file is not a Data Dragon backup." }
        require(envelope.version == BackupFile.VERSION) { "This backup version is not supported." }
        require(envelope.backupId.isNotBlank()) { "Backup ID is missing." }
        validateManifest(envelope.includedCategories, envelope.payload)
        val canonical = canonicalPayload(envelope.payload)
        require(envelope.counts == BackupCounts.from(canonical)) { "Backup category counts do not match its payload." }
        require(envelope.checksum == checksum(canonical)) { "Backup checksum does not match its payload." }
        return BackupFile(
            version = envelope.version,
            backupId = envelope.backupId,
            exportedAt = envelope.createdAt,
            sourceAppVersion = envelope.sourceAppVersion,
            roomSchemaVersion = envelope.roomSchemaVersion,
            includedCategories = envelope.includedCategories.sortedBy { it.ordinal },
            payload = canonical,
            counts = envelope.counts,
            checksum = envelope.checksum,
        )
    }

    private fun validateManifest(categories: List<BackupCategory>, payload: BackupPayload) {
        require(categories.size == categories.distinct().size) { "Backup category manifest contains duplicates." }
        val declared = categories.toSet()
        val present = buildSet {
            if (payload.forms != null) add(BackupCategory.FORMS)
            if (payload.lists != null) add(BackupCategory.LISTS)
            if (payload.ideaLogs != null) add(BackupCategory.IDEA_LOGS)
            if (payload.dailyTasks != null) add(BackupCategory.DAILY_TASKS)
            if (payload.clickerData != null) add(BackupCategory.CLICKER_DATA)
            if (payload.savedColorPresets != null) add(BackupCategory.SAVED_COLOR_PRESETS)
            if (payload.portablePreferences != null) add(BackupCategory.PORTABLE_PREFERENCES)
        }
        require(declared == present) { "Backup category manifest does not match its payload." }
    }

    private fun checksum(payload: BackupPayload): String {
        val bytes = canonicalJson.encodeToString(BackupPayload.serializer(), canonicalPayload(payload)).toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun canonicalPayload(payload: BackupPayload): BackupPayload = payload.copy(
        forms = payload.forms?.let(::canonicalForms),
        lists = payload.lists?.let(::canonicalLists),
        ideaLogs = payload.ideaLogs?.map { log ->
            log.copy(entries = log.entries.sortedWith(compareBy({ it.createdAt }, { it.updatedAt }, { it.valuesJson })))
        }?.sortedWith(compareBy({ it.uuid }, { it.createdAt }, { it.name })),
        dailyTasks = payload.dailyTasks?.map { task ->
            task.copy(items = task.items.sortedWith(compareBy({ it.position }, { it.uuid })))
        }?.sortedWith(compareBy({ it.date }, { it.uuid })),
        clickerData = payload.clickerData?.map { log ->
            log.copy(cards = log.cards.sortedWith(compareBy({ it.createdAt }, { it.uuid })))
        }?.sortedWith(compareBy({ it.uuid }, { it.createdAt }, { it.title })),
        savedColorPresets = payload.savedColorPresets?.sortedWith(compareBy({ it.uuid }, { it.name })),
    )

    private fun canonicalForms(forms: List<BackupLog>): List<BackupLog> = forms.map { form ->
        form.copy(
            entries = form.entries.map { entry ->
                entry.copy(notes = entry.notes.sortedWith(compareBy({ it.createdAt }, { it.text })))
            }.sortedWith(compareBy({ it.createdAt }, { it.updatedAt }, { it.valuesJson })),
            calendars = form.calendars.sortedWith(compareBy({ it.position }, { it.type }, { it.label })),
        )
    }.sortedWith(compareBy({ it.uuid }, { it.createdAt }, { it.name }))

    private fun canonicalLists(lists: List<BackupChecklist>): List<BackupChecklist> = lists.map { list ->
        list.copy(items = list.items.sortedWith(compareBy({ it.position }, { it.text })))
    }.sortedWith(compareBy({ it.uuid }, { it.createdAt }, { it.name }))

    fun logOf(
        template: LogTemplate,
        entries: List<LogEntry>,
        notes: List<EntryNote> = emptyList(),
        calendars: List<Calendar> = emptyList(),
    ): BackupLog {
        val notesByEntry = notes.groupBy { it.entryId }
        return BackupLog(
            id = template.id,
            uuid = template.uuid,
            name = template.name,
            createdAt = template.createdAt,
            schemaJson = template.schemaJson,
            formMarkdown = template.formMarkdown,
            locked = template.locked,
            allowAppendedNotes = template.allowAppendedNotes,
            automaticTimestamping = template.automaticTimestamping,
            sortTimestampLabel = template.sortTimestampLabel,
            sortNewestFirst = template.sortNewestFirst,
            integrateCalendar = template.integrateCalendar,
            entries = entries.map { entry ->
                BackupEntry(
                    id = entry.id,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt,
                    valuesJson = entry.valuesJson,
                    marked = entry.marked,
                    notes = notesByEntry[entry.id].orEmpty().map {
                        BackupNote(createdAt = it.createdAt, text = it.text)
                    },
                )
            },
            calendars = calendars.map { calendar ->
                BackupCalendar(
                    id = calendar.id,
                    position = calendar.position,
                    type = calendar.type,
                    label = calendar.label,
                    description = calendar.description,
                    configJson = calendar.configJson,
                )
            },
        )
    }

    fun templateOf(log: BackupLog): LogTemplate = LogTemplate(
        id = log.id,
        uuid = restoredOrLegacyUuid(log.uuid),
        name = log.name,
        createdAt = log.createdAt,
        schemaJson = log.schemaJson,
        formMarkdown = log.formMarkdown,
        locked = log.locked,
        allowAppendedNotes = log.allowAppendedNotes,
        automaticTimestamping = log.automaticTimestamping,
        sortTimestampLabel = log.sortTimestampLabel,
        sortNewestFirst = log.sortNewestFirst,
        integrateCalendar = log.integrateCalendar,
    )

    fun entriesOf(log: BackupLog): List<LogEntry> = log.entries.map {
        LogEntry(
            id = it.id,
            templateId = log.id,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            valuesJson = it.valuesJson,
            marked = it.marked,
        )
    }

    fun notesOf(log: BackupLog): List<EntryNote> = log.entries.flatMap { entry ->
        entry.notes.map { EntryNote(entryId = entry.id, createdAt = it.createdAt, text = it.text) }
    }

    fun calendarsOf(log: BackupLog): List<Calendar> = log.calendars.map {
        Calendar(
            id = it.id,
            templateId = log.id,
            position = it.position,
            type = it.type,
            label = it.label,
            description = it.description,
            configJson = it.configJson,
        )
    }

    fun checklistOf(checklist: Checklist, items: List<ChecklistItem>): BackupChecklist = BackupChecklist(
        id = checklist.id,
        uuid = checklist.uuid,
        name = checklist.name,
        createdAt = checklist.createdAt,
        draft = checklist.draft,
        items = items.map {
            BackupChecklistItem(
                id = it.id,
                text = it.text,
                completed = it.completed,
                indent = it.indent,
                position = it.position,
            )
        },
    )

    fun checklistEntityOf(checklist: BackupChecklist): Checklist = Checklist(
        id = checklist.id,
        uuid = restoredOrLegacyUuid(checklist.uuid),
        name = checklist.name,
        createdAt = checklist.createdAt,
        draft = checklist.draft,
    )

    fun itemsOf(checklist: BackupChecklist): List<ChecklistItem> = checklist.items.map {
        ChecklistItem(
            id = it.id,
            checklistId = checklist.id,
            text = it.text,
            completed = it.completed,
            indent = it.indent,
            position = it.position,
        )
    }

    fun encodeSingleLog(
        template: LogTemplate,
        entries: List<LogEntry>,
        exportedAt: String,
        notes: List<EntryNote> = emptyList(),
    ): String = encode(
        BackupFile.single(
            exportedAt,
            BackupCategory.FORMS,
            BackupPayload(forms = listOf(logOf(template, entries, notes))),
        ),
    )

    fun encodeSingleChecklist(
        checklist: Checklist,
        items: List<ChecklistItem>,
        exportedAt: String,
    ): String = encode(
        BackupFile.single(
            exportedAt,
            BackupCategory.LISTS,
            BackupPayload(lists = listOf(checklistOf(checklist, items))),
        ),
    )

    private fun restoredOrLegacyUuid(uuid: String): String =
        if (uuid.isBlank()) StableUuid.createNew() else StableUuid.restoreExisting(uuid)
}
