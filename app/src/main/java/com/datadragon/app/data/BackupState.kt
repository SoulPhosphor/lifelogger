package com.datadragon.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Operational, device-local change tracking for automatic backup. This single
 * row is never part of the portable backup payload.
 *
 * [dataRevision] is increased by SQLite triggers in the same transaction as
 * every INSERT, UPDATE, or DELETE on a protected user-data table, so a
 * committed change can never be missed because the process died afterwards.
 */
@Entity(tableName = BackupRevisionTracking.TABLE)
data class BackupState(
    @PrimaryKey val id: Int = BackupRevisionTracking.ROW_ID,
    val dataRevision: Long = 0,
)

@Dao
interface BackupStateDao {
    @Query("SELECT dataRevision FROM backup_state WHERE id = 1")
    suspend fun dataRevision(): Long?
}

/** Installs and describes the protected-data revision triggers. */
object BackupRevisionTracking {
    const val TABLE = "backup_state"
    const val ROW_ID = 1

    enum class Event(val sql: String, val token: String) {
        INSERT("INSERT", "insert"),
        UPDATE("UPDATE", "update"),
        DELETE("DELETE", "delete"),
    }

    /** Every table registered for backup protection receives all three triggers. */
    val protectedTables: Set<String> get() = BackupPhase0Inventory.protectedTableNames

    fun triggerName(table: String, event: Event): String = "backup_revision_${table}_${event.token}"

    fun expectedTriggerNames(): Set<String> = protectedTables.flatMap { table ->
        Event.entries.map { triggerName(table, it) }
    }.toSet()

    fun createStateTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE` (" +
                "`id` INTEGER NOT NULL, " +
                "`dataRevision` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        ensureRow(db)
    }

    fun ensureRow(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO `$TABLE` (`id`, `dataRevision`) VALUES ($ROW_ID, 0)")
    }

    /**
     * Idempotent. Each trigger first guarantees the state row exists, then
     * increments it, so a missing row can never silently swallow a change.
     */
    fun installTriggers(db: SupportSQLiteDatabase) {
        protectedTables.sorted().forEach { table ->
            Event.entries.forEach { event ->
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS `${triggerName(table, event)}` " +
                        "AFTER ${event.sql} ON `$table` " +
                        "BEGIN " +
                        "INSERT OR IGNORE INTO `$TABLE` (`id`, `dataRevision`) VALUES ($ROW_ID, 0); " +
                        "UPDATE `$TABLE` SET `dataRevision` = `dataRevision` + 1 WHERE `id` = $ROW_ID; " +
                        "END"
                )
            }
        }
    }

    fun install(db: SupportSQLiteDatabase) {
        createStateTable(db)
        installTriggers(db)
    }

    fun installedTriggerNames(db: SupportSQLiteDatabase): Set<String> = buildSet {
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    /** Reads the trigger table and event SQLite recorded for each installed revision trigger. */
    fun installedTriggerSql(db: SupportSQLiteDatabase): Map<String, Pair<String, String>> = buildMap {
        db.query("SELECT name, tbl_name, sql FROM sqlite_master WHERE type = 'trigger'").use { cursor ->
            while (cursor.moveToNext()) {
                put(cursor.getString(0), cursor.getString(1) to cursor.getString(2))
            }
        }
    }
}
