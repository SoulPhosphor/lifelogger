package com.datadragon.app.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 0 inventory only. This is deliberately not the Phase 2 backup registry:
 * it records what must be protected so coverage tests can catch omissions before
 * snapshot and restore behavior is expanded.
 */
data class BackupTableInventoryEntry(
    val tableName: String,
    val category: String,
)

data class BackupExcludedOperationalStateEntry(
    val token: String,
    val reason: String,
)

object BackupPhase0Inventory {
    val userDataTables: List<BackupTableInventoryEntry> = listOf(
        BackupTableInventoryEntry("log_templates", "forms"),
        BackupTableInventoryEntry("log_entries", "forms"),
        BackupTableInventoryEntry("entry_notes", "forms"),
        BackupTableInventoryEntry("calendars", "form_calendars"),
        BackupTableInventoryEntry("checklists", "lists"),
        BackupTableInventoryEntry("checklist_items", "lists"),
        BackupTableInventoryEntry("idea_logs", "idea_logs"),
        BackupTableInventoryEntry("idea_entries", "idea_logs"),
        BackupTableInventoryEntry("color_presets", "saved_calendar_color_presets"),
        BackupTableInventoryEntry("daily_lists", "daily_tasks"),
        BackupTableInventoryEntry("daily_list_items", "daily_tasks"),
        BackupTableInventoryEntry("clicker_logs", "clicker_data"),
        BackupTableInventoryEntry("clicker_cards", "clicker_data"),
    )

    /** Preferences representing portable user intent, listed individually. */
    val portablePreferenceKeys: Set<String> = setOf(
        "auto_capitalize_labels",
        "auto_capitalize_options",
        "last_home_view",
        "nav_style",
        "nav_use_mode_label",
        "mode_enabled_forms",
        "mode_enabled_lists",
        "mode_enabled_ideas",
        "mode_enabled_daily_list",
        "mode_enabled_clicker",
        "list_complete_icon",
        "list_cross_out_completed",
        "list_move_completed_bottom",
        "daily_list_heading",
        "daily_list_auto_renew",
        "daily_list_show_completed",
        "daily_list_show_current_unfinished",
        "daily_list_show_past_unfinished",
        "daily_list_auto_trash_past",
        "daily_list_auto_trash_keep",
        "daily_list_auto_reopen",
        "daily_list_allow_title",
        "daily_list_celebration_enabled",
        "daily_list_celebration_icon",
        "daily_list_protect_favorited",
        "daily_list_retention",
        "automatic_backup_cadence",
        "automatic_backup_custom_days",
        "automatic_backup_retention",
    )

    /** Portable preferences added by backup format version 4; version-3 files never carry them. */
    val automaticBackupPortablePreferenceKeys: Set<String> = setOf(
        "automatic_backup_cadence",
        "automatic_backup_custom_days",
        "automatic_backup_retention",
    )

    /**
     * Device-local or reproducible state intentionally excluded from portable
     * backup. These are structured inventory entries, not SharedPreferences keys.
     */
    val excludedOperationalState: List<BackupExcludedOperationalStateEntry> = listOf(
        BackupExcludedOperationalStateEntry("automatic_backup_folder_uri", "Device-specific folder access"),
        BackupExcludedOperationalStateEntry("android_persisted_folder_permission", "Device-specific permission grant"),
        BackupExcludedOperationalStateEntry("automatic_backup_enabled", "Device-local execution state"),
        BackupExcludedOperationalStateEntry("automatic_backup_last_success", "Device-local backup history"),
        BackupExcludedOperationalStateEntry("automatic_backup_destination", "Device-local backup history"),
        BackupExcludedOperationalStateEntry("automatic_backup_errors", "Device-local backup history"),
        BackupExcludedOperationalStateEntry("automatic_backup_dirty_revisions", "Device-local change-tracking state"),
        BackupExcludedOperationalStateEntry("work_manager_identifiers", "Device-local scheduler state"),
        BackupExcludedOperationalStateEntry("automatic_backup_attempt_history", "Device-local backup history"),
        BackupExcludedOperationalStateEntry("pre_import_snapshot.json", "Local undo slot, rebuilt by restore"),
        BackupExcludedOperationalStateEntry("data_dragon.db", "Raw database transport is not portable"),
        BackupExcludedOperationalStateEntry("data_dragon.db-wal", "Raw database transport is not portable"),
        BackupExcludedOperationalStateEntry("data_dragon.db-shm", "Raw database transport is not portable"),
        BackupExcludedOperationalStateEntry("cache_files", "Reproducible output"),
        BackupExcludedOperationalStateEntry("generated_previews", "Reproducible output"),
        BackupExcludedOperationalStateEntry("rendered_heat_maps", "Reproducible output"),
        BackupExcludedOperationalStateEntry("generated_reports", "Reproducible output"),
    )

    val excludedOperationalStateTokens: Set<String> = excludedOperationalState.map { it.token }.toSet()

    /** Tables created by Room or SQLite rather than by a user-data feature. */
    val roomAndSqliteTables: Set<String> = setOf(
        "android_metadata",
        "room_master_table",
        "sqlite_sequence",
    )

    /**
     * Operational tables that are intentionally outside the portable payload.
     * backup_state holds the device-local protected-data revision.
     */
    val operationalTables: Set<String> = setOf(
        BackupRevisionTracking.TABLE,
    )

    val protectedTableNames: Set<String> = userDataTables.map { it.tableName }.toSet()
}

object BackupCoverageGuard {
    private const val GUIDE = "docs/BACKUP_SYSTEM_EXTENSION_GUIDE.md"

    fun unregisteredTables(actualTables: Set<String>): Set<String> =
        actualTables - BackupPhase0Inventory.protectedTableNames - BackupPhase0Inventory.roomAndSqliteTables -
            BackupPhase0Inventory.operationalTables

    fun assertCovered(database: SupportSQLiteDatabase) {
        val actualTables = buildSet {
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            ).use { cursor ->
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            add("sqlite_sequence")
        }
        assertCovered(actualTables)
    }

    fun assertCovered(actualTables: Set<String>) {
        val unregistered = unregisteredTables(actualTables)
        check(unregistered.isEmpty()) {
            "Unprotected database table(s): ${unregistered.sorted().joinToString()}. " +
                "Each listed table is not registered for backup protection. " +
                "See $GUIDE."
        }
    }
}
