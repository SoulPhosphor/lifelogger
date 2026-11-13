package com.datadragon.app.data

import com.datadragon.app.export.LogExport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Forms "Human Readable JSON (.json)" export: the `.txt` report's header and
 * order, labels instead of ids, native values, the item-row summary lines, the
 * suffix rule for repeated keys, and the `<name>_clean.json` file name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleanJsonBuilderTest {

    private val template = LogTemplate(uuid = "form-uuid", name = "Odor Log", createdAt = 1L, schemaJson = "[]")

    private val dateTime = FieldDef(id = "f-date", label = "Date", type = FieldType.DATETIME)
    private val type = FieldDef(
        id = "f-type",
        label = "Odor Type",
        type = FieldType.MULTIPLE,
        choices = listOf(ChoiceOption("c-amm", "Ammonia"), ChoiceOption("c-burn", "Burning")),
    )
    private val strength = FieldDef(
        id = "f-strength",
        label = "Strength",
        type = FieldType.DROPDOWN,
        choices = listOf(ChoiceOption("c-weak", "Weak"), ChoiceOption("c-strong", "Strong")),
    )
    private val counts = FieldDef(
        id = "f-counts",
        label = "Counts",
        type = FieldType.COMPARISON,
        choices = listOf(ChoiceOption("c-amm", "Ammonia"), ChoiceOption("c-burn", "Burning")),
    )
    private val notesField = FieldDef(id = "f-notes", label = "Notes", type = FieldType.TEXT)
    private val repeated = FieldDef(id = "f-date-2", label = "Date", type = FieldType.DATE)

    private fun entry(
        id: Long,
        values: String,
        marked: Boolean = false,
        updatedAt: String? = null,
        createdAt: String = "2026-10-30T03:16:00-04:00",
    ) = LogEntry(id = id, templateId = 1, createdAt = createdAt, updatedAt = updatedAt, valuesJson = values, marked = marked)

    private fun build(fields: List<FieldDef>, entries: List<LogEntry>, notes: Map<Long, List<EntryNote>> = emptyMap(), include: Boolean = false) =
        Json.parseToJsonElement(CleanJsonBuilder.build(template, fields, entries, notes, include)).jsonObject

    @Test
    fun rootMatchesTheReportHeader() {
        val entries = listOf(
            entry(2, "{}", createdAt = "2026-11-02T09:00:00-05:00"),
            entry(1, "{}", createdAt = "2026-10-30T03:16:00-04:00"),
        )
        val root = build(listOf(dateTime), entries)
        assertEquals(listOf("title", "dateRange", "totalEntries", "entries"), root.keys.toList())
        assertEquals("Odor Log Report", root["title"]!!.jsonPrimitive.content)
        val range = root["dateRange"]!!.jsonObject
        assertEquals("2026-10-30", range["start"]!!.jsonPrimitive.content)
        assertEquals("2026-11-02", range["end"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(2), root["totalEntries"])
        // Oldest first, as in the .txt report; timestamps kept with their offset.
        assertEquals(
            listOf("2026-10-30T03:16:00-04:00", "2026-11-02T09:00:00-05:00"),
            root["entries"]!!.jsonArray.map { it.jsonObject["createdAt"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun noEntriesMeansNoDateRange() {
        val root = build(listOf(dateTime), emptyList())
        assertEquals(listOf("title", "totalEntries", "entries"), root.keys.toList())
        assertEquals(JsonPrimitive(0), root["totalEntries"])
    }

    @Test
    fun numbersScalesAndRatiosAreStructured() {
        val number = FieldDef(id = "f-num", label = "Weight", type = FieldType.NUMBER)
        val scale = FieldDef(id = "f-scale", label = "Pain", type = FieldType.SCALE, from = 1, to = 10)
        val ratio = FieldDef(id = "f-ratio", label = "Pressure", type = FieldType.RATIO, unit = "mmHg")
        val values = """{"f-num":"12.5","f-scale":"7","f-ratio":"120/80"}"""
        val v = build(listOf(number, scale, ratio), listOf(entry(1, values)))["entries"]!!.jsonArray[0].jsonObject["values"]!!.jsonObject
        assertTrue(v["Weight"]!!.jsonPrimitive.isString.not())
        assertEquals("12.5", v["Weight"]!!.jsonPrimitive.content)
        val pain = v["Pain"]!!.jsonObject
        assertEquals(listOf("value", "max"), pain.keys.toList())
        assertFalse(pain["value"]!!.jsonPrimitive.isString)
        assertEquals("7", pain["value"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(10), pain["max"])
        val pressure = v["Pressure"]!!.jsonObject
        assertEquals("120/80", pressure["value"]!!.jsonPrimitive.content)
        assertEquals("mmHg", pressure["unit"]!!.jsonPrimitive.content)
    }

    @Test
    fun fieldsAndChoicesReadAsLabelsInStoredForm() {
        val values = """{"f-date":"2026-10-30T03:16","f-type":["c-amm","c-burn"],"f-strength":"c-strong","__notes__":"Smelled near vent"}"""
        val e = build(listOf(dateTime, type, strength), listOf(entry(1, values)))["entries"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("createdAt", "values", "Notes"), e.keys.toList())
        val v = e["values"]!!.jsonObject
        assertEquals(listOf("Date", "Odor Type", "Strength"), v.keys.toList())
        assertEquals("2026-10-30T03:16", v["Date"]!!.jsonPrimitive.content)
        assertEquals(listOf("Ammonia", "Burning"), v["Odor Type"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("Strong", v["Strength"]!!.jsonPrimitive.content)
        assertEquals("Smelled near vent", e["Notes"]!!.jsonPrimitive.content)
    }

    @Test
    fun unknownChoiceIdsAreKeptRaw() {
        val values = """{"f-type":["c-amm","c-gone"],"f-strength":"c-gone"}"""
        val v = build(listOf(type, strength), listOf(entry(1, values)))["entries"]!!.jsonArray[0].jsonObject["values"]!!.jsonObject
        assertEquals(listOf("Ammonia", "c-gone"), v["Odor Type"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("c-gone", v["Strength"]!!.jsonPrimitive.content)
    }

    @Test
    fun internalEntryMetadataIsLeftOut() {
        val full = build(listOf(dateTime), listOf(entry(1, "{}", marked = true, updatedAt = "2026-10-31T09:00:00-04:00")))["entries"]!!
            .jsonArray[0].jsonObject
        assertEquals(listOf("createdAt", "values"), full.keys.toList())
    }

    @Test
    fun followUpNotesFollowTheCheckbox() {
        val notes = mapOf(1L to listOf(EntryNote(entryId = 1, createdAt = "2026-10-30T05:00:00-04:00", text = "Gone now")))
        val entries = listOf(entry(1, "{}"), entry(2, "{}"))

        val off = build(listOf(dateTime), entries, notes, include = false)["entries"]!!.jsonArray
        assertFalse(off[0].jsonObject.containsKey("Follow-Up Notes"))

        val on = build(listOf(dateTime), entries, notes, include = true)["entries"]!!.jsonArray
        val note = on[0].jsonObject["Follow-Up Notes"]!!.jsonArray[0].jsonObject
        assertEquals("2026-10-30T05:00:00-04:00", note["createdAt"]!!.jsonPrimitive.content)
        assertEquals("Gone now", note["text"]!!.jsonPrimitive.content)
        assertFalse(on[1].jsonObject.containsKey("Follow-Up Notes"))
    }

    @Test
    fun repeatedKeysGetANumberSuffix() {
        val values = """{"f-date":"2026-10-30T03:16","f-date-2":"2026-10-29","f-notes":"typed","__notes__":"box"}"""
        val e = build(listOf(dateTime, repeated, notesField), listOf(entry(1, values)))["entries"]!!.jsonArray[0].jsonObject
        val v = e["values"]!!.jsonObject
        assertEquals(listOf("Date", "Date (2)", "Notes"), v.keys.toList())
        assertEquals("2026-10-29", v["Date (2)"]!!.jsonPrimitive.content)
        // A field labelled "Notes" and the Notes box never collide.
        assertEquals("typed", v["Notes"]!!.jsonPrimitive.content)
        assertEquals("box", e["Notes"]!!.jsonPrimitive.content)
    }

    @Test
    fun comparisonRowsUseCaptionsDifferenceAndSummaryLines() {
        val values = """{"f-counts":[{"item":"c-amm","first":3,"second":5},{"item":"c-burn","first":4,"second":6}]}"""
        val counts = build(listOf(counts.copy(showAverage = true)), listOf(entry(1, values)))["entries"]!!
            .jsonArray[0].jsonObject["values"]!!.jsonObject["Counts"]!!.jsonObject
        assertEquals(listOf("Rows", "Total Difference", "Average of Differences"), counts.keys.toList())
        val row = counts["Rows"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("Item", "Expected", "Actual", "Difference"), row.keys.toList())
        assertEquals("Ammonia", row["Item"]!!.jsonPrimitive.content)
        assertEquals("2", row["Difference"]!!.jsonPrimitive.content)
        assertEquals("4", counts["Total Difference"]!!.jsonPrimitive.content)
        assertEquals("2", counts["Average of Differences"]!!.jsonPrimitive.content)
    }

    @Test
    fun clickerRowsFillTheStartingNumber() {
        val clicker = counts.copy(clickerCounting = true, clickerStartingNumber = 10)
        val values = """{"f-counts":[{"item":"c-amm","first":3}]}"""
        val row = build(listOf(clicker), listOf(entry(1, values)))["entries"]!!
            .jsonArray[0].jsonObject["values"]!!.jsonObject["Counts"]!!.jsonObject["Rows"]!!.jsonArray[0].jsonObject
        assertEquals("10", row["Actual"]!!.jsonPrimitive.content)
        assertEquals("7", row["Difference"]!!.jsonPrimitive.content)
    }

    @Test
    fun listItemQuantitiesRowsShowAmountAndCheckedOnlyWhenOn() {
        val quantities = counts.copy(type = FieldType.LIST_ITEM_QUANTITIES, addQuantities = true, addCheckbox = true)
        val values = """{"f-counts":[{"item":"c-amm","first":5,"checked":true},{"item":"c-burn","first":2}]}"""
        val field = build(listOf(quantities), listOf(entry(1, values)))["entries"]!!
            .jsonArray[0].jsonObject["values"]!!.jsonObject["Counts"]!!.jsonObject
        val rows = field["Rows"] as JsonArray
        assertEquals(listOf("Item", "Amount", "Checked"), rows[0].jsonObject.keys.toList())
        assertEquals(listOf("Item", "Amount"), rows[1].jsonObject.keys.toList())
        assertEquals("7", field["Total Quantity"]!!.jsonPrimitive.content)

        val noCheckbox = build(listOf(quantities.copy(addCheckbox = false, addQuantities = false)), listOf(entry(1, values)))["entries"]!!
            .jsonArray[0].jsonObject["values"]!!.jsonObject["Counts"]!!.jsonObject
        assertEquals(listOf("Rows"), noCheckbox.keys.toList())
        assertNull((noCheckbox["Rows"] as JsonArray)[0].jsonObject["Checked"])
    }

    @Test
    fun emptyFieldsAreLeftOut() {
        val v = build(listOf(dateTime, type, counts), listOf(entry(1, """{"f-type":[],"f-counts":[]}""")))["entries"]!!
            .jsonArray[0].jsonObject["values"] as JsonObject
        assertEquals(emptySet<String>(), v.keys)
    }

    @Test
    fun fileNameIsTheFormNameWithACleanSuffix() {
        assertEquals("odor_log_clean.json", LogExport.cleanJson(template, emptyList(), emptyList()).suggestedName)
    }
}
