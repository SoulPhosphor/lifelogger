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

/**
 * Guards the 12→13 migration: it adds the additive `integrateCalendar` column to
 * `log_templates`, defaulting existing forms to 0 (no calendar) and leaving all
 * other columns untouched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarMigrationTest {

    @Test
    fun migration12To13AddsIntegrateCalendarAndLeavesFormsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
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
                            "sortNewestFirst INTEGER NOT NULL DEFAULT 1)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL(
            "INSERT INTO log_templates (uuid, name, createdAt, schemaJson) " +
                "VALUES ('stable-id', 'Mood', 300, '[]')"
        )

        AppDatabase.MIGRATION_12_13.migrate(sqlite)

        // The existing form is untouched and defaults to no calendar.
        sqlite.query("SELECT name, integrateCalendar FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mood", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
        }

        // The new column accepts an enabled value.
        sqlite.execSQL(
            "INSERT INTO log_templates (uuid, name, createdAt, schemaJson, integrateCalendar) " +
                "VALUES ('cal-id', 'Meds', 400, '[]', 1)"
        )
        sqlite.query(
            "SELECT integrateCalendar FROM log_templates WHERE uuid = 'cal-id'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        sqlite.close()
    }

    @Test
    fun migration13To14AddsTheCalendarsTableAndLeavesFormsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
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
                "VALUES ('stable-id', 'Mood', 300, '[]')"
        )

        AppDatabase.MIGRATION_13_14.migrate(sqlite)

        // The existing form is untouched.
        sqlite.query("SELECT name FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mood", cursor.getString(0))
        }

        // The calendars table now exists and accepts rows.
        sqlite.execSQL(
            "INSERT INTO calendars (templateId, position, type, label, description, configJson) " +
                "VALUES (1, 0, 'heat_map', 'Odor Severity', 'Tracks odor', '')"
        )
        sqlite.query(
            "SELECT type, label FROM calendars WHERE templateId = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("heat_map", cursor.getString(0))
            assertEquals("Odor Severity", cursor.getString(1))
        }
        sqlite.close()
    }

    @Test
    fun migration14To15AddsTheColorPresetsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                // The 14->15 migration only creates the app-global color_presets
                // table, so no prior tables are needed for this guard.
                override fun onCreate(db: SupportSQLiteDatabase) {}

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase

        AppDatabase.MIGRATION_14_15.migrate(sqlite)

        sqlite.execSQL(
            "INSERT INTO color_presets (name, colorsJson) " +
                "VALUES ('My Preset', '[\"#0033FF\",\"#FF0000\"]')"
        )
        sqlite.query("SELECT name, colorsJson FROM color_presets").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("My Preset", cursor.getString(0))
            assertEquals("[\"#0033FF\",\"#FF0000\"]", cursor.getString(1))
        }
        sqlite.close()
    }
}
