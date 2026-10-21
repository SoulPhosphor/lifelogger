package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Convert Time Stamps to Date and Time Field" fills only empty Date & Time
 * values from each entry's timestamp and leaves everything else as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimestampConversionTest {

    @Test
    fun fillsAnEmptyFieldWithTheTimestampsDateHourAndMinute() {
        val filled = TimestampConversion.fill("""{"note":"hi"}""", "2026-06-28T14:14:37-05:00", "when")
        assertEquals("""{"note":"hi","when":"2026-06-28T14:14"}""", filled)
    }

    @Test
    fun treatsABlankValueAsEmpty() {
        assertEquals(
            """{"when":"2026-06-28T09:05"}""",
            TimestampConversion.fill("""{"when":""}""", "2026-06-28T09:05:00Z", "when"),
        )
    }

    @Test
    fun neverReplacesAValueAlreadyEntered() {
        assertNull(TimestampConversion.fill("""{"when":"2025-01-01T08:00"}""", "2026-06-28T14:14:00-05:00", "when"))
    }

    @Test
    fun leavesUnreadableDataAlone() {
        assertNull(TimestampConversion.fill("not json", "2026-06-28T14:14:00-05:00", "when"))
        assertNull(TimestampConversion.fill("{}", "not a time", "when"))
    }

    @Test
    fun convertsALockedFormsEntriesAndKeepsTheirTimestampsAndOtherValues() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val templateId = db.logTemplateDao().insert(
                LogTemplate(uuid = "form", name = "Odor Log", createdAt = 1, schemaJson = "[]", locked = true),
            )
            val entryDao = db.logEntryDao()
            val emptyId = entryDao.insert(
                LogEntry(templateId = templateId, createdAt = "2026-06-28T14:14:37-05:00", valuesJson = """{"note":"a"}""", marked = true),
            )
            val filledId = entryDao.insert(
                LogEntry(templateId = templateId, createdAt = "2026-06-29T10:00:00-05:00", updatedAt = "2026-06-30T10:00:00-05:00", valuesJson = """{"when":"2025-01-01T08:00"}"""),
            )

            assertEquals(1, TimestampConversion.convert(entryDao, templateId, "when"))

            val empty = entryDao.getById(emptyId)!!
            assertEquals("""{"note":"a","when":"2026-06-28T14:14"}""", empty.valuesJson)
            assertEquals("2026-06-28T14:14:37-05:00", empty.createdAt)
            assertNull(empty.updatedAt)
            assertEquals(true, empty.marked)
            val filled = entryDao.getById(filledId)!!
            assertEquals("""{"when":"2025-01-01T08:00"}""", filled.valuesJson)
            assertEquals("2026-06-30T10:00:00-05:00", filled.updatedAt)
            assertEquals(true, db.logTemplateDao().getById(templateId)!!.locked)
        } finally {
            db.close()
        }
    }
}
