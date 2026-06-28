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
@Database(entities = [LogTemplate::class, LogEntry::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logTemplateDao(): LogTemplateDao

    abstract fun logEntryDao(): LogEntryDao

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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "data_dragon.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_3_4)
                    // v3 removed the unused description column. There is no
                    // released data to preserve, so recreate cleanly on any
                    // upgrade path not covered by an explicit migration.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
