package com.datadragon.app.data

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Converting a saved Idea Log field to Categories. Its ideas still hold the
 * text they were saved with, while a Categories answer is a category id, so on
 * save each such answer is pointed at the category whose name matches it — the
 * same rule the identity migration applies to legacy categories. Answer text
 * that matches no category is kept as a retired category, so no past answer is
 * lost and it can still be renamed, restored or permanently deleted.
 *
 * Pure: the caller writes the result back in one transaction.
 */
object CategoryAdoption {

    data class Result(
        val fields: List<IdeaFieldDef>,
        val entryValues: List<String>,
    )

    /**
     * [previous] is the log's saved field list and [fields] the one about to be
     * saved. Only fields that were saved as another type and are now Categories
     * are converted; everything else, and every unchanged idea, is returned as
     * given.
     */
    fun adopt(
        previous: List<IdeaFieldDef>,
        fields: List<IdeaFieldDef>,
        entryValues: List<String>,
        newId: () -> String = StableUuid::createNew,
    ): Result {
        val previousById = previous.associateBy { it.id }
        val converted = fields.filter { field ->
            val before = previousById[field.id] ?: return@filter false
            field.type == FieldType.DROPDOWN && before.type != FieldType.DROPDOWN
        }.map { it.id }.toSet()
        if (converted.isEmpty()) return Result(fields, entryValues)

        val choicesById = fields.filter { it.id in converted }
            .associate { it.id to it.choices.toMutableList() }
        val values = entryValues.map { valuesJson ->
            val stored = EntryValues.decode(valuesJson)
            var changed = false
            val updated = stored.mapValues { (fieldId, element) ->
                val choices = choicesById[fieldId] ?: return@mapValues element
                val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                if (text.isNullOrBlank() || choices.any { it.id == text }) return@mapValues element
                val key = OptionDuplicates.key(text)
                val id = choices.firstOrNull { OptionDuplicates.key(it.label) == key }?.id
                    ?: ChoiceOption(id = newId(), label = text, retired = true)
                        .also { choices += it }
                        .id
                changed = true
                JsonPrimitive(id)
            }
            if (changed) EntryValues.encode(updated) else valuesJson
        }
        return Result(
            fields = fields.map { field -> choicesById[field.id]?.let { field.copy(choices = it.toList()) } ?: field },
            entryValues = values,
        )
    }
}
