package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupPhase3RestoreTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceRestoresEveryCategoryRemapsRelationshipsAndAppliesPreferences() = runBlocking {
        var preferences = BackupPortablePreferences()
        val incomingPreferences = preferences.copy(autoCapitalizeLabels = false, lastHomeView = HomeView.CLICKER.key)
        val repository = BackupRepository(db, { preferences }, "test", { preferences = it })
        val backup = completeBackup(incomingPreferences)

        val counts = repository.restore(backup, RestoreMode.REPLACE)
        val restored = repository.buildFull()

        assertEquals(1, counts.categories[BackupCategory.FORMS]?.replaced)
        assertEquals("form-uuid", restored.payload.forms!!.single().uuid)
        assertEquals("list-uuid", restored.payload.lists!!.single().uuid)
        assertEquals("idea-uuid", restored.payload.ideaLogs!!.single().uuid)
        assertEquals("daily-card-uuid", restored.payload.dailyTasks!!.single().uuid)
        assertEquals("daily-item-uuid", restored.payload.dailyTasks!!.single().items.single().uuid)
        assertEquals("clicker-uuid", restored.payload.clickerData!!.single().uuid)
        assertEquals("clicker-card-uuid", restored.payload.clickerData!!.single().cards.single().uuid)
        assertEquals("preset-uuid", restored.payload.savedColorPresets!!.single().uuid)
        assertEquals(incomingPreferences, preferences)

        val form = db.logTemplateDao().getByUuid("form-uuid")!!
        val entry = db.logEntryDao().getForTemplateOnce(form.id).single()
        assertEquals(form.id, entry.templateId)
        assertNotEquals(999L, form.id)
        val list = db.checklistDao().getChecklistByUuid("list-uuid")!!
        assertEquals(list.id, db.checklistDao().getItemsOnce(list.id).single().checklistId)
    }

    @Test
    fun presentEmptyClearsOnlyThatCategoryAndLegacyAbsenceLeavesNewerCategoriesUntouched() = runBlocking {
        val repository = BackupRepository(db)
        db.logTemplateDao().insert(LogTemplate(uuid = "current-form", name = "Current", createdAt = 1, schemaJson = "[]"))
        db.checklistDao().insertChecklist(Checklist(uuid = "current-list", name = "Current List", createdAt = 1))

        val emptyLists = backup(BackupPayload(lists = emptyList()))
        repository.restore(emptyLists, RestoreMode.REPLACE)
        assertEquals(1, db.logTemplateDao().getAllOnce().size)
        assertTrue(db.checklistDao().getAllChecklistsOnce().isEmpty())

        db.checklistDao().insertChecklist(Checklist(uuid = "newer-list", name = "Newer", createdAt = 2))
        val legacy = BackupCodec.decode(javaClass.classLoader!!.getResource("fixtures/backup-v1.json")!!.readText())
        repository.restore(legacy, RestoreMode.REPLACE)
        assertEquals("Newer", db.checklistDao().getAllChecklistsOnce().single().name)

        db.ideaLogDao().insert(IdeaLog(uuid = "newer-idea", name = "Newer Idea", createdAt = 3, fieldsJson = "[]"))
        val legacyV2 = BackupCodec.decode(javaClass.classLoader!!.getResource("fixtures/backup-v2.json")!!.readText())
        repository.restore(legacyV2, RestoreMode.REPLACE)
        assertEquals("Newer Idea", db.ideaLogDao().getAllOnce().single().name)
    }

    @Test
    fun mergeUsesUuidConflictsAndKeepsPreferences() = runBlocking {
        var preferences = BackupPortablePreferences(autoCapitalizeLabels = true)
        val repository = BackupRepository(db, { preferences }, "test", { preferences = it })
        db.logTemplateDao().insert(LogTemplate(uuid = "form-uuid", name = "Current Form", createdAt = 1, schemaJson = "[]"))
        db.checklistDao().insertChecklist(Checklist(uuid = "list-uuid", name = "Current List", createdAt = 1))
        db.ideaLogDao().insert(IdeaLog(uuid = "idea-uuid", name = "Current Ideas", createdAt = 1, fieldsJson = "[]"))
        db.clickerDao().insertLog(ClickerLog(uuid = "clicker-uuid", title = "Current Clicker", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]"))
        db.colorPresetDao().insert(ColorPreset(uuid = "preset-uuid", name = "Current Colors", colorsJson = "[\"#111111\"]"))

        val incoming = completeBackup(preferences.copy(autoCapitalizeLabels = false))
        val preflight = repository.preflight(incoming, RestoreMode.MERGE)
        assertTrue(preflight.conflicts.map { it.category }.containsAll(setOf(
            BackupCategory.FORMS,
            BackupCategory.LISTS,
            BackupCategory.IDEA_LOGS,
            BackupCategory.CLICKER_DATA,
            BackupCategory.SAVED_COLOR_PRESETS,
        )))

        val keep = preflight.conflicts.associate { it.id to RestoreConflictChoice.KEEP_CURRENT }
        val kept = repository.restore(incoming, RestoreMode.MERGE, conflictChoices = keep)
        assertEquals("Current Form", db.logTemplateDao().getByUuid("form-uuid")!!.name)
        assertEquals("Current Colors", db.colorPresetDao().getByUuid("preset-uuid")!!.name)
        assertTrue(preferences.autoCapitalizeLabels)
        assertTrue(kept.conflicted >= 5)

        val useBackup = preflight.conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP }
        repository.restore(incoming, RestoreMode.MERGE, conflictChoices = useBackup)
        assertEquals("Backup Form", db.logTemplateDao().getByUuid("form-uuid")!!.name)
        assertEquals("Backup Colors", db.colorPresetDao().getByUuid("preset-uuid")!!.name)
        assertTrue(preferences.autoCapitalizeLabels)
    }

    @Test
    fun dailySameDateMergeKeepsCurrentCardAndSequenceAndReportsItemConflict() = runBlocking {
        val dao = db.dailyListDao()
        val cardId = dao.insertDailyList(DailyList(uuid = "current-card", date = LocalDate.parse("2026-09-22"), title = "Current", favorited = true, createdAt = 1))
        dao.insertItem(DailyListItem(dailyListId = cardId, uuid = "shared-top", text = "Current wording", position = 0))
        dao.insertItem(DailyListItem(dailyListId = cardId, uuid = "later-top", text = "Later", position = 1))
        val incoming = backup(
            BackupPayload(dailyTasks = listOf(
                BackupDailyTask(
                    uuid = "incoming-card",
                    date = "2026-09-22",
                    title = "Backup",
                    favorited = false,
                    genuinelyCompleted = false,
                    completionBlockedByCleanup = false,
                    maintenanceRunOn = null,
                    renewalRunOn = null,
                    createdAt = 2,
                    items = listOf(
                        BackupDailyTaskItem("shared-top", "Backup wording", false, 0, 0, null),
                        BackupDailyTaskItem("new-sub", "New sub-item", false, 1, 1, null),
                        BackupDailyTaskItem("distinct-top", "Later", false, 0, 2, null),
                    ),
                ),
            )),
        )
        val repository = BackupRepository(db)
        val preflight = repository.preflight(incoming, RestoreMode.MERGE)
        val choices = preflight.conflicts.associate {
            it.id to if (it.kind == RestoreConflictKind.DAILY_DATE_CARD) {
                RestoreConflictChoice.USE_BACKUP
            } else {
                RestoreConflictChoice.KEEP_CURRENT
            }
        }
        val result = repository.restore(incoming, RestoreMode.MERGE, conflictChoices = choices)

        val card = dao.getByDate("2026-09-22")!!
        assertEquals("current-card", card.uuid)
        assertEquals("Current", card.title)
        assertTrue(card.favorited)
        val items = dao.getItemsOnce(card.id)
        assertEquals(listOf("shared-top", "new-sub", "later-top", "distinct-top"), items.map { it.uuid })
        assertEquals(listOf(0, 1, 0, 0), items.map { it.indent })
        assertEquals("Current wording", items.first().text)
        assertTrue(result.conflicted >= 2)
        assertEquals(2, result.added)
    }

    @Test
    fun dailyNewDateStillPreflightsAndMovesAConflictingItemUuidOnlyWhenChosen() = runBlocking {
        val dao = db.dailyListDao()
        val oldCardId = dao.insertDailyList(DailyList(uuid = "old-card", date = LocalDate.parse("2026-09-21"), createdAt = 1))
        dao.insertItem(DailyListItem(dailyListId = oldCardId, uuid = "shared-item", text = "Current", position = 0))
        val incoming = backup(BackupPayload(dailyTasks = listOf(
            BackupDailyTask(
                uuid = "new-card",
                date = "2026-09-22",
                title = "New date",
                favorited = false,
                genuinelyCompleted = false,
                completionBlockedByCleanup = false,
                maintenanceRunOn = null,
                renewalRunOn = null,
                createdAt = 2,
                items = listOf(BackupDailyTaskItem("shared-item", "Backup", false, 0, 0, null)),
            ),
        )))
        val repository = BackupRepository(db)
        val preflight = repository.preflight(incoming, RestoreMode.MERGE)
        assertEquals(listOf(RestoreConflictKind.DAILY_ITEM), preflight.conflicts.map { it.kind })

        repository.restore(
            incoming,
            RestoreMode.MERGE,
            conflictChoices = preflight.conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP },
        )

        val newCard = dao.getByDate("2026-09-22")!!
        assertEquals("new-card", newCard.uuid)
        assertTrue(dao.getItemsOnce(oldCardId).isEmpty())
        assertEquals("shared-item", dao.getItemsOnce(newCard.id).single().uuid)
        assertEquals("Backup", dao.getItemsOnce(newCard.id).single().text)
    }

    @Test
    fun dailyDateWinsWhenIncomingCardUuidAlreadyBelongsToAnotherDate() = runBlocking {
        val dao = db.dailyListDao()
        val dateCardId = dao.insertDailyList(DailyList(uuid = "date-card", date = LocalDate.parse("2026-09-22"), title = "Current date", createdAt = 1))
        val uuidCardId = dao.insertDailyList(DailyList(uuid = "incoming-card", date = LocalDate.parse("2026-09-21"), title = "Other date", createdAt = 1))
        val incoming = backup(BackupPayload(dailyTasks = listOf(
            BackupDailyTask(
                uuid = "incoming-card",
                date = "2026-09-22",
                title = "Backup title",
                favorited = false,
                genuinelyCompleted = false,
                completionBlockedByCleanup = false,
                maintenanceRunOn = null,
                renewalRunOn = null,
                createdAt = 2,
                items = listOf(BackupDailyTaskItem("backup-item", "Backup task", false, 0, 0, null)),
            ),
        )))
        val repository = BackupRepository(db)
        val preflight = repository.preflight(incoming, RestoreMode.MERGE)
        assertEquals(listOf(RestoreConflictKind.DAILY_DATE_CARD), preflight.conflicts.map { it.kind })

        repository.restore(
            incoming,
            RestoreMode.MERGE,
            conflictChoices = preflight.conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP },
        )

        assertEquals("date-card", dao.getByDate("2026-09-22")!!.uuid)
        assertEquals("incoming-card", dao.getByDate("2026-09-21")!!.uuid)
        assertEquals("backup-item", dao.getItemsOnce(dateCardId).single().uuid)
        assertTrue(dao.getItemsOnce(uuidCardId).isEmpty())
    }

    @Test
    fun restoreFailureRollsBackEveryDatabaseCategoryAndPreservesCurrentPreferences() = runBlocking {
        var preferences = BackupPortablePreferences(autoCapitalizeLabels = true)
        db.logTemplateDao().insert(LogTemplate(uuid = "before", name = "Before", createdAt = 1, schemaJson = "[]"))
        val repository = BackupRepository(
            db = db,
            portablePreferences = { preferences },
            sourceAppVersion = "test",
            applyPortablePreferences = { preferences = it },
            restoreFailureInjector = { if (it == BackupCategory.LISTS) error("injected restore failure") },
        )

        val failed = runCatching { repository.restore(completeBackup(preferences.copy(autoCapitalizeLabels = false)), RestoreMode.REPLACE) }
        assertTrue(failed.isFailure)
        assertEquals(listOf("Before"), db.logTemplateDao().getAllOnce().map { it.name })
        assertTrue(db.checklistDao().getAllChecklistsOnce().isEmpty())
        assertTrue(preferences.autoCapitalizeLabels)
    }

    @Test
    fun verifiedUndoWriteFailureNeverReplacesTheExistingSlot() = runBlocking {
        val directory = ApplicationProvider.getApplicationContext<Context>().cacheDir.resolve("undo-${System.nanoTime()}")
        directory.mkdirs()
        val slot = File(directory, "pre_import_snapshot.json")
        val original = UndoSnapshot("2026-09-22T00:00:00Z", completeBackup())
        UndoSnapshotStore(slot).saveVerified(original)

        val failing = UndoSnapshotStore(slot) { error("injected undo verification failure") }
        val replacement = UndoSnapshot("2026-09-22T01:00:00Z", backup(BackupPayload(forms = emptyList())))
        assertTrue(runCatching { failing.saveVerified(replacement) }.isFailure)
        assertEquals(original.capturedAt, UndoSnapshotStore(slot).load()!!.capturedAt)
    }

    @Test
    fun completeUndoRestoresEverySelectedCategoryAndStableUuid() = runBlocking {
        var preferences = BackupPortablePreferences(autoCapitalizeLabels = true)
        val repository = BackupRepository(db, { preferences }, "test", { preferences = it })
        repository.restore(completeBackup(preferences), RestoreMode.REPLACE)
        val before = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        val directory = ApplicationProvider.getApplicationContext<Context>().cacheDir.resolve("complete-undo-${System.nanoTime()}")
        val store = UndoSnapshotStore(File(directory, "pre_import_snapshot.json"))
        store.saveVerified(UndoSnapshot("2026-09-22T00:00:00Z", before, BackupCategory.entries))

        repository.restore(backup(BackupPayload(
            forms = emptyList(), lists = emptyList(), ideaLogs = emptyList(), dailyTasks = emptyList(),
            clickerData = emptyList(), savedColorPresets = emptyList(),
            portablePreferences = preferences.copy(autoCapitalizeLabels = false),
        )), RestoreMode.REPLACE)
        repository.undo(store.load()!!)
        val after = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        assertEquals(before.payload, after.payload)
        assertEquals("form-uuid", after.payload.forms!!.single().uuid)
        assertEquals("daily-item-uuid", after.payload.dailyTasks!!.single().items.single().uuid)
    }

    private fun completeBackup(
        preferences: BackupPortablePreferences = BackupPortablePreferences(),
    ): BackupFile = backup(
        BackupPayload(
            forms = listOf(
                BackupLog(
                    id = 999,
                    uuid = "form-uuid",
                    name = "Backup Form",
                    createdAt = 1,
                    schemaJson = "[]",
                    entries = listOf(BackupEntry(id = 999, createdAt = "2026-09-22T00:00:00Z", valuesJson = "{}", notes = listOf(BackupNote("2026-09-22T00:01:00Z", "Note")))),
                    calendars = listOf(BackupCalendar(id = 999, position = 0, type = "heat_map", label = "Calendar", configJson = "")),
                ),
            ),
            lists = listOf(BackupChecklist(id = 999, uuid = "list-uuid", name = "Backup List", createdAt = 1, draft = true, items = listOf(BackupChecklistItem(id = 999, text = "List item", position = 0)))),
            ideaLogs = listOf(BackupIdeaLog("idea-uuid", "Backup Ideas", 1, "[]", false, false, false, 20, null, true, listOf(BackupIdeaEntry("2026-09-22T00:00:00Z", null, "{}", false, false)))),
            dailyTasks = listOf(BackupDailyTask("daily-card-uuid", "2026-09-22", "Backup Day", false, false, false, null, null, 1, listOf(BackupDailyTaskItem("daily-item-uuid", "Task", false, 0, 0, null)))),
            clickerData = listOf(BackupClickerLog("clicker-uuid", "Backup Clicker", 1, 2, 3, "[]", false, true, false, false, listOf(BackupClickerCard("clicker-card-uuid", 2, "2026-09-22", null, "{}")))),
            savedColorPresets = listOf(BackupColorPreset("preset-uuid", "Backup Colors", "[\"#123456\"]")),
            portablePreferences = preferences,
        ),
    )

    private fun backup(payload: BackupPayload): BackupFile {
        val categories = buildList {
            if (payload.forms != null) add(BackupCategory.FORMS)
            if (payload.lists != null) add(BackupCategory.LISTS)
            if (payload.ideaLogs != null) add(BackupCategory.IDEA_LOGS)
            if (payload.dailyTasks != null) add(BackupCategory.DAILY_TASKS)
            if (payload.clickerData != null) add(BackupCategory.CLICKER_DATA)
            if (payload.savedColorPresets != null) add(BackupCategory.SAVED_COLOR_PRESETS)
            if (payload.portablePreferences != null) add(BackupCategory.PORTABLE_PREFERENCES)
        }
        return BackupCodec.decode(BackupCodec.encode(BackupFile(
            backupId = "phase-3-test",
            exportedAt = "2026-09-22T00:00:00Z",
            sourceAppVersion = "test",
            roomSchemaVersion = AppDatabase.SCHEMA_VERSION,
            includedCategories = categories,
            payload = payload,
        )))
    }
}
