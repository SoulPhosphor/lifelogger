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
@Database(entities = [LogTemplate::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun logTemplateDao(): LogTemplateDao

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

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "data_dragon.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    // v3 removed the unused description column. There is no
                    // released data to preserve, so recreate cleanly on upgrade.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
