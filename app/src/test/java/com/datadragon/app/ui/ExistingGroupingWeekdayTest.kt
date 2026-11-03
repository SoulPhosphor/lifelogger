package com.datadragon.app.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.datadragon.app.data.*
import com.datadragon.app.ui.screens.*
import com.datadragon.app.ui.theme.DataDragonTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Open saved groupings containing old data, use Add Field, select weekday, then save and reopen the schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExistingGroupingWeekdayTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getInstance(app)

    @After fun clearViewModels() { store.clear() }

    @Test fun addWeekdayToAnExistingFormWithSavedEntries() {
        val originalField = FieldDef(id = StableUuid.createNew(), label = "Old Answer", type = FieldType.TEXT)
        val original = LogTemplate(uuid = StableUuid.createNew(), name = "Existing Form", createdAt = 1,
            schemaJson = FormMarkdownParser.encodeFields(listOf(originalField)), locked = false)
        val id = runBlocking { db.logTemplateDao().insert(original) }
        val answer = EntryValues.encode(mapOf(originalField.id to EntryValues.string("Keep this answer")))
        val entryId = runBlocking { db.logEntryDao().insert(LogEntry(templateId = id, createdAt = "2026-11-01T12:00:00Z", valuesJson = answer)) }
        val vm = EditFormViewModel(app).also { store.put("form", it) }
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            EditFormScreen(logId = id.toString(), onBack = {}, onEditCalendar = {}, onOpenCalendar = {}, viewModel = vm)
        } }
        compose.waitUntil(10_000) { vm.fields.value.isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Add Field", substring = true))
        compose.onNodeWithText("Add Field", substring = true).performClick()
        compose.onNodeWithText("Text (one line)").performScrollTo().performClick()
        selectWeekday()
        compose.onNodeWithText("Only Allow Choosing One Day").assertExists()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { runBlocking { db.logTemplateDao().getById(id) }!!.schemaJson.contains("DAY_OF_WEEK") }
        val saved = runBlocking { db.logTemplateDao().getById(id) }!!
        val fields = Json.decodeFromString<List<FieldDef>>(saved.schemaJson)
        assertEquals(original.uuid, saved.uuid)
        assertEquals(originalField, fields.first())
        assertWeekday(fields.single { it.type == FieldType.DAY_OF_WEEK }.choices)
        assertEquals(answer, runBlocking { db.logEntryDao().getById(entryId) }!!.valuesJson)
    }

    @Test fun addWeekdayToAnExistingIdeaLogWithSavedIdeas() {
        val originalField = IdeaFieldDef(id = StableUuid.createNew(), label = "Old Title", type = FieldType.TEXT)
        val original = IdeaLog(uuid = StableUuid.createNew(), name = "Existing Ideas", createdAt = 1,
            fieldsJson = Json.encodeToString(listOf(originalField)))
        val id = runBlocking { db.ideaLogDao().insert(original) }
        val answer = EntryValues.encode(mapOf(originalField.id to EntryValues.string("Keep this idea")))
        val entryId = runBlocking { db.ideaEntryDao().insert(IdeaEntry(ideaLogId = id, createdAt = "2026-11-01T12:00:00Z", valuesJson = answer)) }
        val vm = IdeaLogEditorViewModel(app).also { store.put("ideas", it) }
        var saved = false
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            EditIdeaLogScreen(ideaLogId = id.toString(), onBack = { saved = true }, viewModel = vm)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Old Title").fetchSemanticsNodes().isNotEmpty() }
        waitForAddField()
        compose.onNodeWithText("Add Field", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Text (Multiline)").performScrollTo().performClick()
        selectWeekday()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { saved }
        val log = runBlocking { db.ideaLogDao().getById(id) }!!
        val fields = Json.decodeFromString<List<IdeaFieldDef>>(log.fieldsJson)
        assertEquals(original.uuid, log.uuid)
        assertEquals(originalField, fields.first())
        assertWeekday(fields.single { it.type == FieldType.DAY_OF_WEEK }.choices)
        assertEquals(answer, runBlocking { db.ideaEntryDao().getById(entryId) }!!.valuesJson)
    }

    @Test fun addWeekdayToAnExistingClickerLogWithoutChangingItsZeroCountCard() {
        val originalField = ClickerField(id = StableUuid.createNew(), label = "Counter", type = ClickerFieldType.CLICK_TRACKER)
        val original = ClickerLog(uuid = StableUuid.createNew(), title = "Existing Clicker", createdAt = 1, lastAccessedAt = 1,
            fieldsJson = Json.encodeToString(listOf(originalField)))
        val id = runBlocking { db.clickerDao().insertLog(original) }
        val answer = ClickerValues.encode(mapOf(originalField.id to "0"))
        val card = ClickerCard(clickerLogId = id, uuid = StableUuid.createNew(), createdAt = 1, valuesJson = answer)
        val cardId = runBlocking { db.clickerDao().insertCard(card) }
        val vm = ClickerSetupViewModel(app).also { store.put("clicker", it) }
        var saved = false
        compose.setContent { DataDragonTheme(dynamicColor = false) {
            ClickerSetupScreen(existingLogId = id, onBack = { saved = true }, viewModel = vm)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Counter").fetchSemanticsNodes().isNotEmpty() }
        waitForAddField()
        compose.onNodeWithText("Add Field", substring = true).performScrollTo().performClick()
        selectWeekday()
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { saved }
        val log = runBlocking { db.clickerDao().getLog(id) }!!
        val fields = Json.decodeFromString<List<ClickerField>>(log.fieldsJson)
        assertEquals(original.uuid, log.uuid)
        assertEquals(originalField, fields.first())
        assertWeekday(fields.single { it.type == ClickerFieldType.DAY_OF_WEEK }.choices)
        val savedCard = runBlocking { db.clickerDao().getCard(cardId) }!!
        assertEquals(card.uuid, savedCard.uuid)
        assertEquals(answer, savedCard.valuesJson)
    }

    private fun waitForAddField() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add Field", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectWeekday() {
        compose.onNodeWithText("Day of the Week").performScrollTo().assertIsDisplayed().performClick()
    }

    private fun assertWeekday(choices: List<ChoiceOption>) {
        assertEquals((1..7).toSet(), choices.mapNotNull { it.weekday }.toSet())
        assertEquals(7, choices.map { it.id }.toSet().size)
        assertTrue(choices.none { it.id.isBlank() || it.retired })
    }
}
