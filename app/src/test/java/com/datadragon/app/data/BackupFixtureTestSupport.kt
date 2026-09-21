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
            val sql = requireNotNull(javaClass.classLoader?.getResource("fixtures/data_dragon_v18.sql"))
                .readText()
            sql.split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .forEach(db.openHelper.writableDatabase::execSQL)
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
