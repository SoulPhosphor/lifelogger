package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.util.UUID

object BackupFixtureTestSupport {
    fun newVersion18Database(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "data-dragon-v18-fixture-${UUID.randomUUID()}.db"

        // Let Room create the exact current table shapes, then remove only the
        // Phase-1 schema so the frozen fixture is loaded against a true v18
        // identity layout before Room upgrades it.
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .build()
            .also { it.openHelper.writableDatabase }
            .close()

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(AppDatabase.SCHEMA_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val sqlite = helper.writableDatabase
        val uuidTables = listOf(
            "log_templates",
            "checklists",
            "idea_logs",
            "daily_lists",
            "daily_list_items",
            "clicker_logs",
            "clicker_cards",
            "color_presets",
        )
        uuidTables.forEach { table ->
            sqlite.execSQL("DROP INDEX IF EXISTS `index_${table}_uuid`")
            sqlite.execSQL("DROP TRIGGER IF EXISTS `prevent_${table}_uuid_update`")
        }
        sqlite.execSQL("DROP TABLE IF EXISTS `${BackupRevisionTracking.TABLE}`")
        sqlite.execSQL("ALTER TABLE color_presets RENAME TO color_presets_v19")
        sqlite.execSQL(
            "CREATE TABLE color_presets (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, colorsJson TEXT NOT NULL)",
        )
        sqlite.execSQL("DROP TABLE color_presets_v19")
        sqlite.version = 18

        val resource = checkNotNull(javaClass.classLoader?.getResource("fixtures/data_dragon_v18.sql")) {
            "Missing test resource fixtures/data_dragon_v18.sql"
        }
        resource.readText()
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { statement ->
                runCatching { sqlite.execSQL(statement) }
                    .getOrElse { error ->
                        System.err.println("Version-18 fixture statement failed: $statement")
                        System.err.println("SQLite error: ${error.message}")
                        throw IllegalStateException("Version-18 fixture statement failed: $statement", error)
                    }
            }
        helper.close()

        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20)
            .addCallback(AppDatabase.UUID_IDENTITY_CALLBACK)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .build()
            .also { it.openHelper.writableDatabase }
    }

    fun tableNames(database: SupportSQLiteDatabase): Set<String> = buildSet {
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
        ).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
        add("sqlite_sequence")
    }
}
