package com.datadragon.app.data

import com.datadragon.app.export.ClickerExport
import com.datadragon.app.export.LogExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Clicker Data export builders: oldest-first cards, hidden automatic stamps
 * staying hidden, every field exporting, the Follow-Up Notes choice, and the
 * plain `<name>.<ext>` file names shared with Forms.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickerExportTest {

    private val tracker = ClickerField(id = "f-tracker", type = ClickerFieldType.CLICK_TRACKER, label = "Glasses", startingNumber = 3)
    private val writeIn = ClickerField(id = "f-write", type = ClickerFieldType.WRITE_IN_NUMBER, label = "Steps")
    private val text = ClickerField(id = "f-text", type = ClickerFieldType.TEXT, label = "Mood")
    private val multi = ClickerField(id = "f-multi", type = ClickerFieldType.MULTITEXT, label = "Details")
    private val date = ClickerField(id = "f-date", type = ClickerFieldType.DATE, label = "Seen On")
    private val fields = listOf(tracker, writeIn, text, multi, date)

    private fun log(autoDate: Boolean, autoTime: Boolean, faceOnly: Boolean = false) = ClickerLog(
        id = 1,
        uuid = "log-uuid",
        title = "Water Intake",
        createdAt = 1,
        lastAccessedAt = 1,
        fieldsJson = "[]",
        displayOnlyClickerDateTime = faceOnly,
        autoDateStamp = autoDate,
        autoTimeStamp = autoTime,
        allowFollowUp = true,
    )

    private fun card(uuid: String, createdAt: Long, date: String?, time: String?, values: Map<String, String>) =
        ClickerCard(
            clickerLogId = 1,
            uuid = uuid,
            createdAt = createdAt,
            displayDate = date,
            displayTime = time,
            valuesJson = ClickerValues.encode(values),
        )

    // Stored newest first, as the Main screen lists them.
    private val cards = listOf(
        card("card-b", 200, "2026-09-02", "14:15", mapOf("f-tracker" to "7", ClickerValues.FOLLOW_UP_KEY to "Felt good")),
        card(
            "card-a", 100, "2026-09-01", "08:05",
            mapOf("f-write" to "42", "f-text" to "Calm", "f-multi" to "Line one\nLine two", "f-date" to "2026-08-30"),
        ),
    )

    @Test
    fun reportListsCardsOldestFirstWithVisibleStampsAndCount() {
        val report = ClickerReport.build(log(autoDate = true, autoTime = true), fields, cards, includeFollowUps = true)
        assertEquals("Water Intake Report", report.title)
        assertEquals(2, report.totalCards)
        assertEquals(
            listOf(
                EntryValues.displayDateTime(java.time.LocalDateTime.of(2026, 9, 1, 8, 5)),
                EntryValues.displayDateTime(java.time.LocalDateTime.of(2026, 9, 2, 14, 15)),
            ),
            report.cards.map { it.heading },
        )
        assertEquals(
            EntryValues.displayDate(java.time.LocalDate.of(2026, 9, 1)) + " – " +
                EntryValues.displayDate(java.time.LocalDate.of(2026, 9, 2)),
            report.dateRange,
        )
        val text = ClickerReport.text(report, markdown = false)
        assertTrue(text.contains("Total cards: 2"))
        assertTrue(text.indexOf("Steps: 42") < text.indexOf("Glasses: 7"))
    }

    @Test
    fun hiddenStampsNeverAppearAndCardsGetNumberedHeadings() {
        val hidden = log(autoDate = false, autoTime = false)
        val report = ClickerReport.build(hidden, fields, cards, includeFollowUps = true)
        assertNull(report.dateRange)
        assertEquals(listOf("Card 1", "Card 2"), report.cards.map { it.heading })

        val text = ClickerReport.text(report, markdown = true)
        assertFalse(text.contains("Date range"))
        assertFalse(text.contains("2026-09-01"))
        assertFalse(text.contains(EntryValues.displayDate(java.time.LocalDate.of(2026, 9, 1))))
        assertFalse(text.contains(EntryValues.displayTime(java.time.LocalTime.of(8, 5))))
        // A user-created Date field is ordinary data and still exports.
        assertTrue(text.contains("**Seen On:** " + EntryValues.displayDate(java.time.LocalDate.of(2026, 8, 30))))
    }

    @Test
    fun onlyTheEnabledStampShows() {
        val dateOnly = log(autoDate = true, autoTime = false)
        val report = ClickerReport.build(dateOnly, fields, cards, includeFollowUps = true)
        assertEquals(EntryValues.displayDate(java.time.LocalDate.of(2026, 9, 1)), report.cards.first().heading)

        val timeOnly = ClickerReport.build(log(autoDate = false, autoTime = true), fields, cards, includeFollowUps = true)
        assertNull(timeOnly.dateRange)
        assertEquals(EntryValues.displayTime(java.time.LocalTime.of(8, 5)), timeOnly.cards.first().heading)
    }

    @Test
    fun everyFieldExportsTrackerDefaultsAndBlanksAreLeftOut() {
        // The face-only setting hides text on the card face, never in the export.
        val report = ClickerReport.build(log(true, false, faceOnly = true), fields, cards, includeFollowUps = true)
        val older = report.cards[0].lines.associate { it.label to it.value }
        assertEquals("3", older["Glasses"]) // untouched tracker: its starting number
        assertEquals("42", older["Steps"])
        assertEquals("Calm", older["Mood"])
        assertEquals("Line one\nLine two", older["Details"])
        assertTrue(report.cards[0].lines.single { it.label == "Details" }.block)

        val newer = report.cards[1].lines.map { it.label }
        assertEquals(listOf("Glasses"), newer) // blank write-in, text, and date omitted
    }

    @Test
    fun followUpNotesFollowTheCheckbox() {
        val withNotes = ClickerReport.text(ClickerReport.build(log(true, true), fields, cards, true), markdown = false)
        assertTrue(withNotes.contains("Follow-Up Notes:\nFelt good"))
        val withoutNotes = ClickerReport.text(ClickerReport.build(log(true, true), fields, cards, false), markdown = false)
        assertFalse(withoutNotes.contains("Follow-Up Notes"))
        assertFalse(withoutNotes.contains("Felt good"))
    }

    @Test
    fun csvHasVisibleStampsOnlyAndNoCreatedAt() {
        val visible = ClickerReport.csv(log(true, true), fields, cards, includeFollowUps = true).split("\r\n")
        assertEquals("card_id,date,time,Glasses,Steps,Mood,Details,Seen On,follow_up_notes", visible[0])
        assertEquals("card-a,2026-09-01,08:05,3,42,Calm,\"Line one\nLine two\",2026-08-30,", visible[1])
        assertEquals("card-b,2026-09-02,14:15,7,,,,,Felt good", visible[2])

        val hidden = ClickerReport.csv(log(false, false), fields, cards, includeFollowUps = false)
        val lines = hidden.split("\r\n")
        assertEquals("card_id,Glasses,Steps,Mood,Details,Seen On", lines[0])
        assertEquals("card-b,7,,,,", lines[2])
        assertFalse(hidden.contains("created_at"))
        assertFalse(hidden.contains("2026-09-0"))
        assertFalse(hidden.contains("Felt good"))
    }

    @Test
    fun fileNamesHaveNoReportSuffix() {
        val l = log(true, true)
        assertEquals("water_intake.txt", ClickerExport.text(l, fields, cards, true).suggestedName)
        assertEquals("water_intake.md", ClickerExport.markdown(l, fields, cards, true).suggestedName)
        assertEquals("water_intake.csv", ClickerExport.csv(l, fields, cards, true).suggestedName)
        assertEquals("water_intake.json", ClickerExport.json(l, cards, true).suggestedName)
        assertEquals("water_intake.pdf", ExportNaming.fileName(l.title, "pdf"))

        val template = LogTemplate(uuid = "form-uuid", name = "Blood Pressure", createdAt = 1L, schemaJson = "[]")
        assertEquals("blood_pressure.txt", ReportBuilder.build(template, emptyList(), emptyList(), markdown = false).fileName)
        assertEquals("blood_pressure.md", ReportBuilder.build(template, emptyList(), emptyList(), markdown = true).fileName)
        assertEquals("blood_pressure.csv", LogExport.csv(template, emptyList(), emptyList()).suggestedName)
        assertEquals("blood_pressure.pdf", ExportNaming.fileName(template.name, "pdf"))
    }

    @Test
    fun jsonKeepsEverythingAndDropsOnlyFollowUpsWhenUnchecked() {
        val l = log(true, true)
        val full = BackupCodec.decode(String(ClickerExport.json(l, cards, true).bytes))
        assertEquals(listOf(BackupCategory.CLICKER_DATA), full.includedCategories)
        val exported = full.clickerLogs.single()
        assertEquals("log-uuid", exported.uuid)
        assertEquals(listOf("card-a", "card-b"), exported.cards.map { it.uuid })
        assertEquals(listOf(100L, 200L), exported.cards.map { it.createdAt })
        assertEquals("Felt good", ClickerValues.decode(exported.cards[1].valuesJson)[ClickerValues.FOLLOW_UP_KEY])

        val trimmed = BackupCodec.decode(String(ClickerExport.json(l, cards, false).bytes)).clickerLogs.single()
        val values = ClickerValues.decode(trimmed.cards[1].valuesJson)
        assertFalse(values.containsKey(ClickerValues.FOLLOW_UP_KEY))
        assertEquals("7", values["f-tracker"])
    }
}
