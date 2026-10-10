package com.datadragon.app.ui

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.datadragon.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRestoreRefreshTest {
    @Test fun firstEmptyOrPreferencesOnlyMergeLeavesUndoUnavailable() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val undoFile = java.io.File(app.filesDir, "pre_import_snapshot.json")
        undoFile.delete()
        val viewModel = BackupViewModel(app)
        val empty = BackupCodec.encode(BackupFile(
            exportedAt = "2026-10-10T00:00:00Z",
            includedCategories = listOf(BackupCategory.LUCKY_LISTS, BackupCategory.PORTABLE_PREFERENCES),
            payload = BackupPayload(luckyLists = emptyList(), portablePreferences = BackupPortablePreferences.defaults()),
        ))
        for (category in listOf(BackupCategory.LUCKY_LISTS, BackupCategory.PORTABLE_PREFERENCES, BackupCategory.FORMS)) {
            val result = viewModel.restore(empty, RestoreMode.MERGE, categories = setOf(category)) as RestoreResult.Success
            assertEquals(0, result.counts.added)
            assertEquals(false, viewModel.hasUndoSnapshot())
            assertEquals(false, undoFile.exists())
        }
    }

    @Test fun visibleControlsFollowPreferencesRestoreAndUndo() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("data_dragon_settings", 0).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val repo = SettingsRepository(app)
        val db = AppDatabase.getInstance(app)
        val settings = SettingsViewModel(app)
        val store = ViewModelStore().apply { put("settings", settings) }
        val backup = BackupViewModel(app)
        val original = repo.portableBackupSnapshot()
        val incoming = original.copy(
            automaticBackupCadence = AutoBackupCadence.CUSTOM.key,
            automaticBackupCustomDays = 12,
            automaticBackupRetention = 7,
            navStyle = NavStyle.DROPDOWN.key,
        )
        try {
            val encoded = runBlocking(Dispatchers.IO) {
                BackupCodec.encode(BackupRepository(db, { incoming }).buildFull())
            }
            runBlocking(Dispatchers.IO) {
                backup.restore(encoded, RestoreMode.REPLACE, categories = setOf(BackupCategory.PORTABLE_PREFERENCES))
            }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(AutoBackupCadence.CUSTOM, settings.autoBackupCadence.value)
            assertEquals("12", settings.autoBackupCustomDaysText.value)
            assertEquals(7, settings.autoBackupRetention.value)
            assertEquals(NavStyle.DROPDOWN, settings.navStyle.value)
            val undoFile = java.io.File(app.filesDir, "pre_import_snapshot.json")
            val previousUndo = undoFile.readText()
            val kept = runBlocking(Dispatchers.IO) {
                val earlierPreferences = BackupCodec.encode(BackupRepository(db, { original }).buildFull())
                backup.restore(earlierPreferences, RestoreMode.MERGE, categories = setOf(BackupCategory.PORTABLE_PREFERENCES)) as RestoreResult.Success
            }
            assertEquals(1, kept.counts.skipped)
            assertEquals(0, kept.counts.added)
            assertEquals(previousUndo, undoFile.readText())
            val empty = BackupCodec.encode(BackupFile(
                exportedAt = "2026-10-10T00:00:00Z",
                includedCategories = listOf(BackupCategory.LUCKY_LISTS),
                payload = BackupPayload(luckyLists = emptyList()),
            ))
            runBlocking(Dispatchers.IO) {
                backup.restore(empty, RestoreMode.MERGE, categories = setOf(BackupCategory.LUCKY_LISTS))
            }
            assertEquals(previousUndo, undoFile.readText())
            assertEquals(12, repo.automaticBackupCustomDays)
            runBlocking(Dispatchers.IO) { backup.undoImport() }
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(original.automaticBackupCadence, settings.autoBackupCadence.value.key)
            assertEquals(original.automaticBackupCustomDays.toString(), settings.autoBackupCustomDaysText.value)
            assertEquals(original.automaticBackupRetention, settings.autoBackupRetention.value)
            assertEquals(original.navStyle, settings.navStyle.value.key)
            // An unrelated portable write must not erase incomplete custom-days input.
            settings.setAutoBackupCustomDaysText("")
            settings.setAutoBackupRetention(5)
            assertEquals("", settings.autoBackupCustomDaysText.value)
            settings.setAutoBackupCustomDaysText("012")
            settings.setAutoBackupRetention(7)
            assertEquals("012", settings.autoBackupCustomDaysText.value)
        } finally {
            store.clear()
            java.io.File(app.filesDir, "pre_import_snapshot.json").delete()
        }
    }
}
