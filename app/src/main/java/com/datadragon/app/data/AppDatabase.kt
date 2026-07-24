package com.datadragon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's local Room database. Local only — no network, no sync.
 */
@Database(
    entities = [LogTemplate::class, LogEntry::class, EntryNote::class, Checklist::class, ChecklistItem::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logTemplateDao(): LogTemplateDao

    abstract fun logEntryDao(): LogEntryDao

    abstract fun entryNoteDao(): EntryNoteDao

    abstract fun checklistDao(): ChecklistDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v2 added the original Form Markdown alongside the parsed schema. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN formMarkdown TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /** v4 added the log_entries table (Phase 4: saved entries). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`templateId` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`updatedAt` TEXT, " +
                        "`valuesJson` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_log_entries_templateId` " +
                        "ON `log_entries` (`templateId`)"
                )
            }
        }

        /**
         * v5 added per-log `locked` / `allowAppendedNotes` flags and the
         * `entry_notes` table (append-only follow-up notes). Existing logs default
         * to locked, preserving the prior create-once behavior.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN locked INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN allowAppendedNotes INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `entry_notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`entryId` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_entry_notes_entryId` " +
                        "ON `entry_notes` (`entryId`)"
                )
            }
        }

        /**
         * v6 added the `checklists` and `checklist_items` tables (the Lists
         * feature). Purely additive — existing logs, entries and notes are
         * untouched.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`checklistId` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`completed` INTEGER NOT NULL, " +
                        "`indent` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_checklistId` " +
                        "ON `checklist_items` (`checklistId`)"
                )
            }
        }

        /**
         * v7 added the per-entry `marked` flag (the manual star highlight).
         * Purely additive; existing entries default to unmarked (no star).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE log_entries ADD COLUMN marked INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v8 gave every log and list a permanent, app-internal `uuid` — its
         * stable identity across installs, used so a backup can recognize "the
         * same log/list" regardless of any later rename. Purely additive: the
         * column is added, then each existing row is backfilled with a freshly
         * generated UUID (v4). The `randomblob` calls re-run per row, so every
         * existing row gets its own distinct id.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // A SQLite expression that builds a random v4 UUID string.
                val uuidExpr =
                    "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-4' || " +
                        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                        "substr('89ab', abs(random()) % 4 + 1, 1) || " +
                        "substr(lower(hex(randomblob(2))), 2) || '-' || " +
                        "lower(hex(randomblob(6)))"
                db.execSQL(
                    "ALTER TABLE log_templates ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE log_templates SET uuid = $uuidExpr")
                db.execSQL(
                    "ALTER TABLE checklists ADD COLUMN uuid TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE checklists SET uuid = $uuidExpr")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "data_dragon.db",
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    // v3 removed the unused description column. There is no
                    // released data to preserve, so recreate cleanly on any
                    // upgrade path not covered by an explicit migration.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
