package com.datadragon.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the 23→24 migration on a real SQLite database: forms, entries,
 * calendars and Idea Logs are converted to permanent identities with every
 * answer, timestamp and unrelated column kept, and running it again changes
 * nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FieldIdentityDatabaseMigrationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun openV23(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE log_templates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                            "schemaJson TEXT NOT NULL, formMarkdown TEXT NOT NULL DEFAULT '', " +
                            "locked INTEGER NOT NULL DEFAULT 1, allowAppendedNotes INTEGER NOT NULL DEFAULT 0, " +
                            "automaticTimestamping INTEGER NOT NULL DEFAULT 0, sortTimestampLabel TEXT, " +
                            "sortNewestFirst INTEGER NOT NULL DEFAULT 1, integrateCalendar INTEGER NOT NULL DEFAULT 0, " +
                            "allowAdditionsFromForm INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE log_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "templateId INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT, " +
                            "valuesJson TEXT NOT NULL, marked INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE TABLE calendars (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "templateId INTEGER NOT NULL, position INTEGER NOT NULL, type TEXT NOT NULL, " +
                            "label TEXT NOT NULL, description TEXT NOT NULL, configJson TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE idea_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "uuid TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                            "fieldsJson TEXT NOT NULL, automaticTimestamping INTEGER NOT NULL, " +
                            "allowArchiving INTEGER NOT NULL, showEntireIdeaCard INTEGER NOT NULL, " +
                            "previewLines INTEGER NOT NULL, sortTimestampFieldId TEXT, sortNewestFirst INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE idea_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "ideaLogId INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT, " +
                            "valuesJson TEXT NOT NULL, marked INTEGER NOT NULL, archived INTEGER NOT NULL)"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    private fun one(db: SupportSQLiteDatabase, sql: String): List<String?> =
        db.query(sql).use { c ->
            assertTrue(c.moveToFirst())
            (0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }
        }

    @Test
    fun formsEntriesCalendarsAndIdeasGainIdentitiesWithEveryAnswerKept() {
        val db = openV23()
        val schema = """[{"label":"When","type":"DATE"},""" +
            """{"label":"Pet","type":"MULTIPLE","options":["Cow","Cat","Dog","Cat"]}]"""
        db.execSQL(
            "INSERT INTO log_templates (id, uuid, name, createdAt, schemaJson, formMarkdown, sortTimestampLabel) " +
                "VALUES (1, 'form-uuid', 'Pets', 300, ?, '# Pets', 'When')",
            arrayOf<Any?>(schema),
        )
        db.execSQL(
            "INSERT INTO log_entries (id, templateId, createdAt, valuesJson, marked) VALUES " +
                "(10, 1, '2026-10-01T09:00:00-05:00', '{\"When\":\"2026-10-01\",\"Pet\":[\"Cat\",\"Dog\"],\"__notes__\":\"n\"}', 1), " +
                "(11, 1, '2026-10-02T09:00:00-05:00', '{\"Pet\":[\"Hamster\"]}', 0)"
        )
        val calendar = CalendarConfigCodec.encode(CalendarConfig(sourceField = "Pet", matchOption = "Cat", dayTimestampField = "When"))
        db.execSQL(
            "INSERT INTO calendars (id, templateId, position, type, label, description, configJson) " +
                "VALUES (5, 1, 0, 'heat_map', 'Cats', '', ?)",
            arrayOf<Any?>(calendar),
        )
        db.execSQL(
            "INSERT INTO idea_logs VALUES (2, 'idea-uuid', 'Ideas', 1, " +
                "'[{\"id\":\"kind\",\"label\":\"Kind\",\"type\":\"DROPDOWN\",\"options\":[\"Home\",\"Work\"]}]', 0, 0, 0, 20, NULL, 1)"
        )
        db.execSQL("INSERT INTO idea_entries VALUES (20, 2, '2026-10-01T09:00:00-05:00', NULL, '{\"kind\":\"Work\"}', 0, 0)")

        AppDatabase.MIGRATION_23_24.migrate(db)

        val (uuid, schemaOut, sort, markdown) = one(db, "SELECT uuid, schemaJson, sortTimestampLabel, formMarkdown FROM log_templates")
        assertEquals("form-uuid", uuid)
        assertEquals("# Pets", markdown)
        val fields = json.decodeFromString(ListSerializer(FieldDef.serializer()), schemaOut!!)
        val (whenField, pet) = fields
        assertEquals(whenField.id, sort)
        assertEquals(listOf("Cow", "Cat", "Dog"), pet.activeChoices.map { it.label })
        assertEquals(listOf("Hamster"), pet.retiredChoices.map { it.label })

        val first = one(db, "SELECT valuesJson, createdAt, marked FROM log_entries WHERE id = 10")
        val values = EntryValues.decode(first[0]!!)
        assertEquals("Cat, Dog", EntryValues.displayValue(pet, values))
        assertEquals("2026-10-01", EntryValues.rawValue(values, whenField))
        assertEquals("n", EntryValues.notes(values))
        assertEquals("2026-10-01T09:00:00-05:00", first[1])
        assertEquals("1", first[2])
        val second = EntryValues.decode(one(db, "SELECT valuesJson FROM log_entries WHERE id = 11")[0]!!)
        assertEquals("Hamster", EntryValues.displayValue(pet, second))

        val config = CalendarConfigCodec.decode(one(db, "SELECT configJson FROM calendars")[0]!!)
        assertEquals(pet.id, config.sourceField)
        assertEquals(whenField.id, config.dayTimestampField)
        assertEquals(pet.choices[1].id, config.matchOption)

        val kind = json.decodeFromString(
            ListSerializer(IdeaFieldDef.serializer()),
            one(db, "SELECT fieldsJson FROM idea_logs")[0]!!,
        ).single()
        assertEquals(listOf("Home", "Work"), kind.choices.map { it.label })
        val idea = IdeaValues.decode(one(db, "SELECT valuesJson FROM idea_entries")[0]!!)
        assertEquals("Work", IdeaValues.displayValue(idea, kind))

        // A second run is a no-op: no identity changes.
        val before = listOf(
            one(db, "SELECT schemaJson, sortTimestampLabel FROM log_templates"),
            one(db, "SELECT valuesJson FROM log_entries WHERE id = 10"),
            one(db, "SELECT configJson FROM calendars"),
            one(db, "SELECT fieldsJson FROM idea_logs"),
        )
        AppDatabase.MIGRATION_23_24.migrate(db)
        val after = listOf(
            one(db, "SELECT schemaJson, sortTimestampLabel FROM log_templates"),
            one(db, "SELECT valuesJson FROM log_entries WHERE id = 10"),
            one(db, "SELECT configJson FROM calendars"),
            one(db, "SELECT fieldsJson FROM idea_logs"),
        )
        assertEquals(before, after)
        assertFalse(FieldIdentityMigration.formNeedsMigration(after[0][0]!!))
        db.close()
    }

    @Test
    fun anUnreadableFormIsLeftUntouched() {
        val db = openV23()
        db.execSQL("INSERT INTO log_templates (id, uuid, name, createdAt, schemaJson) VALUES (1, 'u', 'Broken', 1, 'not json')")
        db.execSQL("INSERT INTO log_entries (id, templateId, createdAt, valuesJson) VALUES (1, 1, 'c', '{\"A\":\"b\"}')")

        AppDatabase.MIGRATION_23_24.migrate(db)

        assertEquals("not json", one(db, "SELECT schemaJson FROM log_templates")[0])
        assertEquals("{\"A\":\"b\"}", one(db, "SELECT valuesJson FROM log_entries")[0])
        db.close()
    }
}
