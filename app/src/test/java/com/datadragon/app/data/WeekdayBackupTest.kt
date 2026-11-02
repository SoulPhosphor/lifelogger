package com.datadragon.app.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WeekdayBackupTest {
    private val days = Weekday.newChoices()
    private val settings = WeekdaySettings(defaultEnteredDay = false, onlyOne = false, choiceStyle = ChoiceStyle.DISPLAYED, weekdayIds = Weekday.identities(days))
    private val choices = ChoiceEdits.retired(ChoiceEdits.renamed(days, days.first().id, "Munday"), days.last().id) + ChoiceOption.createNew("Odd Day", preselected = true)
    private val field = FieldDef(id = StableUuid.createNew(), label = "My Days", type = FieldType.DAY_OF_WEEK, required = true, choices = choices, weekday = settings)
    private val values = EntryValues.encode(mapOf(field.id to EntryValues.stringArray(listOf(days.first().id, days.last().id))))
    private fun formPayload() = BackupPayload(forms = listOf(BackupCodec.logOf(
        LogTemplate(uuid = StableUuid.createNew(), name = "Form", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(listOf(field))),
        listOf(LogEntry(templateId = 1, createdAt = "2026-11-02T12:00:00Z", valuesJson = values)),
    )))
    private fun ideaPayload() = BackupPayload(ideaLogs = listOf(BackupIdeaLog(
        uuid = StableUuid.createNew(), name = "Ideas", createdAt = 1,
        fieldsJson = Json.encodeToString(listOf(IdeaFieldDef(id = field.id, label = field.label, type = field.type, required = true, choices = choices, weekday = settings))),
        automaticTimestamping = false, allowArchiving = true, showEntireIdeaCard = false, previewLines = 20,
        sortTimestampFieldId = null, sortNewestFirst = true,
        entries = listOf(BackupIdeaEntry("2026-11-02T12:00:00Z", null, values, false, false)),
    )))
    private fun clickerPayload() = BackupPayload(clickerData = listOf(BackupCodec.clickerOf(
        ClickerLog(uuid = StableUuid.createNew(), title = "Data", createdAt = 1, lastAccessedAt = 1,
            fieldsJson = Json.encodeToString(listOf(ClickerField(id = field.id, label = field.label, type = ClickerFieldType.DAY_OF_WEEK, required = true, choices = choices, weekday = settings)))),
        listOf(ClickerCard(clickerLogId = 1, uuid = StableUuid.createNew(), createdAt = 1,
            valuesJson = ClickerValues.encode(mapOf(field.id to Weekday.clickerValue(listOf(days.first().id, days.last().id)))))),
    )))

    @Test fun everyModeCarriesSettingsAssociationsRetiredOptionsAndValuesThroughBackup() {
        listOf(BackupCategory.FORMS to formPayload(), BackupCategory.IDEA_LOGS to ideaPayload(), BackupCategory.CLICKER_DATA to clickerPayload()).forEach { (category, payload) ->
            val backup = BackupFile.single(exportedAt = "2026-11-02T12:00:00Z", category = category, payload = payload)
            val encoded = BackupCodec.encode(backup)
            val restored = BackupCodec.decode(encoded)
            BackupRestoreValidator.validate(restored)
            assertEquals(15, restored.version)
            assertEquals(payload, restored.payload)
            assertEquals(encoded, BackupCodec.encode(restored))
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(encoded.replace("\"version\": 15", "\"version\": 14")) }
        }
    }

    @Test fun aFullMultiModePayloadAndRepeatedDecodingKeepTheExactUUIDs() {
        val payload = BackupPayload(forms = formPayload().forms, ideaLogs = ideaPayload().ideaLogs, clickerData = clickerPayload().clickerData)
        val backup = BackupFile(exportedAt = "2026-11-02T12:00:00Z", includedCategories = listOf(BackupCategory.FORMS, BackupCategory.IDEA_LOGS, BackupCategory.CLICKER_DATA), payload = payload)
        val encoded = BackupCodec.encode(backup)
        repeat(3) { assertEquals(payload, BackupCodec.decode(encoded).payload) }
    }

    @Test fun enablingEnteredDayRestoresOriginalIdentitiesAndBackupRejectsMissingActiveDays() {
        val bad = field.copy(weekday = settings.copy(defaultEnteredDay = true))
        val template = LogTemplate(uuid = StableUuid.createNew(), name = "Form", createdAt = 1, schemaJson = FormMarkdownParser.encodeFields(listOf(bad)))
        val backup = BackupFile.single(exportedAt = "2026-11-02T12:00:00Z", category = BackupCategory.FORMS, payload = BackupPayload(forms = listOf(BackupCodec.logOf(template, emptyList()))))
        assertThrows(IllegalArgumentException::class.java) { BackupRestoreValidator.validate(backup) }
        val restored = Weekday.restoreWeekdays(choices, Weekday.seeds(settings))
        assertEquals(days.last().id, restored.single { it.weekday == 7 }.id)
        assertEquals("Munday", restored.single { it.weekday == 1 }.label)
    }
    @Test fun immediateDeletionKeepsSavedWeekdaySettingsValidEvenIfTheEditorIsThenCancelled() {
        val on = field.copy(choices = days, weekday = settings.copy(defaultEnteredDay = true))
        val optionId = days.first().id
        val answer = EntryValues.encode(mapOf(field.id to EntryValues.stringArray(listOf(optionId, days[1].id))))
        val formResult = ChoiceDeletion.fromForm(FormMarkdownParser.encodeFields(listOf(on)), listOf(answer), emptyList(), field.id, optionId)!!
        val form = Json.decodeFromString<List<FieldDef>>(formResult.schemaJson).single()
        assertFalse(form.weekday.defaultEnteredDay)
        assertEquals(settings.weekdayIds, form.weekday.weekdayIds)
        assertFalse(form.choices.any { it.id == optionId })
        assertEquals(listOf(days[1].id), Weekday.selected(EntryValues.decode(formResult.entryValues.single())[field.id]))
        val formPayload = formPayload().let { it.copy(forms = it.forms!!.map { log -> log.copy(schemaJson = formResult.schemaJson) }) }
        BackupRestoreValidator.validate(BackupFile.single("now", BackupCategory.FORMS, formPayload))

        val idea = IdeaFieldDef(id = field.id, label = field.label, type = field.type, choices = days, weekday = on.weekday)
        val ideaResult = ChoiceDeletion.fromIdeaLog(Json.encodeToString(listOf(idea)), listOf(answer), field.id, optionId)!!
        val savedIdea = Json.decodeFromString<List<IdeaFieldDef>>(ideaResult.fieldsJson).single()
        assertFalse(savedIdea.weekday.defaultEnteredDay)
        assertEquals(settings.weekdayIds, savedIdea.weekday.weekdayIds)
        assertEquals(listOf(days[1].id), Weekday.selected(EntryValues.decode(ideaResult.entryValues.single())[field.id]))
        val ideaPayload = ideaPayload().let { it.copy(ideaLogs = it.ideaLogs!!.map { log -> log.copy(fieldsJson = ideaResult.fieldsJson) }) }
        BackupRestoreValidator.validate(BackupFile.single("now", BackupCategory.IDEA_LOGS, ideaPayload))

        val clicker = ClickerField(id = field.id, label = field.label, type = ClickerFieldType.DAY_OF_WEEK, choices = days, weekday = on.weekday)
        val log = ClickerLog(uuid = StableUuid.createNew(), title = "Test", createdAt = 1, lastAccessedAt = 1, fieldsJson = Json.encodeToString(listOf(clicker)))
        val updated = ClickerComparison.withoutOptionInLog(log, field.id, optionId)!!
        val savedClicker = Json.decodeFromString<List<ClickerField>>(updated.fieldsJson).single()
        assertFalse(savedClicker.weekday.defaultEnteredDay)
        assertEquals(settings.weekdayIds, savedClicker.weekday.weekdayIds)
        BackupRestoreValidator.validate(BackupFile.single("now", BackupCategory.CLICKER_DATA, BackupPayload(clickerData = listOf(BackupCodec.clickerOf(updated, emptyList())))))

        val custom = ChoiceOption.createNew("Extra")
        assertEquals(on.weekday, Weekday.afterDeleting(on.weekday, days + custom, custom.id))
        assertEquals(days.first().id, Weekday.restoreWeekdays(form.choices, Weekday.seeds(form.weekday)).single { it.weekday == 1 }.id)
    }

}
