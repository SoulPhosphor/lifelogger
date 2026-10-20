package com.datadragon.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The additive 25→26 migration keeps every Clicker Data Log and defaults New Option off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickerComparisonMigrationTest {

    @Test
    fun migration25To26AddsTheSettingOffAndLeavesLogsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(25) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE clicker_logs (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uuid TEXT NOT NULL, " +
                            "title TEXT NOT NULL, createdAt INTEGER NOT NULL, lastAccessedAt INTEGER NOT NULL, " +
                            "lastModifiedAt INTEGER NOT NULL, fieldsJson TEXT NOT NULL, " +
                            "displayOnlyClickerDateTime INTEGER NOT NULL, autoDateStamp INTEGER NOT NULL, " +
                            "autoTimeStamp INTEGER NOT NULL, allowFollowUp INTEGER NOT NULL, " +
                            "statisticsJson TEXT NOT NULL DEFAULT '')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL(
            "INSERT INTO clicker_logs (uuid, title, createdAt, lastAccessedAt, lastModifiedAt, fieldsJson, " +
                "displayOnlyClickerDateTime, autoDateStamp, autoTimeStamp, allowFollowUp) " +
                "VALUES ('stable-id', 'Deliveries', 1, 2, 3, '[\"x\"]', 0, 1, 0, 1)"
        )

        AppDatabase.MIGRATION_25_26.migrate(sqlite)
        AppDatabase.MIGRATION_25_26.migrate(sqlite)

        sqlite.query("SELECT uuid, title, fieldsJson, allowFollowUp, allowAdditionsFromForm FROM clicker_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Deliveries", cursor.getString(1))
            assertEquals("[\"x\"]", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
        }
        sqlite.close()
    }
}
