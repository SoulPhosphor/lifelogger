package com.datadragon.app.data

import com.datadragon.app.export.ChecklistExport
import com.datadragon.app.export.ChecklistExportFormat
import com.datadragon.app.export.ClickerExport
import com.datadragon.app.export.LuckyListExport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Human Readable" `.json` exports for Clicker Data, Lists and Lucky Lists:
 * each carries what its `.txt` export carries, structured, with no internal
 * ids or settings, under a `<name>_clean.json` file name.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HumanReadableJsonExportTest {

    private fun parse(bytes: ByteArray): JsonObject = Json.parseToJsonElement(String(bytes)).jsonObject

    // --- Clicker Data ---

    private val tracker = ClickerField(id = "f-tracker", type = ClickerFieldType.CLICK_TRACKER, label = "Glasses", startingNumber = 3)
    private val writeIn = ClickerField(id = "f-write", type = ClickerFieldType.WRITE_IN_NUMBER, label = "Steps")
    private val text = ClickerField(id = "f-text", type = ClickerFieldType.TEXT, label = "Mood")
    private val dateTime = ClickerField(id = "f-dt", type = ClickerFieldType.DATE_TIME, label = "Seen At")
    private val fields = listOf(tracker, writeIn, text, dateTime)

    private fun log(autoDate: Boolean, autoTime: Boolean) = ClickerLog(
        id = 1,
        uuid = "log-uuid",
        title = "Water Intake",
        createdAt = 1,
        lastAccessedAt = 1,
        fieldsJson = "[]",
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

    private val cards = listOf(
        card("card-b", 200, "2026-09-02", "14:15", mapOf("f-tracker" to "7", ClickerValues.FOLLOW_UP_KEY to "Felt good")),
        card("card-a", 100, "2026-09-01", "08:05", mapOf("f-write" to "42", "f-text" to "Calm", "f-dt" to "2026-08-30 07:45")),
    )

    @Test
    fun clickerMatchesTheReport() {
        val root = parse(ClickerExport.cleanJson(log(true, true), fields, cards, includeFollowUps = true).bytes)
        assertEquals(listOf("title", "dateRange", "totalCards", "cards"), root.keys.toList())
        assertEquals("Water Intake Report", root["title"]!!.jsonPrimitive.content)
        assertEquals("2026-09-01", root["dateRange"]!!.jsonObject["start"]!!.jsonPrimitive.content)
        assertEquals("2026-09-02", root["dateRange"]!!.jsonObject["end"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(2), root["totalCards"])

        val older = root["cards"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("dateTime", "values"), older.keys.toList())
        assertEquals("2026-09-01T08:05", older["dateTime"]!!.jsonPrimitive.content)
        val v = older["values"]!!.jsonObject
        assertEquals(listOf("Glasses", "Steps", "Mood", "Seen At"), v.keys.toList())
        assertEquals(JsonPrimitive(3), v["Glasses"]) // untouched tracker: its starting number
        assertFalse(v["Steps"]!!.jsonPrimitive.isString)
        assertEquals("42", v["Steps"]!!.jsonPrimitive.content)
        assertEquals("2026-08-30T07:45", v["Seen At"]!!.jsonPrimitive.content)

        val newer = root["cards"]!!.jsonArray[1].jsonObject
        assertEquals("Felt good", newer["Follow-Up Notes"]!!.jsonPrimitive.content)
    }

    @Test
    fun clickerHiddenStampsAndFollowUpsStayOut() {
        val root = parse(ClickerExport.cleanJson(log(false, false), fields, cards, includeFollowUps = false).bytes)
        assertEquals(listOf("title", "totalCards", "cards"), root.keys.toList())
        root["cards"]!!.jsonArray.forEach { assertEquals(listOf("values"), it.jsonObject.keys.toList()) }

        val dateOnly = parse(ClickerExport.cleanJson(log(true, false), fields, cards, includeFollowUps = true).bytes)
        assertEquals("2026-09-01", dateOnly["cards"]!!.jsonArray[0].jsonObject["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun clickerFileName() {
        assertEquals("water_intake_clean.json", ClickerExport.cleanJson(log(true, true), fields, cards, false).suggestedName)
    }

    // --- Lists ---

    @Test
    fun listNestsSubItemsAndSkipsBlanks() {
        val list = Checklist(id = 1, uuid = "list-uuid", name = "Groceries", createdAt = 1)
        val items = listOf(
            ChecklistItem(checklistId = 1, text = "Produce", completed = false, indent = 0, position = 0),
            ChecklistItem(checklistId = 1, text = "Apples", completed = true, indent = 1, position = 1),
            ChecklistItem(checklistId = 1, text = " ", indent = 0, position = 2),
            ChecklistItem(checklistId = 1, text = "Bread", completed = true, indent = 0, position = 3),
        )
        val export = ChecklistExport.cleanJson(list, items)
        assertEquals("groceries_clean.json", export.suggestedName)
        val root = parse(export.bytes)
        assertEquals(listOf("title", "items"), root.keys.toList())
        assertEquals("Groceries", root["title"]!!.jsonPrimitive.content)
        val top = root["items"]!!.jsonArray
        assertEquals(2, top.size)
        val produce = top[0].jsonObject
        assertEquals(listOf("text", "completed", "subItems"), produce.keys.toList())
        assertEquals(JsonPrimitive(false), produce["completed"])
        val apples = produce["subItems"]!!.jsonArray.single().jsonObject
        assertEquals("Apples", apples["text"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(true), apples["completed"])
        assertEquals(listOf("text", "completed"), top[1].jsonObject.keys.toList())
    }

    // --- Lucky Lists ---

    @Test
    fun luckyListIsTitleAndItemTextsInOrder() {
        val list = LuckyList(id = 1, uuid = "lucky-uuid", name = "Dinner", createdAt = 1)
        val items = listOf(
            LuckyListItem(luckyListId = 1, text = "Soup", position = 1),
            LuckyListItem(luckyListId = 1, text = "Pizza", position = 0),
            LuckyListItem(luckyListId = 1, text = "", position = 2),
        )
        val export = LuckyListExport.build(list, items, ChecklistExportFormat.CLEAN_JSON)
        assertEquals("dinner_clean.json", export.suggestedName)
        val root = parse(export.bytes)
        assertEquals(listOf("title", "items"), root.keys.toList())
        assertEquals("Dinner", root["title"]!!.jsonPrimitive.content)
        assertEquals(listOf("Pizza", "Soup"), root["items"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
