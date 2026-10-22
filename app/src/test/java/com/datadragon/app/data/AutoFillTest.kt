package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Auto Fill takes each matching part from its source and the missing part from
 * the entry's timestamp, and the one-time conversion fills only empty values.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoFillTest {

    private val stamp = LocalDateTime.of(2026, 6, 28, 14, 14, 37)

    private val start = FieldDef(id = "start", label = "Start", type = FieldType.DATETIME)
    private val day = FieldDef(id = "day", label = "Day", type = FieldType.DATE)
    private val clock = FieldDef(id = "clock", label = "Clock", type = FieldType.TIME)
    private val note = FieldDef(id = "note", label = "Note", type = FieldType.TEXT)

    @Test
    fun theTimestampFillsEachTypeWithItsParts() {
        assertEquals("2026-06-28", AutoFill.value(FieldType.DATE, null, null, stamp))
        assertEquals("14:14", AutoFill.value(FieldType.TIME, null, null, stamp))
        assertEquals("2026-06-28T14:14", AutoFill.value(FieldType.DATETIME, null, null, stamp))
    }

    @Test
    fun aDateAndTimeSourceDonatesTheMatchingPart() {
        assertEquals("2025-01-02", AutoFill.value(FieldType.DATE, FieldType.DATETIME, "2025-01-02T08:30", stamp))
        assertEquals("08:30", AutoFill.value(FieldType.TIME, FieldType.DATETIME, "2025-01-02T08:30", stamp))
        assertEquals("2025-01-02T08:30", AutoFill.value(FieldType.DATETIME, FieldType.DATETIME, "2025-01-02T08:30", stamp))
    }

    @Test
    fun aPartialSourceTakesTheMissingPartFromTheTimestamp() {
        assertEquals("2025-01-02T14:14", AutoFill.value(FieldType.DATETIME, FieldType.DATE, "2025-01-02", stamp))
        assertEquals("2026-06-28T08:30", AutoFill.value(FieldType.DATETIME, FieldType.TIME, "08:30", stamp))
        assertEquals("2025-01-02", AutoFill.value(FieldType.DATE, FieldType.DATE, "2025-01-02", stamp))
        assertEquals("08:30", AutoFill.value(FieldType.TIME, FieldType.TIME, "08:30", stamp))
    }

    @Test
    fun anEmptyOrUnreadableSourceFillsNothing() {
        assertNull(AutoFill.value(FieldType.DATETIME, FieldType.DATE, null, stamp))
        assertNull(AutoFill.value(FieldType.DATETIME, FieldType.DATE, "  ", stamp))
        assertNull(AutoFill.value(FieldType.TIME, FieldType.TIME, "not a time", stamp))
    }

    @Test
    fun theDropDownOffersEveryFieldWithAMatchingPartAndNoLoops() {
        val fields = listOf(start, day, clock, note)
        assertEquals(listOf("start"), AutoFill.sourceFields(day, fields).map { it.id })
        assertEquals(listOf("start"), AutoFill.sourceFields(clock, fields).map { it.id })
        assertEquals(listOf("day", "clock"), AutoFill.sourceFields(start, fields).map { it.id })
        // Day fills from Start, so Start can no longer fill from Day.
        val chained = listOf(start, day.copy(autoFillSource = "start"), clock, note)
        assertEquals(listOf("clock"), AutoFill.sourceFields(start, chained).map { it.id })
    }

    @Test
    fun conversionFillsOnlyEmptyValuesInSourceOrder() {
        val fields = listOf(
            start.copy(autoFillSource = AutoFill.TIMESTAMP),
            clock.copy(autoFillSource = "start"),
            day.copy(autoFillSource = "start"),
        )
        val targets = setOf("start", "clock", "day")
        assertEquals(
            """{"day":"2025-01-01","start":"2026-06-28T14:14","clock":"14:14"}""",
            AutoFill.fillEntry("""{"day":"2025-01-01"}""", "2026-06-28T14:14:37-05:00", fields, targets),
        )
        // Nothing empty to fill, or nothing readable: unchanged.
        assertNull(
            AutoFill.fillEntry("""{"start":"2025-01-01T08:00","clock":"09:00","day":"2025-01-01"}""", "2026-06-28T14:14:37-05:00", fields, targets),
        )
        assertNull(AutoFill.fillEntry("not json", "2026-06-28T14:14:37-05:00", fields, targets))
        assertNull(AutoFill.fillEntry("{}", "not a time", fields, targets))
        // Only the requested fields are filled.
        assertEquals(
            """{"start":"2026-06-28T14:14"}""",
            AutoFill.fillEntry("{}", "2026-06-28T14:14:37-05:00", fields, setOf("start")),
        )
    }

    @Test
    fun convertKeepsEntryTimestampsAndWorksOnALockedForm() = runBlocking {
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
                LogEntry(templateId = templateId, createdAt = "2026-06-29T10:00:00-05:00", updatedAt = "2026-06-30T10:00:00-05:00", valuesJson = """{"start":"2025-01-01T08:00"}"""),
            )
            val fields = listOf(start.copy(autoFillSource = AutoFill.TIMESTAMP), note)

            assertEquals(1, AutoFill.convert(entryDao, templateId, fields, setOf("start")))

            val empty = entryDao.getById(emptyId)!!
            assertEquals("""{"note":"a","start":"2026-06-28T14:14"}""", empty.valuesJson)
            assertEquals("2026-06-28T14:14:37-05:00", empty.createdAt)
            assertNull(empty.updatedAt)
            assertTrue(empty.marked)
            val filled = entryDao.getById(filledId)!!
            assertEquals("""{"start":"2025-01-01T08:00"}""", filled.valuesJson)
            assertEquals("2026-06-30T10:00:00-05:00", filled.updatedAt)
            assertTrue(db.logTemplateDao().getById(templateId)!!.locked)
        } finally {
            db.close()
        }
    }

    @Test
    fun formMarkdownRoundTripsAutoFillByLabel() {
        val fields = listOf(start, clock.copy(autoFillSource = "start"), day.copy(autoFillSource = AutoFill.TIMESTAMP))
        val markdown = FormMarkdownGenerator.generate("Times", fields)
        assertTrue(markdown.contains("auto_fill: Start"))
        assertTrue(markdown.contains("auto_fill: timestamp"))
        val parsed = FormMarkdownParser.parse(markdown).fields
        assertNull(parsed[0].autoFillSource)
        assertEquals(parsed[0].id, parsed[1].autoFillSource)
        assertEquals(AutoFill.TIMESTAMP, parsed[2].autoFillSource)
    }

    @Test
    fun aFieldLabeledTimestampRoundTripsAsThatField() {
        val stampField = FieldDef(id = "ts", label = "Timestamp", type = FieldType.DATETIME)
        val fields = listOf(stampField, clock.copy(autoFillSource = "ts"), day.copy(autoFillSource = AutoFill.TIMESTAMP))
        val markdown = FormMarkdownGenerator.generate("Times", fields)
        assertTrue(markdown.contains("auto_fill_field: Timestamp"))
        val parsed = FormMarkdownParser.parse(markdown).fields
        assertEquals(parsed[0].id, parsed[1].autoFillSource)
        assertEquals(AutoFill.TIMESTAMP, parsed[2].autoFillSource)
    }

    @Test
    fun formMarkdownIgnoresAnUnsuitableSource() {
        val result = FormMarkdownParser.parse("## Note\ntype: text\n\n## Day\ntype: date\nauto_fill: Note\n")
        assertNull(result.fields.single { it.label == "Day" }.autoFillSource)
        assertTrue(result.issues.any { it.contains("auto_fill") })
    }

    @Test
    fun autoFillNeedsBackupVersion11() {
        val schema = FormMarkdownParser.encodeFields(listOf(start.copy(autoFillSource = AutoFill.TIMESTAMP)))
        val text = BackupCodec.encode(
            BackupFile.single(
                "2026-10-21T12:00:00Z",
                BackupCategory.FORMS,
                BackupPayload(forms = listOf(BackupCodec.logOf(LogTemplate(uuid = "f", name = "F", createdAt = 1, schemaJson = schema, locked = false), emptyList()))),
            ),
        )
        assertEquals(BackupFile.VERSION, BackupCodec.decode(text).version)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(text.replace("\"version\": ${BackupFile.VERSION}", "\"version\": 10"))
        }
        // Without Auto Fill the schema encodes exactly as before.
        assertFalse(FormMarkdownParser.encodeFields(listOf(start)).contains("autoFillSource"))
    }
}
