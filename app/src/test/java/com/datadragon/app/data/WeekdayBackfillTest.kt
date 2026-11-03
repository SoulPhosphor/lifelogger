package com.datadragon.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeekdayBackfillTest {
    private val days = Weekday.newChoices().map { if (it.weekday == 1) it.copy(label = "Munday") else it }
    private val weekday = FieldDef(id = StableUuid.createNew(), label = "Days", type = FieldType.DAY_OF_WEEK,
        choices = days, weekday = WeekdaySettings(weekdayIds = Weekday.identities(days)))
    private val date = FieldDef(id = StableUuid.createNew(), label = "Custom Date", type = FieldType.DATE)
    private val datetime = FieldDef(id = StableUuid.createNew(), label = "Custom Timestamp", type = FieldType.DATETIME)
    private val fields = listOf(date, datetime, weekday)
    private val created = "2026-11-01T23:30:00-06:00" // Sunday, even though UTC is Monday.
    private fun day(number: Int) = days.single { it.weekday == number }.id
    private fun filled(raw: String, source: String? = null, selectedFields: List<FieldDef> = fields): List<String> =
        Weekday.selected(EntryValues.decode(WeekdayBackfill.fillEntry(raw, created, selectedFields, setOf(weekday.id), source)!!)[weekday.id])

    @Test fun hierarchyUsesChosenDateOrDateTimeOtherwiseTheCreationDateWithItsOwnOffset() {
        assertEquals(listOf(day(7)), filled("{}"))
        assertEquals(listOf(day(1)), filled("""{"${date.id}":"2026-11-02"}""", date.id))
        assertEquals(listOf(day(2)), filled("""{"${datetime.id}":"2026-11-03T00:01"}""", datetime.id))
        assertEquals(listOf(day(7)), filled("{}", date.id))
        assertEquals(listOf(day(7)), filled("""{"${date.id}":""}""", date.id))
        assertEquals(listOf(day(7)), filled("""{"${date.id}":"bad"}""", date.id))
        // An ordinary date field does not replace the timestamp unless selected.
        assertEquals(listOf(day(7)), filled("""{"${date.id}":"2026-11-02"}"""))
    }

    @Test fun backfillRespectsOptOutAndExistingAnswersAndMultipleDefaults() {
        assertNull(WeekdayBackfill.fillEntry("{}", created, fields, emptySet(), null))
        assertNull(WeekdayBackfill.fillEntry("{}", created, listOf(weekday.copy(weekday = weekday.weekday.copy(defaultEnteredDay = false))), setOf(weekday.id), null))
        val existing = EntryValues.encode(mapOf(weekday.id to EntryValues.stringArray(listOf(day(3)))))
        assertNull(WeekdayBackfill.fillEntry(existing, created, fields, setOf(weekday.id), null))
        val multi = weekday.copy(choices = days.map { it.copy(preselected = it.weekday == 1) }, weekday = weekday.weekday.copy(onlyOne = false))
        assertEquals(setOf(day(1), day(7)), filled("{}", selectedFields = listOf(multi)).toSet())
        assertNull(WeekdayBackfill.fillEntry("invalid json", created, fields, setOf(weekday.id), null))
    }

    @Test fun defaultTimestampSwitchAndTimestampEditsAdjustOnlyAutomaticWeekdayFields() {
        val old = mapOf(date.id to JsonPrimitive("2026-11-02"), datetime.id to JsonPrimitive("2026-11-03T04:00"),
            weekday.id to EntryValues.stringArray(listOf(day(7))))
        val chosen = WeekdayBackfill.adjust(old, created, fields, date.id, old, previousTimestampFieldId = null)
        assertEquals(listOf(day(1)), Weekday.selected(chosen[weekday.id]))
        val otherSource = WeekdayBackfill.adjust(chosen, created, fields, datetime.id, chosen, previousTimestampFieldId = date.id)
        assertEquals(listOf(day(2)), Weekday.selected(otherSource[weekday.id]))
        val edited = otherSource + (datetime.id to JsonPrimitive("2026-11-04T05:00"))
        assertEquals(listOf(day(3)), Weekday.selected(WeekdayBackfill.adjust(edited, created, fields, datetime.id, otherSource)[weekday.id]))
        val cleared = edited - datetime.id
        assertEquals(listOf(day(7)), Weekday.selected(WeekdayBackfill.adjust(cleared, created, fields, datetime.id, edited)[weekday.id]))
        val off = fields.map { if (it.id == weekday.id) it.copy(weekday = it.weekday.copy(defaultEnteredDay = false)) else it }
        assertEquals(old, WeekdayBackfill.adjust(old, created, off, date.id, old, previousTimestampFieldId = null))
        // Unrelated edits never reset a manual weekday selection.
        assertEquals(old, WeekdayBackfill.adjust(old, created, fields, null, old))
        val multi = fields.map { if (it.id == weekday.id) it.copy(weekday = it.weekday.copy(onlyOne = false)) else it }
        val multipleValues = old + (weekday.id to EntryValues.stringArray(listOf(day(7), day(4))))
        assertEquals(setOf(day(1), day(4)), Weekday.selected(WeekdayBackfill.adjust(multipleValues, created, multi, date.id, multipleValues, previousTimestampFieldId = null)[weekday.id]).toSet())
    }

    @Test fun savedBackfillPreservesMetadataAndSurvivesPortableBackup() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val form = LogTemplate(uuid = StableUuid.createNew(), name = "Form", createdAt = 1,
                schemaJson = FormMarkdownParser.encodeFields(fields), sortTimestampFieldId = date.id)
            val id = db.logTemplateDao().insert(form)
            val original = LogEntry(templateId = id, createdAt = created, updatedAt = "2026-11-05T12:00:00Z", marked = true,
                valuesJson = EntryValues.encode(mapOf(date.id to JsonPrimitive("2026-11-02"), EntryValues.NOTES_KEY to JsonPrimitive("Keep me"))))
            val entryId = db.logEntryDao().insert(original)
            WeekdayBackfill.apply(db.logEntryDao(), id, fields, setOf(weekday.id), date.id)
            val entry = db.logEntryDao().getById(entryId)!!
            assertEquals(original.copy(id = entryId, valuesJson = entry.valuesJson), entry)
            assertEquals(listOf(day(1)), Weekday.selected(EntryValues.decode(entry.valuesJson)[weekday.id]))
            assertEquals(JsonPrimitive("Keep me"), EntryValues.decode(entry.valuesJson)[EntryValues.NOTES_KEY])
            val payload = BackupPayload(forms = listOf(BackupCodec.logOf(db.logTemplateDao().getById(id)!!, listOf(entry))))
            val backup = BackupFile.single("2026-11-05T12:00:00Z", BackupCategory.FORMS, payload)
            val restored = BackupCodec.decode(BackupCodec.encode(backup))
            BackupRestoreValidator.validate(restored)
            assertEquals(payload, restored.payload)
            assertEquals("Munday", EntryValues.displayValue(weekday, EntryValues.decode(entry.valuesJson)))
            // Retrying is idempotent.
            WeekdayBackfill.apply(db.logEntryDao(), id, fields, setOf(weekday.id), date.id)
            assertEquals(entry, db.logEntryDao().getById(entryId))
        } finally { db.close() }
    }
}
