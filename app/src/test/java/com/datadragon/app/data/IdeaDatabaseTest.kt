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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real-database tests for Ideas (Robolectric provides SQLite): the Home summary
 * excluding archived ideas, archive/unarchive round-tripping, and the 11→12
 * migration adding the Ideas tables without disturbing existing forms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdeaDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var logDao: IdeaLogDao
    private lateinit var entryDao: IdeaEntryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logDao = db.ideaLogDao()
        entryDao = db.ideaEntryDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun newLog(name: String = "Ideas"): Long =
        logDao.insert(IdeaLog(name = name, createdAt = 1, fieldsJson = "[]"))

    private suspend fun newIdea(logId: Long, at: String, archived: Boolean = false): Long =
        entryDao.insert(
            IdeaEntry(
                ideaLogId = logId,
                createdAt = at,
                valuesJson = "{}",
                archived = archived,
            )
        )

    @Test
    fun homeSummaryCountsOnlyActiveIdeas() = runBlocking {
        val logId = newLog()
        newIdea(logId, "2026-01-01T09:00:00-05:00")
        newIdea(logId, "2026-01-02T09:00:00-05:00")
        newIdea(logId, "2026-01-09T09:00:00-05:00", archived = true)

        val summary = entryDao.observeActiveSummaries().first().single()

        assertEquals(logId, summary.ideaLogId)
        assertEquals(2, summary.count)
        // The archived idea is the newest, and must not become the "last entry".
        assertEquals("2026-01-02T09:00:00-05:00", summary.lastCreatedAt)
    }

    @Test
    fun archivingAndUnarchivingKeepsTheIdeaAndItsValues() = runBlocking {
        val logId = newLog()
        val ideaId = entryDao.insert(
            IdeaEntry(
                ideaLogId = logId,
                createdAt = "2026-01-01T09:00:00-05:00",
                valuesJson = """{"f1":"an idea"}""",
            )
        )

        entryDao.setArchived(ideaId, true)
        assertEquals(1, entryDao.archivedCount(logId))
        val archived = entryDao.getById(ideaId)!!
        assertTrue(archived.archived)
        assertEquals("""{"f1":"an idea"}""", archived.valuesJson)

        entryDao.setArchived(ideaId, false)
        assertEquals(0, entryDao.archivedCount(logId))
        assertEquals(false, entryDao.getById(ideaId)!!.archived)
    }

    @Test
    fun markingWorksWhileArchived() = runBlocking {
        val logId = newLog()
        val ideaId = newIdea(logId, "2026-01-01T09:00:00-05:00", archived = true)

        entryDao.setMarked(ideaId, true)

        val entry = entryDao.getById(ideaId)!!
        assertTrue(entry.marked)
        assertTrue(entry.archived)
    }

    @Test
    fun deletingAnIdeaLogTakesItsIdeasWithIt() = runBlocking {
        val keep = newLog("Keep")
        val drop = newLog("Drop")
        newIdea(keep, "2026-01-01T09:00:00-05:00")
        newIdea(drop, "2026-01-01T09:00:00-05:00")
        newIdea(drop, "2026-01-02T09:00:00-05:00", archived = true)

        entryDao.deleteForLog(drop)
        logDao.delete(logDao.getById(drop)!!)

        assertEquals(1, entryDao.observeForLog(keep).first().size)
        assertTrue(entryDao.observeForLog(drop).first().isEmpty())
        assertEquals(listOf("Keep"), logDao.observeAll().first().map { it.name })
    }

    @Test
    fun migration11To12AddsTheIdeasTablesAndLeavesFormsAlone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
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

        AppDatabase.MIGRATION_11_12.migrate(sqlite)

        // The existing form is untouched.
        sqlite.query("SELECT uuid, name FROM log_templates").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("stable-id", cursor.getString(0))
            assertEquals("Mood", cursor.getString(1))
        }

        // And the Ideas tables now exist and accept rows.
        sqlite.execSQL(
            "INSERT INTO idea_logs (uuid, name, createdAt, fieldsJson, automaticTimestamping, " +
                "allowArchiving, showEntireIdeaCard, previewLines, sortNewestFirst) " +
                "VALUES ('idea-uuid', 'Sparks', 400, '[]', 0, 0, 0, 20, 1)"
        )
        sqlite.execSQL(
            "INSERT INTO idea_entries (ideaLogId, createdAt, valuesJson, marked, archived) " +
                "VALUES (1, '2026-01-01T09:00:00-05:00', '{}', 0, 0)"
        )
        sqlite.query("SELECT name, previewLines FROM idea_logs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Sparks", cursor.getString(0))
            assertEquals(20, cursor.getInt(1))
        }
        sqlite.close()
    }
}
