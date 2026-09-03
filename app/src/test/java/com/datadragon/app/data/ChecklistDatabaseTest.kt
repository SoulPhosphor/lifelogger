package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-database tests (Robolectric provides SQLite): the draft column's behavior
 * on Home, finalize/discard, recovery lookup, and the 7→8 migration preserving
 * existing lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChecklistDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ChecklistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.checklistDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun draftsAreExcludedFromTheHomeList() = runBlocking {
        dao.insertChecklist(Checklist(name = "Saved", createdAt = 1, draft = false))
        dao.insertChecklist(Checklist(name = "Draft", createdAt = 2, draft = true))

        val visible = dao.observeChecklists().first().map { it.name }
        assertEquals(listOf("Saved"), visible)
    }

    @Test
    fun finalizingADraftMakesItAppearOnHome() = runBlocking {
        val id = dao.insertChecklist(Checklist(name = "New", createdAt = 1, draft = true))
        assertTrue(dao.observeChecklists().first().isEmpty())

        dao.finalizeChecklist(id)

        assertEquals(listOf("New"), dao.observeChecklists().first().map { it.name })
    }

    @Test
    fun deleteChecklistWithItemsRemovesTheListAndItsItems() = runBlocking {
        val id = dao.insertChecklist(Checklist(name = "Trip", createdAt = 1, draft = true))
        dao.insertItem(ChecklistItem(checklistId = id, text = "Tent", position = 0))
        dao.insertItem(ChecklistItem(checklistId = id, text = "Stove", position = 1))

        dao.deleteChecklistWithItems(id)

        assertNull(dao.getChecklist(id))
        assertTrue(dao.getItemsOnce(id).isEmpty())
    }

    @Test
    fun mostRecentDraftReturnsOnlyTheLatestDraft() = runBlocking {
        dao.insertChecklist(Checklist(name = "Saved", createdAt = 10, draft = false))
        dao.insertChecklist(Checklist(name = "Old draft", createdAt = 1, draft = true))
        dao.insertChecklist(Checklist(name = "New draft", createdAt = 5, draft = true))

        assertEquals("New draft", dao.mostRecentDraft()?.name)
    }

    @Test
    fun migration7To8PreservesExistingListsAndDefaultsToNotDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // The v7 shape of the checklists table — no draft column yet.
                    db.execSQL(
                        "CREATE TABLE checklists (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL("INSERT INTO checklists (name, createdAt) VALUES ('Groceries', 100)")

        AppDatabase.MIGRATION_7_8.migrate(sqlite)

        sqlite.query("SELECT name, draft FROM checklists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Groceries", cursor.getString(0)) // existing list preserved
            assertEquals(0, cursor.getInt(1))              // defaults to a normal saved list
        }
        sqlite.close()
    }

    @Test
    fun migration8To9BackfillsADistinctUuidForEveryExistingListAndLog() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // The v8 shape: draft exists, uuid does not.
                    db.execSQL(
                        "CREATE TABLE checklists (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                            "draft INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE log_templates (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "name TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                            "schemaJson TEXT NOT NULL, formMarkdown TEXT NOT NULL DEFAULT '', " +
                            "locked INTEGER NOT NULL DEFAULT 1, " +
                            "allowAppendedNotes INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL("INSERT INTO checklists (name, createdAt) VALUES ('Groceries', 100)")
        sqlite.execSQL("INSERT INTO checklists (name, createdAt) VALUES ('Music', 200)")
        sqlite.execSQL(
            "INSERT INTO log_templates (name, createdAt, schemaJson) VALUES ('Mood', 300, '[]')"
        )

        AppDatabase.MIGRATION_8_9.migrate(sqlite)

        // Every existing row keeps its data and gains its own non-empty uuid.
        val listUuids = mutableSetOf<String>()
        sqlite.query("SELECT name, uuid FROM checklists ORDER BY createdAt").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Groceries", cursor.getString(0))
            listUuids.add(cursor.getString(1))
            assertTrue(cursor.moveToNext())
            assertEquals("Music", cursor.getString(0))
            listUuids.add(cursor.getString(1))
        }
        assertEquals(2, listUuids.size)            // distinct per row, not one shared value
        assertTrue(listUuids.none { it.isEmpty() })

        sqlite.query("SELECT name, uuid FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Mood", cursor.getString(0))
            assertTrue(cursor.getString(1).isNotEmpty())
        }
        sqlite.close()
    }

    @Test
    fun migration9To10PreservesFormsAndDefaultsTimestampSettingsOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE log_templates (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, schemaJson TEXT NOT NULL, " +
                            "formMarkdown TEXT NOT NULL DEFAULT '', " +
                            "locked INTEGER NOT NULL DEFAULT 1, " +
                            "allowAppendedNotes INTEGER NOT NULL DEFAULT 0)"
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

        AppDatabase.MIGRATION_9_10.migrate(sqlite)

        sqlite.query(
            "SELECT uuid, name, automaticTimestamping, sortTimestampLabel FROM log_templates"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Mood", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }
        sqlite.close()
    }
}
