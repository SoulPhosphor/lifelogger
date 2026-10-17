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

/** The additive 24→25 migration preserves existing forms and defaults search off. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedSearchMigrationTest {

    @Test
    fun migration24To25AddsTheSettingOffAndLeavesFormsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(24) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE log_templates (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, schemaJson TEXT NOT NULL, " +
                            "formMarkdown TEXT NOT NULL DEFAULT '', " +
                            "locked INTEGER NOT NULL DEFAULT 1, " +
                            "allowAppendedNotes INTEGER NOT NULL DEFAULT 0, " +
                            "automaticTimestamping INTEGER NOT NULL DEFAULT 0, " +
                            "sortTimestampLabel TEXT, " +
                            "sortNewestFirst INTEGER NOT NULL DEFAULT 1, " +
                            "integrateCalendar INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL(
            "INSERT INTO log_templates (uuid, name, createdAt, schemaJson) " +
                "VALUES ('stable-id', 'Mood', 300, '[\"x\"]')"
        )

        AppDatabase.MIGRATION_24_25.migrate(sqlite)

        sqlite.query("SELECT uuid, name, schemaJson, advancedSearchEnabled FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Mood", cursor.getString(1))
            assertEquals("[\"x\"]", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        sqlite.close()
    }
}
