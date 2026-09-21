package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPhase0InventoryTest {

    @Test
    fun legacyFixturesDecodeUsingTheirHistoricalVersions() {
        val loader = requireNotNull(javaClass.classLoader)
        val v1 = BackupCodec.decode(loader.getResource("fixtures/backup-v1.json")!!.readText())
        val v2 = BackupCodec.decode(loader.getResource("fixtures/backup-v2.json")!!.readText())

        assertEquals(1, v1.version)
        assertTrue(v1.logs.single().uuid.isEmpty())
        assertTrue(v1.checklists.isEmpty())
        assertEquals(2, v2.version)
        assertTrue(v2.logs.single().uuid.isNotEmpty())
        assertEquals(1, v2.checklists.size)
        assertTrue(v2.logs.single().calendars.isNotEmpty())
    }

    @Test
    fun version18FixturePopulatesEveryCurrentUserDataTable() {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            assertEquals(18, db.openHelper.readableDatabase.version)
            val counts = BackupPhase0Inventory.userDataTables.associate { entry ->
                entry.tableName to db.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM ${entry.tableName}"
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getInt(0)
                }
            }
            assertEquals(13, counts.size)
            assertTrue(counts.values.all { it > 0 })
            assertTrue(counts["checklists"]!! >= 2)
            assertTrue(counts["daily_list_items"]!! >= 2)
            assertTrue(counts["clicker_cards"]!! >= 2)
        } finally {
            db.close()
        }
    }

    @Test
    fun allCurrentUserDataTablesAreRepresentedInTheInventory() {
        assertEquals(
            setOf(
                "log_templates", "log_entries", "entry_notes", "calendars",
                "checklists", "checklist_items", "idea_logs", "idea_entries",
                "color_presets", "daily_lists", "daily_list_items",
                "clicker_logs", "clicker_cards",
            ),
            BackupPhase0Inventory.protectedTableNames,
        )
    }

    @Test
    fun operationalStateIsExplicitlyExcluded() {
        val excluded = BackupPhase0Inventory.excludedOperationalState
        assertTrue(excluded.contains("automatic_backup_folder_uri"))
        assertTrue(excluded.contains("android_persisted_folder_permission"))
        assertTrue(excluded.contains("work_manager_identifiers"))
        assertTrue(excluded.contains("data_dragon.db-wal"))
        assertFalse(excluded.contains("log_templates"))
    }

    @Test
    fun portablePreferencesAreAnExplicitAllowlist() {
        val keys = BackupPhase0Inventory.portablePreferenceKeys
        assertEquals(26, keys.size)
        assertTrue(keys.contains("nav_style"))
        assertTrue(keys.contains("daily_list_retention"))
        assertTrue(keys.contains("mode_enabled_clicker"))
        assertFalse(keys.contains("automatic_backup_folder_uri"))
    }

    @Test
    fun coverageGuardPassesForThePopulatedVersion18Database() {
        val db = BackupFixtureTestSupport.newVersion18Database()
        try {
            BackupPhase0Inventory.roomAndSqliteTables.forEach { table ->
                assertTrue(BackupFixtureTestSupport.tableNames(db.openHelper.readableDatabase).contains(table))
            }
            BackupCoverageGuard.assertCovered(db.openHelper.readableDatabase)
        } finally {
            db.close()
        }
    }

    @Test
    fun coverageGuardNamesAnUnregisteredTableAndPointsToTheGuide() {
        val error = runCatching {
            BackupCoverageGuard.assertCovered(
                BackupPhase0Inventory.protectedTableNames + "future_user_data"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("future_user_data"))
        assertTrue(error.message!!.contains("not registered for backup protection"))
        assertTrue(error.message!!.contains("docs/BACKUP_SYSTEM_EXTENSION_GUIDE.md"))
    }
}
