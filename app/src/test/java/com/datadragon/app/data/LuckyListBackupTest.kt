package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.datadragon.app.export.ChecklistExportFormat
import com.datadragon.app.export.LuckyListExport
import com.datadragon.app.ui.restoreIndividualItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LuckyListBackupTest {
    private fun database() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries().addCallback(AppDatabase.UUID_IDENTITY_CALLBACK).addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()

    @Test fun backupReplaceMergeAndUndoPreserveAllFieldsAndIdentities() = runBlocking {
        val db = database()
        try {
            val dao = db.luckyListDao()
            val id = dao.insertList(LuckyList(uuid = "lucky-id", name = "Dinner", createdAt = 123, excludePreviouslySelected = true))
            dao.insertItem(LuckyListItem(luckyListId = id, text = "Soup", position = 1))
            dao.insertItem(LuckyListItem(luckyListId = id, text = "Pizza", position = 0))
            val repo = BackupRepository(db)
            val backup = BackupCodec.decode(BackupCodec.encode(repo.buildFull()))
            assertEquals(1, backup.counts.luckyLists)
            assertEquals(2, backup.counts.luckyListItems)
            val saved = backup.payload.luckyLists!!.single()
            assertEquals(listOf("Pizza", "Soup"), saved.items.map { it.text })
            assertTrue(saved.excludePreviouslySelected)
            assertTrue(repo.preflight(backup, RestoreMode.MERGE).conflicts.isEmpty())
            assertEquals(1, repo.restore(backup, RestoreMode.MERGE).categories[BackupCategory.LUCKY_LISTS]!!.skipped)
            dao.rename(id, "Changed"); dao.setExclusion(id, false)
            assertEquals("lucky-id", dao.getList(id)!!.uuid)
            val changed = repo.buildFull()
            val conflict = repo.preflight(backup, RestoreMode.MERGE).conflicts.single()
            repo.restore(backup, RestoreMode.MERGE, setOf(BackupCategory.LUCKY_LISTS), mapOf(conflict.id to RestoreConflictChoice.KEEP_CURRENT))
            assertEquals("Changed", dao.getByUuid("lucky-id")!!.name)
            repo.restore(backup, RestoreMode.MERGE, setOf(BackupCategory.LUCKY_LISTS), mapOf(conflict.id to RestoreConflictChoice.USE_BACKUP))
            val restored = dao.getByUuid("lucky-id")!!
            assertTrue(restored.id != id)
            assertEquals(saved, repo.buildFull().payload.luckyLists!!.single())
            assertTrue(dao.getItemsOnce(restored.id).all { it.luckyListId == restored.id })
            repo.undo(UndoSnapshot("now", changed, listOf(BackupCategory.LUCKY_LISTS)))
            assertEquals("Changed", dao.getByUuid("lucky-id")!!.name)
            repo.restore(backup, RestoreMode.REPLACE, setOf(BackupCategory.LUCKY_LISTS))
            assertEquals(saved, repo.buildFull().payload.luckyLists!!.single())
        } finally { db.close() }
    }

    @Test fun editsReorderSettingsAndDeletionMakeAutomaticBackupDirty() = runBlocking {
        val db = database()
        try {
            val dao = db.luckyListDao()
            var revision = db.backupStateDao().dataRevision()!!
            suspend fun changed(block: suspend () -> Unit) { block(); val next = db.backupStateDao().dataRevision()!!; assertTrue(next > revision); revision = next }
            var id = 0L; var item = 0L
            changed { id = dao.insertList(LuckyList(uuid = "stable", name = "A", createdAt = 1)) }
            changed { item = dao.insertItem(LuckyListItem(luckyListId = id, text = "One", position = 0)) }
            changed { dao.updateText(item, "Edited") }
            changed { dao.setPosition(item, 2) }
            changed { dao.setExclusion(id, true) }
            changed { dao.rename(id, "B") }
            assertEquals("stable", dao.getList(id)!!.uuid)
            val error = runCatching { db.openHelper.writableDatabase.execSQL("UPDATE lucky_lists SET uuid = 'different' WHERE id = $id") }.exceptionOrNull()
            assertNotNull(error)
            changed { dao.deleteWithItems(id) }
        } finally { db.close() }
    }

    @Test fun olderBackupsLeaveLuckyListsUntouchedAndExplicitEmptyClearsThem() = runBlocking {
        val db = database()
        try {
            val dao = db.luckyListDao()
            dao.insertList(LuckyList(uuid = "keep", name = "Existing", createdAt = 1))
            val legacy = javaClass.classLoader!!.getResource("fixtures/backup-v2.json")!!.readText()
            val repo = BackupRepository(db)
            repo.restore(BackupCodec.decode(legacy), RestoreMode.REPLACE)
            assertEquals(1, dao.getAllOnce().size)
            val empty = BackupFile.single("now", BackupCategory.LUCKY_LISTS, BackupPayload(luckyLists = emptyList()))
            repo.restore(empty, RestoreMode.REPLACE)
            assertTrue(dao.getAllOnce().isEmpty())
        } finally { db.close() }
    }

    @Test fun singleExportsRestoreCorrectCategoryAndPlainExportsHaveNoCheckboxes() = runBlocking {
        val db = database()
        try {
            val list = LuckyList(id = 100, uuid = "exported", name = "Pick", createdAt = 1, draft = true, excludePreviouslySelected = true)
            val items = listOf(LuckyListItem(luckyListId = 100, text = "One", position = 0))
            for (format in listOf(ChecklistExportFormat.TEXT, ChecklistExportFormat.MARKDOWN)) {
                assertFalse(LuckyListExport.build(list, items, format).bytes.decodeToString().contains("[ ]"))
            }
            val json = LuckyListExport.build(list, items, ChecklistExportFormat.JSON).bytes.decodeToString()
            restoreIndividualItem(json, BackupRepository(db))
            assertTrue(db.checklistDao().getAllChecklistsOnce().isEmpty())
            val restored = db.luckyListDao().getByUuid("exported")!!
            assertTrue(restored.draft)
            assertTrue(restored.excludePreviouslySelected)
            assertEquals("One", db.luckyListDao().getItemsOnce(restored.id).single().text)
            val payload = BackupCodec.decode(json).payload
            val bad = BackupFile.single("now", BackupCategory.LUCKY_LISTS, payload.copy(luckyLists = payload.luckyLists!!.map { it.copy(items = listOf(BackupLuckyListItem("A", 0), BackupLuckyListItem("B", 0))) }))
            assertTrue(runCatching { BackupRestoreValidator.validate(bad) }.isFailure)
        } finally { db.close() }
    }
}
