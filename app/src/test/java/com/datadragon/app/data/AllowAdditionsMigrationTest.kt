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
 * Guards the 22→23 migration: it adds the additive `allowAdditionsFromForm`
 * column to `log_templates`, defaulting existing forms to 0 (off), leaving every
 * other column — the uuid included — untouched, and is safe to run twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AllowAdditionsMigrationTest {

    @Test
    fun migration22To23AddsTheSettingOffAndLeavesFormsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(22) {
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

        AppDatabase.MIGRATION_22_23.migrate(sqlite)
        // Running it again must not fail on the already-present column.
        AppDatabase.MIGRATION_22_23.migrate(sqlite)

        sqlite.query("SELECT uuid, name, schemaJson, allowAdditionsFromForm FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Mood", cursor.getString(1))
            assertEquals("[\"x\"]", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
        }
        sqlite.close()
    }
}
