package com.datadragon.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ClickerCard
import com.datadragon.app.data.ClickerField
import com.datadragon.app.data.ClickerFieldType
import com.datadragon.app.data.ClickerLog
import com.datadragon.app.data.ClickerValues
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import com.datadragon.app.data.ReportBuilder
import com.datadragon.app.data.StableUuid
import com.datadragon.app.export.ClickerExport
import com.datadragon.app.export.LogExport
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Multiple Selection on the Forms and Clicker Data Main screens: the count box,
 * toggling, Select Newest / Oldest X by each record's timestamp, Copy as the
 * `.txt` report, exporting only the selection, and deleting only the selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardSelectionTest {

    // --- Count box ---

    @Test
    fun countBoxHoldsEnoughDigitsForTheCardCount() {
        assertEquals(1, CardSelection.digitsFor(0))
        assertEquals(1, CardSelection.digitsFor(8))
        assertEquals(2, CardSelection.digitsFor(85))
        assertEquals(3, CardSelection.digitsFor(274))
        assertEquals(4, CardSelection.digitsFor(1500))
    }

    @Test
    fun countBoxKeepsDigitsOnlyAndOkayNeedsAPositiveNumber() {
        assertEquals("12", CardSelection.countInput("1a2-3", 2))
        assertEquals("5", CardSelection.countInput("-5", 3))
        assertNull(CardSelection.parseCount(""))
        assertNull(CardSelection.parseCount("0"))
        assertNull(CardSelection.parseCount("000"))
        assertNull(CardSelection.parseCount("-3"))
        assertNull(CardSelection.parseCount("x"))
        assertEquals(12, CardSelection.parseCount("12"))
    }

    @Test
    fun tappingTogglesACard() {
        val one = CardSelection.toggle(emptySet(), 4)
        assertEquals(setOf(4L), one)
        assertEquals(emptySet<Long>(), CardSelection.toggle(one, 4))
    }

    // --- Forms: Newest / Oldest X ---

    private val template = LogTemplate(id = 1, uuid = "form-uuid", name = "Odor Log", createdAt = 1L, schemaJson = "[]")
    private val seen = FieldDef(id = "f-seen", label = "Seen", type = FieldType.DATE)
    private val note = FieldDef(id = "f-note", label = "Smell", type = FieldType.TEXT)

    private fun entry(id: Long, createdAt: String, values: String = "{}") =
        LogEntry(id = id, templateId = 1, createdAt = createdAt, valuesJson = values)

    private val byCreation = listOf(
        entry(1, "2026-10-01T09:00:00-04:00"),
        entry(2, "2026-10-03T09:00:00-04:00"),
        entry(3, "2026-10-02T09:00:00-04:00"),
        entry(4, "2026-10-03T09:00:00-04:00"), // same time as 2: id breaks the tie
    )

    @Test
    fun formsUseTheEntryTimestampWithoutADesignatedField() {
        assertEquals(setOf(4L, 2L), CardSelection.formPick(byCreation, listOf(note), template, 2, newest = true))
        assertEquals(setOf(1L, 3L), CardSelection.formPick(byCreation, listOf(note), template, 2, newest = false))
        assertEquals(setOf(4L), CardSelection.formPick(byCreation, listOf(note), template, 1, newest = true))
        assertEquals(setOf(1L, 2L, 3L, 4L), CardSelection.formPick(byCreation, listOf(note), template, 99, newest = true))
    }

    @Test
    fun formsUseTheDesignatedFieldAndSkipEntriesWithoutIt() {
        val designated = template.copy(sortTimestampFieldId = seen.id)
        val entries = listOf(
            // Created last but seen first.
            entry(1, "2026-10-09T09:00:00-04:00", """{"f-seen":"2026-09-01"}"""),
            entry(2, "2026-10-01T09:00:00-04:00", """{"f-seen":"2026-09-20"}"""),
            entry(3, "2026-10-05T09:00:00-04:00", """{"f-seen":"2026-09-10"}"""),
            entry(4, "2026-10-10T09:00:00-04:00"), // no Seen value: never picked
        )
        val fields = listOf(seen, note)
        assertEquals(setOf(2L), CardSelection.formPick(entries, fields, designated, 1, newest = true))
        assertEquals(setOf(1L, 3L), CardSelection.formPick(entries, fields, designated, 2, newest = false))
        assertEquals(setOf(1L, 2L, 3L), CardSelection.formPick(entries, fields, designated, 10, newest = true))
    }

    @Test
    fun aFilteredListOnlyPicksFromWhatIsShown() {
        val shown = byCreation.filter { it.id != 4L }
        assertEquals(setOf(2L), CardSelection.formPick(shown, listOf(note), template, 1, newest = true))
    }

    // --- Clicker Data: Newest / Oldest X ---

    private val zone = ZoneOffset.UTC

    private fun card(id: Long, created: String, date: String? = null, time: String? = null, values: Map<String, String> = emptyMap()) =
        ClickerCard(
            id = id,
            clickerLogId = 1,
            uuid = "u$id",
            createdAt = LocalDateTime.parse(created).toInstant(zone).toEpochMilli(),
            displayDate = date,
            displayTime = time,
            valuesJson = ClickerValues.encode(values),
        )

    @Test
    fun clickerUsesTheCardStampsThenCreation() {
        val cards = listOf(
            card(1, "2026-10-05T09:00", date = "2026-09-01"), // stamped early, created late
            card(2, "2026-10-01T09:00", date = "2026-10-01", time = "08:00"),
            card(3, "2026-10-01T10:00", date = "2026-10-01", time = "18:00"),
            card(4, "2026-10-02T09:00"), // no stamps: its creation time
        )
        assertEquals(setOf(4L), CardSelection.clickerPick(cards, 1, newest = true, zone = zone))
        assertEquals(setOf(4L, 3L), CardSelection.clickerPick(cards, 2, newest = true, zone = zone))
        assertEquals(setOf(1L, 2L), CardSelection.clickerPick(cards, 2, newest = false, zone = zone))
        assertEquals(setOf(1L, 2L, 3L, 4L), CardSelection.clickerPick(cards, 9, newest = false, zone = zone))
    }

    // --- Copy and Export: only the selection ---

    private val notes = mapOf(
        2L to listOf(EntryNote(entryId = 2, createdAt = "2026-10-04T09:00:00-04:00", text = "Faded")),
        1L to listOf(EntryNote(entryId = 1, createdAt = "2026-10-02T09:00:00-04:00", text = "Unselected note")),
    )

    @Test
    fun formCopyIsTheTxtReportOfTheSelection() {
        val values = """{"f-note":"Ammonia","__notes__":"Near the vent"}"""
        val entries = listOf(
            entry(1, "2026-10-01T09:00:00-04:00", """{"f-note":"Unselected"}"""),
            entry(2, "2026-10-03T09:00:00-04:00", values),
            entry(3, "2026-10-02T09:00:00-04:00", values),
        )
        val selected = entries.filter { it.id != 1L }
        val text = CardSelection.formCopyText(template, listOf(note), selected, notes)
        assertEquals(
            ReportBuilder.build(template, listOf(note), selected, markdown = false, entryNotes = notes, includeFollowUps = true).text,
            text,
        )
        assertTrue(text.startsWith("Odor Log Report\n"))
        assertTrue(text.contains("Total entries: 2"))
        assertTrue(text.contains("Smell: Ammonia"))
        assertTrue(text.contains("Notes:\nNear the vent"))
        assertTrue(text.contains("Faded"))
        assertFalse(text.contains("Unselected"))
        // Oldest first, as the report reads.
        assertTrue(text.indexOf("October 2") < text.indexOf("October 3"))
    }

    @Test
    fun formExportOfTheSelectionCarriesOnlyItsEntriesAndNotes() {
        val entries = listOf(entry(1, "2026-10-01T09:00:00-04:00"), entry(2, "2026-10-03T09:00:00-04:00"))
        val ids = setOf(2L)
        val selectedNotes = CardSelection.notesFor(notes, ids)
        assertEquals(setOf(2L), selectedNotes.keys)

        val selected = entries.filter { it.id in ids }
        val txt = String(LogExport.text(template, listOf(note), selected, selectedNotes, includeFollowUps = true).bytes)
        assertTrue(txt.contains("Total entries: 1"))
        assertEquals("odor_log.txt", LogExport.text(template, listOf(note), selected, selectedNotes, true).suggestedName)

        val backup = String(LogExport.json(template, selected, selectedNotes, includeFollowUps = true).bytes)
        assertTrue(backup.contains("Faded"))
        assertFalse(backup.contains("Unselected note"))
    }

    private val clickerLog = ClickerLog(
        id = 1, uuid = "log-uuid", title = "Water Intake", createdAt = 1, lastAccessedAt = 1, fieldsJson = "[]",
        autoDateStamp = true, autoTimeStamp = false, allowFollowUp = true,
    )
    private val glasses = ClickerField(id = "f-glasses", type = ClickerFieldType.CLICK_TRACKER, label = "Glasses")

    @Test
    fun clickerCopyAndExportCarryOnlyTheSelection() {
        val cards = listOf(
            card(1, "2026-09-01T09:00", date = "2026-09-01", values = mapOf("f-glasses" to "3")),
            card(2, "2026-09-02T09:00", date = "2026-09-02", values = mapOf("f-glasses" to "5", ClickerValues.FOLLOW_UP_KEY to "Felt good")),
            card(3, "2026-09-03T09:00", date = "2026-09-03", values = mapOf("f-glasses" to "8")),
        )
        val selected = cards.filter { it.id != 1L }
        val copy = CardSelection.clickerCopyText(clickerLog, listOf(glasses), selected)
        assertEquals(String(ClickerExport.text(clickerLog, listOf(glasses), selected, includeFollowUps = true).bytes), copy)
        assertTrue(copy.contains("Total cards: 2"))
        assertTrue(copy.contains("Felt good"))
        assertFalse(copy.contains("Glasses: 3"))
        assertEquals("water_intake.txt", ClickerExport.text(clickerLog, listOf(glasses), selected, true).suggestedName)
    }

    // --- Delete: only the selection, never the grouping ---

    private fun database(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun deletingSelectedEntriesRemovesThemAndTheirNotesOnly() = runBlocking {
        val db = database()
        try {
            val templates = db.logTemplateDao()
            val entryDao = db.logEntryDao()
            val noteDao = db.entryNoteDao()
            val formId = templates.insert(LogTemplate(uuid = StableUuid.createNew(), name = "Odor Log", createdAt = 1, schemaJson = "[]"))
            val otherId = templates.insert(LogTemplate(uuid = StableUuid.createNew(), name = "Other", createdAt = 1, schemaJson = "[]"))
            val keep = entryDao.insert(LogEntry(templateId = formId, createdAt = "2026-10-01T09:00:00-04:00", valuesJson = "{}"))
            val gone = entryDao.insert(LogEntry(templateId = formId, createdAt = "2026-10-02T09:00:00-04:00", valuesJson = "{}"))
            val elsewhere = entryDao.insert(LogEntry(templateId = otherId, createdAt = "2026-10-02T09:00:00-04:00", valuesJson = "{}"))
            noteDao.insert(EntryNote(entryId = gone, createdAt = "2026-10-03T09:00:00-04:00", text = "bye"))
            noteDao.insert(EntryNote(entryId = keep, createdAt = "2026-10-03T09:00:00-04:00", text = "stay"))

            // An id from another form is ignored: only this form's entries go.
            CardSelection.deleteEntries(db, formId, setOf(gone, elsewhere))

            assertEquals(listOf(keep), entryDao.getForTemplateOnce(formId).map { it.id })
            assertEquals(1, entryDao.getForTemplateOnce(otherId).size)
            assertTrue(noteDao.getForEntryOnce(gone).isEmpty())
            assertEquals(1, noteDao.getForEntryOnce(keep).size)
            assertNotNull(templates.getById(formId))
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingSelectedCardsRemovesThemOnly() = runBlocking {
        val db = database()
        try {
            val dao = db.clickerDao()
            val logId = dao.insertLog(clickerLog.copy(id = 0))
            val keep = dao.insertCard(card(0, "2026-09-01T09:00").copy(id = 0, uuid = StableUuid.createNew(), clickerLogId = logId))
            val gone = dao.insertCard(card(0, "2026-09-02T09:00").copy(id = 0, uuid = StableUuid.createNew(), clickerLogId = logId))

            CardSelection.deleteCards(dao, logId, setOf(gone))

            assertEquals(listOf(keep), dao.getCardsForLog(logId).map { it.id })
            assertNotNull(dao.getLog(logId))
        } finally {
            db.close()
        }
    }
}
