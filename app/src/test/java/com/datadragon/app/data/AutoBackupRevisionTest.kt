package com.datadragon.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 5: protected-data and portable-preference revision tracking. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupRevisionTest {

    @Test
    fun everyProtectedTableHasInsertUpdateAndDeleteRevisionTriggers() {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val sqlite = db.openHelper.writableDatabase
            val installed = BackupRevisionTracking.installedTriggerSql(sqlite)
            BackupPhase0Inventory.protectedTableNames.forEach { table ->
                BackupRevisionTracking.Event.entries.forEach { event ->
                    val name = BackupRevisionTracking.triggerName(table, event)
                    val trigger = installed[name]
                    assertTrue("Missing revision trigger $name for protected table $table", trigger != null)
                    assertEquals(table, trigger!!.first)
                    assertTrue(trigger.second.contains("AFTER ${event.sql} ON `$table`"))
                    assertTrue(trigger.second.contains("`dataRevision` = `dataRevision` + 1"))
                }
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun registryAndRevisionTriggersCannotDriftApart() {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val sqlite = db.openHelper.writableDatabase
            val userTables = BackupFixtureTestSupport.tableNames(sqlite) -
                BackupPhase0Inventory.roomAndSqliteTables -
                BackupPhase0Inventory.operationalTables
            // Every user-data table in SQLite is a registered protected table ...
            assertEquals(BackupPhase0Inventory.protectedTableNames, userTables)
            // ... the triggers are generated from that same registry ...
            assertEquals(
                BackupPhase0Inventory.protectedTableNames,
                BackupRevisionTracking.protectedTables,
            )
            // ... and every installed revision trigger belongs to a registered table.
            val revisionTriggers = BackupRevisionTracking.installedTriggerSql(sqlite)
                .filterKeys { it.startsWith("backup_revision_") }
            assertEquals(BackupRevisionTracking.expectedTriggerNames(), revisionTriggers.keys)
            assertTrue(revisionTriggers.values.all { it.first in BackupPhase0Inventory.protectedTableNames })
        } finally {
            db.close()
        }
    }

    @Test
    fun everyProtectedTableIncrementsTheDataRevisionOnInsertUpdateAndDelete() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val sqlite = db.openHelper.writableDatabase
            val dao = db.backupStateDao()
            BackupPhase0Inventory.protectedTableNames.sorted().forEach { table ->
                val beforeInsert = dao.dataRevision()!!
                copyFirstRow(sqlite, table)
                val afterInsert = dao.dataRevision()!!
                assertTrue("INSERT on $table did not increase the revision", afterInsert > beforeInsert)

                sqlite.execSQL("UPDATE `$table` SET `id` = `id` WHERE `id` = (SELECT MAX(`id`) FROM `$table`)")
                val afterUpdate = dao.dataRevision()!!
                assertTrue("UPDATE on $table did not increase the revision", afterUpdate > afterInsert)

                sqlite.execSQL("DELETE FROM `$table` WHERE `id` = (SELECT MAX(`id`) FROM `$table`)")
                val afterDelete = dao.dataRevision()!!
                assertTrue("DELETE on $table did not increase the revision", afterDelete > afterUpdate)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun revisionIncreaseCommitsOrRollsBackWithTheDataChange() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val sqlite = db.openHelper.writableDatabase
            val before = db.backupStateDao().dataRevision()!!
            sqlite.beginTransaction()
            try {
                sqlite.execSQL("UPDATE checklists SET name = 'Rolled back'")
            } finally {
                sqlite.endTransaction()
            }
            assertEquals(before, db.backupStateDao().dataRevision())
        } finally {
            db.close()
        }
    }

    @Test
    fun snapshotCapturesTheRevisionFromItsOwnTransaction() = runBlocking {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            val repository = BackupRepository(db)
            db.openHelper.writableDatabase.execSQL("UPDATE checklists SET name = name || ' edited'")
            val captured = repository.buildFullCaptured { BackupPortablePreferences.defaults() to 7L }
            assertEquals(db.backupStateDao().dataRevision(), captured.dataRevision)
            assertEquals(7L, captured.preferencesRevision)
            assertFalse(BackupCodec.encode(captured.backup).contains("dataRevision"))
        } finally {
            db.close()
        }
    }

    @Test
    fun portablePreferenceChangesIncreaseThePreferenceRevision() {
        val settings = freshSettings()
        val changes: List<Pair<String, (SettingsRepository) -> Unit>> = listOf(
            change("auto_capitalize_labels") { it.autoCapitalizeLabels = false },
            change("auto_capitalize_options") { it.autoCapitalizeOptions = false },
            change("last_home_view") { it.lastView = HomeView.CLICKER },
            change("nav_style") { it.navStyle = NavStyle.DROPDOWN },
            change("nav_use_mode_label") { it.useModeLabelInDropdown = false },
            change("mode_enabled_ideas") { it.setModeEnabled(HomeView.IDEAS, false) },
            change("list_complete_icon") { it.completeIcon = CompleteIcon.CHECKMARK },
            change("list_cross_out_completed") { it.crossOutWhenCompleted = true },
            change("list_move_completed_bottom") { it.moveCompletedToBottom = true },
            change("daily_list_heading") { it.dailyListHeading = "Today" },
            change("daily_list_auto_renew") { it.dailyListAutoRenew = true },
            change("daily_list_show_completed") { it.dailyListShowCompleted = false },
            change("daily_list_show_current_unfinished") { it.dailyListShowCurrentUnfinished = false },
            change("daily_list_show_past_unfinished") { it.dailyListShowPastUnfinished = true },
            change("daily_list_auto_trash_past") { it.dailyListAutoTrashPast = true },
            change("daily_list_auto_trash_keep") { it.dailyListAutoTrashKeepPast = 3 },
            change("daily_list_auto_reopen") { it.dailyListAutoReopen = true },
            change("daily_list_allow_title") { it.dailyListAllowTitle = true },
            change("daily_list_celebration_enabled") { it.dailyListCelebrationEnabled = false },
            change("daily_list_celebration_icon") { it.dailyListCelebrationIcon = CelebrationIcon.CHEER },
            change("daily_list_protect_favorited") { it.dailyListProtectFavorited = false },
            change("daily_list_retention") { it.dailyListRetentionRaw = "14" },
            change("automatic_backup_cadence") { it.automaticBackupCadence = AutoBackupCadence.WEEKLY },
            change("automatic_backup_custom_days") { it.automaticBackupCustomDays = 10 },
            change("automatic_backup_retention") { it.automaticBackupRetention = 7 },
        )
        changes.forEach { (key, change) ->
            val before = settings.portablePreferencesRevision
            val snapshotBefore = settings.portableBackupSnapshot()
            change(settings)
            assertTrue("Changing $key did not increase the preference revision", settings.portablePreferencesRevision > before)
            assertTrue("Changing $key did not change the portable snapshot", settings.portableBackupSnapshot() != snapshotBefore)
        }
        // Every portable key except the mode toggles not exercised individually is covered above.
        val covered = changes.map { it.first }.toSet() + setOf(
            "mode_enabled_forms",
            "mode_enabled_lists",
            "mode_enabled_daily_list",
            "mode_enabled_clicker",
        )
        assertEquals(BackupPhase0Inventory.portablePreferenceKeys, covered)
    }

    @Test
    fun unchangedWritesAndDeviceLocalWorkflowStateDoNotIncreaseThePreferenceRevision() {
        val settings = freshSettings()
        settings.navStyle = NavStyle.ICONS
        settings.autoCapitalizeLabels = true
        settings.setModeEnabled(HomeView.FORMS, true)
        assertEquals(0L, settings.portablePreferencesRevision)

        settings.restoreConflictPolicy = RestoreConflictPolicy.USE_BACKUP
        assertEquals(0L, settings.portablePreferencesRevision)
    }

    @Test
    fun restoringPortablePreferencesIncreasesTheRevision() {
        val settings = freshSettings()
        settings.applyPortableBackup(BackupPortablePreferences.defaults().copy(navStyle = NavStyle.DROPDOWN.key))
        assertEquals(1L, settings.portablePreferencesRevision)
        assertEquals(NavStyle.DROPDOWN, settings.navStyle)
    }

    @Test
    fun preferenceRevisionSurvivesANewRepositoryInstance() {
        val settings = freshSettings()
        settings.navStyle = NavStyle.DROPDOWN
        val restarted = SettingsRepository(ApplicationProvider.getApplicationContext())
        assertEquals(1L, restarted.portablePreferencesRevision)
    }

    private fun change(
        key: String,
        block: (SettingsRepository) -> Unit,
    ): Pair<String, (SettingsRepository) -> Unit> = key to block

    private fun freshSettings(): SettingsRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("data_dragon_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsRepository(context)
    }

    /** Inserts a copy of an existing row with fresh identity and unique columns. */
    private fun copyFirstRow(sqlite: SupportSQLiteDatabase, table: String) {
        val columns = buildList {
            sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }.filter { it != "id" }
        val selected = columns.joinToString { column ->
            when (column) {
                "uuid" -> "'${UUID.randomUUID()}'"
                "date" -> if (table == "daily_lists") "'2099-12-31'" else "`date`"
                else -> "`$column`"
            }
        }
        val names = columns.joinToString { "`$it`" }
        sqlite.execSQL(
            "INSERT INTO `$table` ($names) SELECT $selected FROM `$table` " +
                "WHERE `id` = (SELECT MIN(`id`) FROM `$table`)"
        )
    }
}
