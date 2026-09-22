package com.datadragon.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityHardeningMigrationTest {

    private val existingUuid = "ABCDEFAB-CDEF-4ABC-8DEF-ABCDEFABCDEF"
    private val duplicatedUuid = "11111111-2222-4333-8444-555555555555"
    private val uuidTables = listOf(
        "log_templates",
        "checklists",
        "idea_logs",
        "daily_lists",
        "daily_list_items",
        "clicker_logs",
        "clicker_cards",
    )

    @Test
    fun migrationRepairsOnlyBlankAndLaterDuplicateUuidsBeforeEnforcingIdentity() {
        val sqlite = version18Database()
        try {
            AppDatabase.MIGRATION_18_19.migrate(sqlite)

            (uuidTables + "color_presets").forEach { table ->
                val rows = uuidsById(sqlite, table)
                assertEquals("every migrated UUID must be unique in $table", rows.size, rows.values.toSet().size)
                assertTrue("every migrated UUID must be non-blank in $table", rows.values.none { it.isBlank() })
                assertEquals(1, schemaObjectCount(sqlite, "index", "index_${table}_uuid"))
                assertEquals(1, schemaObjectCount(sqlite, "trigger", "prevent_${table}_uuid_update"))
            }

            uuidTables.forEach { table ->
                val rows = uuidsById(sqlite, table)
                assertEquals("valid UUID changed in $table", existingUuid, rows.getValue(1))
                assertEquals("lowest duplicate did not retain identity in $table", duplicatedUuid, rows.getValue(2))
                assertNotEquals("later duplicate was not repaired in $table", duplicatedUuid, rows.getValue(3))
            }
        } finally {
            sqlite.close()
        }
    }

    @Test
    fun uniqueIndexesAndImmutabilityTriggersRejectIdentityCorruption() {
        val sqlite = version18Database()
        try {
            AppDatabase.MIGRATION_18_19.migrate(sqlite)

            (uuidTables + "color_presets").forEach { table ->
                val keptUuid = uuidsById(sqlite, table).getValue(1)
                assertTrue(
                    "duplicate UUID insert unexpectedly succeeded in $table",
                    runCatching {
                        if (table == "color_presets") {
                            sqlite.execSQL(
                                "INSERT INTO color_presets (id, uuid, name, colorsJson) " +
                                    "VALUES (99, '$keptUuid', 'duplicate', '[]')"
                            )
                        } else {
                            sqlite.execSQL(
                                "INSERT INTO `$table` (`id`, `uuid`, `name`) " +
                                    "VALUES (99, '$keptUuid', 'duplicate')"
                            )
                        }
                    }.isFailure,
                )
                assertTrue(
                    "UUID update unexpectedly succeeded in $table",
                    runCatching {
                        sqlite.execSQL("UPDATE `$table` SET `uuid` = 'changed' WHERE `id` = 1")
                    }.isFailure,
                )
                sqlite.execSQL("UPDATE `$table` SET `name` = 'edited' WHERE `id` = 1")
                assertEquals("edited", stringValue(sqlite, "SELECT name FROM `$table` WHERE id = 1"))
            }
        } finally {
            sqlite.close()
        }
    }

    private fun version18Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    uuidTables.forEach { table ->
                        db.execSQL(
                            "CREATE TABLE `$table` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL)"
                        )
                    }
                    db.execSQL(
                        "CREATE TABLE `color_presets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, `colorsJson` TEXT NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.also { db ->
            uuidTables.forEach { table ->
                db.execSQL("INSERT INTO `$table` (id, uuid, name) VALUES (1, '$existingUuid', 'valid')")
                db.execSQL("INSERT INTO `$table` (id, uuid, name) VALUES (2, '$duplicatedUuid', 'first')")
                db.execSQL("INSERT INTO `$table` (id, uuid, name) VALUES (3, '$duplicatedUuid', 'later')")
                db.execSQL("INSERT INTO `$table` (id, uuid, name) VALUES (4, '', 'empty')")
                db.execSQL("INSERT INTO `$table` (id, uuid, name) VALUES (5, '   ', 'whitespace')")
            }
            db.execSQL("INSERT INTO color_presets (id, name, colorsJson) VALUES (1, 'One', '[]')")
            db.execSQL("INSERT INTO color_presets (id, name, colorsJson) VALUES (2, 'Two', '[]')")
        }
    }

    private fun uuidsById(db: SupportSQLiteDatabase, table: String): Map<Long, String> = buildMap {
        db.query("SELECT id, uuid FROM `$table` ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) put(cursor.getLong(0), cursor.getString(1))
        }
    }

    private fun schemaObjectCount(db: SupportSQLiteDatabase, type: String, name: String): Int =
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?",
            arrayOf(type, name),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun stringValue(db: SupportSQLiteDatabase, query: String): String =
        db.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
}
