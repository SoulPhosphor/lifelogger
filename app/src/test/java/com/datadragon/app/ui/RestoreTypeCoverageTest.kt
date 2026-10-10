package com.datadragon.app.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupCategory
import com.datadragon.app.data.BackupCodec
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.Checklist
import com.datadragon.app.data.LuckyList
import com.datadragon.app.data.RestoreMode
import com.datadragon.app.ui.screens.RestoreType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RestoreTypeCoverageTest {
    @Test fun everyBackupCategoryHasOneIndependentRestoreChoice() {
        val choices = RestoreType.entries.filter { it != RestoreType.EVERYTHING }
        assertEquals(BackupCategory.entries.toSet(), choices.map { it.category }.toSet())
        assertEquals(BackupCategory.entries.size, choices.size)
        choices.forEach { choice ->
            assertEquals(setOf(choice.category), choice.categories())
            assertFalse(choice.label().isBlank())
        }
        assertEquals(BackupCategory.entries.toSet(), RestoreType.EVERYTHING.categories())
        assertEquals(setOf(BackupCategory.LUCKY_LISTS), RestoreType.LUCKY_LIST.categories())
        assertEquals(setOf(BackupCategory.LISTS), RestoreType.LIST.categories())
    }

    @Test fun luckyListChoiceRestoresAndUndoesOnlyLuckyListsThroughTheViewModel() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getInstance(app)
        db.clearAllTables()
        try {
            val luckyId = db.luckyListDao().insertList(LuckyList(uuid = "selected-lucky", name = "Before", createdAt = 1))
            val ordinaryId = db.checklistDao().insertChecklist(Checklist(uuid = "unselected-list", name = "Ordinary", createdAt = 1))
            val encoded = BackupCodec.encode(BackupRepository(db).buildFull())
            db.luckyListDao().rename(luckyId, "Current")
            db.checklistDao().renameChecklist(ordinaryId, "Leave unchanged")

            val viewModel = BackupViewModel(app)
            val result = viewModel.restore(encoded, RestoreMode.REPLACE, categories = RestoreType.LUCKY_LIST.categories()) as RestoreResult.Success
            assertEquals(1, result.luckyLists)
            assertEquals(0, result.lists)
            assertEquals("Before", db.luckyListDao().getByUuid("selected-lucky")!!.name)
            assertEquals("Leave unchanged", db.checklistDao().getChecklist(ordinaryId)!!.name)
            assertEquals("Previous state restored.", viewModel.undoImport())
            assertEquals("Current", db.luckyListDao().getByUuid("selected-lucky")!!.name)
            assertEquals("Leave unchanged", db.checklistDao().getChecklist(ordinaryId)!!.name)
        } finally {
            db.clearAllTables()
            java.io.File(app.filesDir, "pre_import_snapshot.json").delete()
        }
    }
}
