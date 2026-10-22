package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locked Log is reversible; Never Allow Form Unlocking keeps a form locked for
 * good; and the database upgrade and backups keep every form's real lock state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FormLockingTest {

    private fun form(locked: Boolean, neverAllowUnlocking: Boolean = false) = LogTemplate(
        uuid = "form",
        name = "Odor Log",
        createdAt = 1,
        schemaJson = "[]",
        locked = locked,
        neverAllowUnlocking = neverAllowUnlocking,
    )

    private fun <T> withDb(block: suspend (AppDatabase) -> T): T = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try { block(db) } finally { db.close() }
    }

    @Test
    fun lockingTurnsOnAndOffRepeatedly() = withDb { db ->
        val dao = db.logTemplateDao()
        val id = dao.insert(form(locked = true))
        repeat(2) {
            dao.setLocked(id, false)
            assertFalse(dao.getById(id)!!.locked)
            dao.setLocked(id, true)
            assertTrue(dao.getById(id)!!.locked)
        }
        dao.setLocked(id, false)
        assertFalse(dao.getById(id)!!.locked)
        assertEquals("form", dao.getById(id)!!.uuid)
    }

    @Test
    fun aPermanentlyLockedFormCanNeverBeUnlocked() = withDb { db ->
        val dao = db.logTemplateDao()
        val id = dao.insert(form(locked = true, neverAllowUnlocking = true))
        dao.setLocked(id, false)
        assertTrue(dao.getById(id)!!.locked)
        assertTrue(dao.getById(id)!!.neverAllowUnlocking)
    }

    @Test
    fun editingTimestampingOrAddingADateTimeFieldLeavesTheLockAlone() = withDb { db ->
        val dao = db.logTemplateDao()
        val withDateTime = FormMarkdownParser.encodeFields(
            listOf(FieldDef(id = "when", label = "When", type = FieldType.DATETIME, required = true, defaultNow = true)),
        )
        listOf(true, false).forEach { locked ->
            val id = dao.insert(form(locked = locked).copy(uuid = "form-$locked", automaticTimestamping = true))
            dao.updateForm(id, "Odor Log", withDateTime, "", false, null, true, false, false, false)
            assertEquals(locked, dao.getById(id)!!.locked)
            assertFalse(dao.getById(id)!!.automaticTimestamping)
        }
    }

    @Test
    fun migration26To27KeepsEachFormsLockStateAndTurnsThePermanentLockOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE log_templates (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, " +
                            "createdAt INTEGER NOT NULL, schemaJson TEXT NOT NULL, " +
                            "formMarkdown TEXT NOT NULL, " +
                            "locked INTEGER NOT NULL DEFAULT 1, " +
                            "allowAppendedNotes INTEGER NOT NULL DEFAULT 0, " +
                            "automaticTimestamping INTEGER NOT NULL DEFAULT 0, " +
                            "sortTimestampLabel TEXT, " +
                            "sortNewestFirst INTEGER NOT NULL DEFAULT 1, " +
                            "integrateCalendar INTEGER NOT NULL DEFAULT 0, " +
                            "allowAdditionsFromForm INTEGER NOT NULL DEFAULT 0, " +
                            "advancedSearchEnabled INTEGER NOT NULL DEFAULT 0)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val sqlite = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
        sqlite.execSQL(
            "INSERT INTO log_templates (uuid, name, createdAt, schemaJson, formMarkdown, locked) VALUES " +
                "('locked-id', 'Locked', 1, '[]', '', 1), ('open-id', 'Open', 2, '[]', '', 0)"
        )

        AppDatabase.MIGRATION_26_27.migrate(sqlite)
        AppDatabase.MIGRATION_26_27.migrate(sqlite)

        sqlite.query("SELECT uuid, locked, neverAllowUnlocking FROM log_templates ORDER BY id").use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals("locked-id", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.moveToNext())
            assertEquals("open-id", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
        // After the upgrade an existing form unlocks normally.
        sqlite.execSQL(
            "UPDATE log_templates SET locked = CASE WHEN neverAllowUnlocking = 1 THEN 1 ELSE 0 END " +
                "WHERE uuid = 'locked-id'"
        )
        sqlite.query("SELECT locked FROM log_templates WHERE uuid = 'locked-id'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        sqlite.close()
    }

    private fun formFile(template: LogTemplate): String = BackupCodec.encode(
        BackupFile.single(
            "2026-10-21T12:00:00Z",
            BackupCategory.FORMS,
            BackupPayload(forms = listOf(BackupCodec.logOf(template, emptyList()))),
        ),
    )

    @Test
    fun thePermanentLockRoundTripsAsVersion11AndOlderVersionsRejectIt() {
        val text = formFile(form(locked = true, neverAllowUnlocking = true))
        val decoded = BackupCodec.decode(text)
        assertEquals(BackupFile.VERSION, decoded.version)
        val restored = BackupCodec.templateOf(decoded.logs.single())
        assertTrue(restored.locked)
        assertTrue(restored.neverAllowUnlocking)
        assertEquals("form", restored.uuid)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 10"))
        }
    }

    @Test
    fun anOrdinaryFormEncodesWithoutThePermanentLockAndKeepsItsLockState() {
        listOf(true, false).forEach { locked ->
            val text = formFile(form(locked = locked))
            assertFalse(text.contains("neverAllowUnlocking"))
            // A version-10 file (written before the option) restores the same way.
            val asVersion10 = text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 10")
            listOf(text, asVersion10).forEach { file ->
                val restored = BackupCodec.templateOf(BackupCodec.decode(file).logs.single())
                assertEquals(locked, restored.locked)
                assertFalse(restored.neverAllowUnlocking)
            }
        }
    }

    @Test
    fun restoreKeepsThePermanentLockAndItsLockState() = withDb { db ->
        var preferences = BackupPortablePreferences.defaults()
        val repository = BackupRepository(db, { preferences }, "test", { preferences = it })
        db.logTemplateDao().insert(form(locked = true, neverAllowUnlocking = true))
        db.logTemplateDao().insert(form(locked = false).copy(uuid = "open"))
        val snapshot = BackupCodec.decode(BackupCodec.encode(repository.buildFull()))
        repository.restore(snapshot, RestoreMode.REPLACE)
        val permanent = db.logTemplateDao().getByUuid("form")!!
        assertTrue(permanent.locked)
        assertTrue(permanent.neverAllowUnlocking)
        val open = db.logTemplateDao().getByUuid("open")!!
        assertFalse(open.locked)
        assertFalse(open.neverAllowUnlocking)
    }
}
