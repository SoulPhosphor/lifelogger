package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedSearchPersistenceTest {
    private fun form(enabled: Boolean = false) = LogTemplate(uuid = "form", name = "Form", createdAt = 1, schemaJson = "[]", advancedSearchEnabled = enabled)

    @Test fun formDefaultsOffAndTargetedEditPreservesIdentityAndAnswers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val id = db.logTemplateDao().insert(form())
            assertFalse(db.logTemplateDao().getById(id)!!.advancedSearchEnabled)
            db.logEntryDao().insert(LogEntry(templateId = id, createdAt = "2026-10-17T00:00:00Z", valuesJson = "{}"))
            db.logTemplateDao().updateForm(id, "Renamed", "[]", "", false, null, true, false, false, true)
            assertEquals("form", db.logTemplateDao().getById(id)!!.uuid)
            assertTrue(db.logTemplateDao().getById(id)!!.advancedSearchEnabled)
            assertEquals(1, db.logEntryDao().getForTemplateOnce(id).size)
        } finally { db.close() }
    }

    @Test fun dailyPreferencePersistsTracksChangesAndRestoresOldAbsenceOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("data_dragon_settings", 0).edit().clear().commit()
        val settings = SettingsRepository(context)
        assertFalse(settings.dailyListAdvancedSearchEnabled)
        val revision = settings.portablePreferencesRevision
        settings.dailyListAdvancedSearchEnabled = true
        assertTrue(SettingsRepository(context).dailyListAdvancedSearchEnabled)
        assertTrue(settings.portablePreferencesRevision > revision)
        val preferences = settings.portableBackupSnapshot()
        assertEquals(true, preferences.dailyListAdvancedSearchEnabled)
        settings.applyPortableBackup(preferences.copy(dailyListAdvancedSearchEnabled = null))
        assertFalse(settings.dailyListAdvancedSearchEnabled)
    }

    @Test fun formatNineRoundTripAndOlderChecksumsRemainCompatible() {
        val log = BackupCodec.logOf(form(true), emptyList())
        val file = BackupFile.single("2026-10-17T00:00:00Z", BackupCategory.FORMS, BackupPayload(forms = listOf(log)))
        val encoded = BackupCodec.encode(file)
        assertTrue(BackupCodec.templateOf(BackupCodec.decode(encoded).logs.single()).advancedSearchEnabled)
        assertEquals("form", BackupCodec.decode(encoded).logs.single().uuid)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(encoded.replace("\"version\": 9", "\"version\": 8"))
        }
        val legacyCompatible = BackupCodec.encode(file.copy(payload = BackupPayload(forms = listOf(log.copy(advancedSearchEnabled = null)))))
        val version8 = legacyCompatible.replace("\"version\": 9", "\"version\": 8")
        assertFalse(BackupCodec.templateOf(BackupCodec.decode(version8).logs.single()).advancedSearchEnabled)
    }

    @Test fun fullIndividualMergeReplaceAndUndoUseExistingBoundaries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        var preferences = BackupPortablePreferences.defaults().copy(dailyListAdvancedSearchEnabled = true)
        val repository = BackupRepository(db, { preferences }, "test", { preferences = it })
        try {
            db.logTemplateDao().insert(form(true))
            val snapshot = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
            assertEquals(true, snapshot.payload.forms!!.single().advancedSearchEnabled)
            assertEquals(true, snapshot.payload.portablePreferences!!.dailyListAdvancedSearchEnabled)
            val incoming = BackupFile.single("2026-10-17T00:00:00Z", BackupCategory.FORMS,
                BackupPayload(forms = listOf(BackupCodec.logOf(form(false), emptyList()))))
            repository.restore(incoming, RestoreMode.MERGE, conflictChoices = repository.preflight(incoming, RestoreMode.MERGE).conflicts.associate { it.id to RestoreConflictChoice.KEEP_CURRENT })
            assertTrue(db.logTemplateDao().getByUuid("form")!!.advancedSearchEnabled)
            repository.restore(incoming, RestoreMode.MERGE, conflictChoices = repository.preflight(incoming, RestoreMode.MERGE).conflicts.associate { it.id to RestoreConflictChoice.USE_BACKUP })
            assertFalse(db.logTemplateDao().getByUuid("form")!!.advancedSearchEnabled)
            repository.restore(snapshot, RestoreMode.REPLACE)
            assertTrue(db.logTemplateDao().getByUuid("form")!!.advancedSearchEnabled)
            assertEquals("form", db.logTemplateDao().getAllOnce().single().uuid)
            val prefsOnly = BackupFile.single("2026-10-17T00:00:00Z", BackupCategory.PORTABLE_PREFERENCES,
                BackupPayload(portablePreferences = preferences.copy(dailyListAdvancedSearchEnabled = null)))
            repository.restore(prefsOnly, RestoreMode.MERGE)
            assertEquals(true, preferences.dailyListAdvancedSearchEnabled)
            repository.restore(prefsOnly, RestoreMode.REPLACE)
            assertNull(preferences.dailyListAdvancedSearchEnabled)
        } finally { db.close() }
    }
}
