package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The one-time conversion from label-keyed Forms data to permanent field and
 * option identities, and from option text to option identities for Idea
 * Categories. It is shared by the Room upgrade (`MIGRATION_23_24`) and by
 * restoring a backup written before identities existed, so both paths produce
 * exactly the same shape.
 *
 * It is pure: it reads stored JSON strings and returns new ones, and touches a
 * database only through its callers, which apply the result inside one
 * transaction. The conversion is safe to repeat: a field that already has an id
 * (Forms) or already has [ChoiceOption]s (Idea Categories) is left exactly as it
 * is, so no established identity is ever regenerated or rewritten.
 *
 * What it does to a legacy Form:
 * - every field gets a new permanent id, and its entry values move from the
 *   label key to that id (a value shared by two fields with the same label is
 *   copied to both — fields are never merged);
 * - Single / Multiple Choice options become [ChoiceOption]s. Options that are
 *   the same under the duplicate rule ([OptionDuplicates.key]) within one field
 *   are merged into one option that keeps the first occurrence's position and
 *   is pre-selected if either duplicate was;
 * - stored answers become option ids. A Multiple Choice answer that named a
 *   merged option twice keeps it once, with every other selection kept;
 * - a stored answer that matches no current option is preserved as a retired
 *   option (one per distinct value), so no historical answer is lost;
 * - the Multiple Choice `*` marker (only where that switch was on — otherwise a
 *   leading `*` is part of the option's real name and is left alone) and the
 *   Single Choice "top answer" switch become per-option pre-selection;
 * - the default sort field and each calendar's source field, day-timestamp
 *   field and Count Matching option are re-pointed from labels to ids.
 *
 * Keys in an entry that belong to no field (data the old label scheme had
 * already orphaned) are kept untouched rather than discarded.
 */
object FieldIdentityMigration {

    private val json = Json { ignoreUnknownKeys = true }
    private val choicesSerializer = ListSerializer(ChoiceOption.serializer())

    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"
    private const val KEY_TYPE = "type"
    private const val KEY_CHOICES = "choices"
    private const val KEY_OPTIONS = "options"
    private const val KEY_ALLOW_PRESELECTED = "allowPreselectedMultipleChoices"
    private const val KEY_TOP_ANSWER = "topAnswerPreselected"

    /** One Form's stored data, as read from Room or a backup. */
    data class FormData(
        val schemaJson: String,
        val entryValues: List<String>,
        val calendarConfigs: List<String>,
        /** The stored default sort reference: a label before migration, an id after. */
        val sortTimestampField: String?,
    )

    /** One Idea Log's stored data. */
    data class IdeaData(
        val fieldsJson: String,
        val entryValues: List<String>,
    )

    /** True when [schemaJson] still holds a field without a permanent id. */
    fun formNeedsMigration(schemaJson: String): Boolean =
        parseArray(schemaJson)?.any { element ->
            val field = element as? JsonObject ?: return@any false
            stringOf(field[KEY_ID]).isNullOrBlank()
        } ?: false

    /** True when [fieldsJson] still holds a Categories field with legacy option text. */
    fun ideaNeedsMigration(fieldsJson: String): Boolean =
        parseArray(fieldsJson)?.any { element ->
            val field = element as? JsonObject ?: return@any false
            isLegacyCategories(field)
        } ?: false

    /**
     * Convert one Form. Returns [form] unchanged (the same instance) when every
     * field already has an id, or when its schema can't be read — an unreadable
     * schema is preserved as-is rather than risk losing it.
     */
    fun migrateForm(form: FormData, newId: () -> String = StableUuid::createNew): FormData {
        val schema = parseArray(form.schemaJson) ?: return form
        val fieldObjects = schema.map { it as? JsonObject ?: return form }
        if (fieldObjects.none { stringOf(it[KEY_ID]).isNullOrBlank() }) return form

        val entries = form.entryValues.map { EntryValues.decode(it).toMutableMap() }
        val consumedLabels = mutableSetOf<String>()
        // Per migrated field: its new id and its option resolver, for calendars.
        val resolvers = mutableListOf<MigratedField>()

        for (field in fieldObjects) {
            val existingId = stringOf(field[KEY_ID])
            if (!existingId.isNullOrBlank()) {
                resolvers += MigratedField(
                    id = existingId,
                    label = stringOf(field[KEY_LABEL]).orEmpty(),
                    type = typeOf(field),
                    options = null,
                )
                continue
            }
            val label = stringOf(field[KEY_LABEL]).orEmpty()
            val type = typeOf(field)
            val fieldId = newId()
            consumedLabels += label

            val options = if (type?.hasChoices == true) {
                OptionBuilder.fromLegacy(
                    legacy = stringList(field[KEY_OPTIONS]),
                    stripPreselectMarker = type == FieldType.MULTIPLE &&
                        booleanOf(field[KEY_ALLOW_PRESELECTED]),
                    topAnswerPreselected = type == FieldType.DROPDOWN &&
                        booleanOf(field[KEY_TOP_ANSWER]),
                    newId = newId,
                )
            } else {
                null
            }

            entries.forEach { values ->
                val stored = values[label] ?: return@forEach
                values[fieldId] = if (options != null) options.convertValue(stored) else stored
            }

            resolvers += MigratedField(fieldId, label, type, options)
        }

        // Drop the old label keys only after every field has copied its value,
        // so two fields that shared a label both keep it.
        entries.forEach { values ->
            consumedLabels.forEach { label -> if (label != EntryValues.NOTES_KEY) values.remove(label) }
        }

        // Calendars may name an option that no longer exists; resolving it can
        // add a retired option, so they run before the schema is written out.
        val calendars = form.calendarConfigs.map { migrateCalendar(it, resolvers) }

        val schemaOut = JsonArray(
            fieldObjects.indices.map { index ->
                val original = fieldObjects[index]
                val resolver = resolvers[index]
                if (!stringOf(original[KEY_ID]).isNullOrBlank()) {
                    original
                } else {
                    rebuildFormField(original, resolver.id, resolver.options?.result())
                }
            },
        )

        return FormData(
            schemaJson = schemaOut.toString(),
            entryValues = entries.mapIndexed { i, values ->
                val rewritten = EntryValues.encode(values)
                // Leave an entry's stored text byte-for-byte alone when nothing in it changed.
                if (EntryValues.decode(form.entryValues[i]) == JsonObject(values)) form.entryValues[i] else rewritten
            },
            calendarConfigs = calendars,
            sortTimestampField = form.sortTimestampField?.let { reference ->
                resolveField(reference, resolvers)?.id ?: reference
            },
        )
    }

    /**
     * Convert one Idea Log's Categories fields. Idea fields already carry
     * permanent ids and their values are already keyed by them, so only the
     * category text becomes [ChoiceOption]s and the stored category answers
     * become option ids. Returns [idea] unchanged when nothing is legacy.
     */
    fun migrateIdeaLog(idea: IdeaData, newId: () -> String = StableUuid::createNew): IdeaData {
        val fields = parseArray(idea.fieldsJson) ?: return idea
        val fieldObjects = fields.map { it as? JsonObject ?: return idea }
        if (fieldObjects.none(::isLegacyCategories)) return idea

        val entries = idea.entryValues.map { EntryValues.decode(it).toMutableMap() }
        val out = fieldObjects.map { field ->
            if (!isLegacyCategories(field)) return@map field
            val fieldId = stringOf(field[KEY_ID]).orEmpty()
            val options = OptionBuilder.fromLegacy(
                legacy = stringList(field[KEY_OPTIONS]),
                stripPreselectMarker = false,
                topAnswerPreselected = false,
                newId = newId,
            )
            entries.forEach { values ->
                val stored = values[fieldId] ?: return@forEach
                values[fieldId] = options.convertValue(stored)
            }
            JsonObject(
                field.filterKeys { it != KEY_OPTIONS } +
                    (KEY_CHOICES to json.encodeToJsonElement(choicesSerializer, options.result())),
            )
        }
        return IdeaData(
            fieldsJson = JsonArray(out).toString(),
            entryValues = entries.mapIndexed { i, values ->
                if (EntryValues.decode(idea.entryValues[i]) == JsonObject(values)) {
                    idea.entryValues[i]
                } else {
                    EntryValues.encode(values)
                }
            },
        )
    }

    // ---- Internals ----------------------------------------------------------

    private class MigratedField(
        val id: String,
        val label: String,
        val type: FieldType?,
        /** Null for a field that isn't a legacy choice field. */
        val options: OptionBuilder?,
    )

    /**
     * Builds one field's [ChoiceOption] list from legacy option text and maps
     * legacy answer text onto it, adding a retired option for any answer that no
     * longer matches a listed option.
     */
    private class OptionBuilder private constructor(
        private val stripPreselectMarker: Boolean,
        private val newId: () -> String,
    ) {
        private val options = mutableListOf<ChoiceOption>()
        private val idByKey = mutableMapOf<String, String>()

        fun result(): List<ChoiceOption> = options.toList()

        /** The option id for legacy text [text], recovering a retired option if needed. */
        fun idFor(text: String): String {
            val key = OptionDuplicates.key(text, stripPreselectMarker)
            idByKey[key]?.let { return it }
            val recovered = ChoiceOption(id = newId(), label = text, retired = true)
            options += recovered
            idByKey[key] = recovered.id
            return recovered.id
        }

        /** Look an existing option up by id or legacy text, without creating one. */
        fun find(reference: String): String? =
            options.firstOrNull { it.id == reference }?.id
                ?: idByKey[OptionDuplicates.key(reference, stripPreselectMarker)]

        fun convertValue(stored: JsonElement): JsonElement = when (stored) {
            is JsonArray -> {
                val seen = mutableSetOf<String>()
                JsonArray(
                    stored.mapNotNull { element ->
                        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                            ?: return@mapNotNull element
                        val id = idFor(text)
                        if (seen.add(id)) JsonPrimitive(id) else null
                    },
                )
            }
            is JsonPrimitive -> {
                val text = stored.takeIf { it.isString }?.contentOrNull
                if (text.isNullOrBlank()) stored else JsonPrimitive(idFor(text))
            }
            else -> stored
        }

        companion object {
            fun fromLegacy(
                legacy: List<String>,
                stripPreselectMarker: Boolean,
                topAnswerPreselected: Boolean,
                newId: () -> String,
            ): OptionBuilder {
                val builder = OptionBuilder(stripPreselectMarker, newId)
                legacy.forEach { raw ->
                    val trimmed = raw.trim()
                    val marked = stripPreselectMarker && trimmed.startsWith("*") &&
                        trimmed.removePrefix("*").isNotBlank()
                    val label = if (marked) trimmed.removePrefix("*").trimStart() else raw
                    val key = OptionDuplicates.key(raw, stripPreselectMarker)
                    val existingId = builder.idByKey[key]
                    if (existingId != null) {
                        // A duplicate: merge into the first occurrence, keeping
                        // its position and its pre-selection if either had it.
                        if (marked) {
                            val index = builder.options.indexOfFirst { it.id == existingId }
                            builder.options[index] = builder.options[index].copy(preselected = true)
                        }
                    } else {
                        val option = ChoiceOption(id = newId(), label = label, preselected = marked)
                        builder.options += option
                        builder.idByKey[key] = option.id
                    }
                }
                if (topAnswerPreselected && builder.options.isNotEmpty()) {
                    builder.options[0] = builder.options[0].copy(preselected = true)
                }
                return builder
            }
        }
    }

    private fun rebuildFormField(original: JsonObject, id: String, choices: List<ChoiceOption>?): JsonObject {
        val rebuilt = LinkedHashMap<String, JsonElement>()
        rebuilt[KEY_ID] = JsonPrimitive(id)
        original.forEach { (key, value) ->
            if (key != KEY_ID && key != KEY_OPTIONS && key != KEY_ALLOW_PRESELECTED && key != KEY_TOP_ANSWER) {
                rebuilt[key] = value
            }
        }
        if (choices != null) rebuilt[KEY_CHOICES] = json.encodeToJsonElement(choicesSerializer, choices)
        return JsonObject(rebuilt)
    }

    private fun migrateCalendar(configJson: String, fields: List<MigratedField>): String {
        if (configJson.isBlank()) return configJson
        val config = runCatching { json.decodeFromString(CalendarConfig.serializer(), configJson) }
            .getOrNull() ?: return configJson
        val source = config.sourceField?.let { resolveField(it, fields) }
        val timestamp = config.dayTimestampField?.let { resolveField(it, fields) }
        val matchOption = config.matchOption?.let { option ->
            val builder = source?.options
            when {
                builder == null -> option
                // A Count Matching target that no longer exists is kept as a retired option.
                else -> builder.find(option) ?: builder.idFor(option)
            }
        }
        val updated = config.copy(
            sourceField = source?.id ?: config.sourceField,
            dayTimestampField = timestamp?.id ?: config.dayTimestampField,
            matchOption = matchOption,
        )
        return if (updated == config) configJson else CalendarConfigCodec.encode(updated)
    }

    /** A field reference stored as an id (already migrated) or as a legacy label. */
    private fun resolveField(reference: String, fields: List<MigratedField>): MigratedField? =
        fields.firstOrNull { it.id == reference }
            ?: fields.firstOrNull { it.label == reference }
            ?: fields.firstOrNull { it.label.trim() == reference.trim() }

    private fun isLegacyCategories(field: JsonObject): Boolean {
        val type = typeOf(field)
        return type == FieldType.DROPDOWN && field[KEY_CHOICES] == null && field[KEY_OPTIONS] != null
    }

    private fun typeOf(field: JsonObject): FieldType? {
        val token = stringOf(field[KEY_TYPE]) ?: return null
        return FieldType.entries.firstOrNull { it.name == token } ?: FieldType.fromToken(token)
    }

    private fun parseArray(text: String): JsonArray? =
        runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()

    private fun stringOf(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun booleanOf(element: JsonElement?): Boolean =
        (element as? JsonPrimitive)?.booleanOrNull ?: false

    private fun stringList(element: JsonElement?): List<String> =
        (element as? JsonArray)?.mapNotNull { stringOf(it) }.orEmpty()
}
