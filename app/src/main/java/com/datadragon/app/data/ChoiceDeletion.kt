package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The data side of permanently deleting one option (Forms) or category
 * (Ideas). Pure: callers read the stored JSON, apply the result, and write it
 * back inside one database transaction so a deletion is never half-applied.
 *
 * Only the deleted option is touched. Every entry is kept; a Single Choice
 * answer that held it becomes unanswered, and a Multiple Choice answer keeps
 * all of its other selections. Settings that pointed at the option (a
 * calendar's Count Matching target) are cleared. Backup files already written
 * are not — and cannot be — changed.
 */
object ChoiceDeletion {

    private val json = Json { ignoreUnknownKeys = true }

    data class FormResult(
        val schemaJson: String,
        val entryValues: List<String>,
        val calendarConfigs: List<String>,
    )

    data class IdeaResult(
        val fieldsJson: String,
        val entryValues: List<String>,
    )

    /**
     * Remove option [optionId] from field [fieldId] of a Form: from the schema,
     * from every entry's answer, and from any calendar counting it. Returns null
     * when the schema can't be read, so nothing is written.
     */
    fun fromForm(
        schemaJson: String,
        entryValues: List<String>,
        calendarConfigs: List<String>,
        fieldId: String,
        optionId: String,
    ): FormResult? {
        val fields = runCatching { json.decodeFromString(ListSerializer(FieldDef.serializer()), schemaJson) }
            .getOrNull() ?: return null
        val updated = fields.map { field ->
            if (field.id == fieldId) field.copy(choices = ChoiceEdits.withoutId(field.choices, optionId)) else field
        }
        return FormResult(
            schemaJson = if (updated == fields) schemaJson else FormMarkdownParser.encodeFields(updated),
            entryValues = entryValues.map { EntryValues.withoutOption(it, fieldId, optionId) },
            calendarConfigs = calendarConfigs.map { configJson ->
                if (configJson.isBlank()) return@map configJson
                val config = CalendarConfigCodec.decode(configJson)
                if (config.sourceField == fieldId && config.matchOption == optionId) {
                    CalendarConfigCodec.encode(config.copy(matchOption = null))
                } else {
                    configJson
                }
            },
        )
    }

    /**
     * Remove category [categoryId] from Categories field [fieldId] of an Idea
     * Log and from every idea that chose it. Returns null when the fields can't
     * be read, so nothing is written.
     */
    fun fromIdeaLog(
        fieldsJson: String,
        entryValues: List<String>,
        fieldId: String,
        categoryId: String,
    ): IdeaResult? {
        val fields = runCatching { json.decodeFromString(ListSerializer(IdeaFieldDef.serializer()), fieldsJson) }
            .getOrNull() ?: return null
        val updated = fields.map { field ->
            if (field.id == fieldId) field.copy(choices = ChoiceEdits.withoutId(field.choices, categoryId)) else field
        }
        return IdeaResult(
            fieldsJson = if (updated == fields) {
                fieldsJson
            } else {
                json.encodeToString(ListSerializer(IdeaFieldDef.serializer()), updated)
            },
            entryValues = entryValues.map { EntryValues.withoutOption(it, fieldId, categoryId) },
        )
    }
}
