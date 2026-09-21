package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider

object BackupFixtureTestSupport {
    fun newVersion18Database(): AppDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build().also { db ->
            val resource = checkNotNull(javaClass.classLoader?.getResource("fixtures/data_dragon_v18.sql")) {
                "Missing test resource fixtures/data_dragon_v18.sql"
            }
            resource.readText()
                .split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach { statement ->
                    runCatching { db.openHelper.writableDatabase.execSQL(statement) }
                        .getOrElse { error ->
                            throw IllegalStateException("Version-18 fixture statement failed: $statement", error)
                        }
                }
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
