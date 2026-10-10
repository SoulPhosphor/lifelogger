package com.datadragon.app.ui

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
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
    @Test fun visibleControlsFollowPreferencesRestoreAndUndo() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("data_dragon_settings", 0).edit().clear().commit()
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
