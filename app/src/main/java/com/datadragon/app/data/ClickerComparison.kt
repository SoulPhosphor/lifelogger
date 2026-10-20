package com.datadragon.app.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/**
 * Comparison fields on Clicker cards. A card keeps its values as a map of field
 * id to string ([ClickerValues]), so a Comparison answer is stored there as the
 * text of the same JSON row array a form entry stores ([ComparisonValues]):
 * each row names its item by the option's permanent id.
 */
object ClickerComparison {

    private val json = Json { ignoreUnknownKeys = true }
    private val fieldsSerializer = ListSerializer(ClickerField.serializer())

    /** The saved rows of the Comparison field [fieldId] on a card. */
    fun rows(values: Map<String, String>, fieldId: String): List<ComparisonRow> =
        values[fieldId]
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            .let(ComparisonValues::decode)

    /**
     * The stored text for [rows] of [field], or null when there is nothing to
     * keep. With clicker counting an item's untouched Starting Number is its
     * value. A row with a number but no item yet is kept as a draft, since the
     * card face saves as it changes; it is never counted as an answer.
     */
    fun stored(field: FieldDef, rows: List<ComparisonRow>): String? =
        ComparisonValues.encode(ComparisonValues.withStartingNumbers(field, rows), keepDrafts = true)?.toString()

    /** [valuesJson] with every row that picked [optionId] in field [fieldId] removed. */
    fun withoutOption(valuesJson: String, fieldId: String, optionId: String): String {
        val values = ClickerValues.decode(valuesJson)
        val raw = values[fieldId] ?: return valuesJson
        val stored = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonArray ?: return valuesJson
        val kept = ComparisonValues.withoutItem(stored, optionId)
        if (kept == stored) return valuesJson
        val updated = values.toMutableMap()
        if (kept == null) updated.remove(fieldId) else updated[fieldId] = kept.toString()
        return ClickerValues.encode(updated)
    }

    /** The log's fields, or null when they can't be read. */
    fun decodeFields(fieldsJson: String): List<ClickerField>? =
        runCatching { json.decodeFromString(fieldsSerializer, fieldsJson) }.getOrNull()

    fun encodeFields(fields: List<ClickerField>): String = json.encodeToString(fieldsSerializer, fields)

    /**
     * The most digits any saved number of Comparison field [fieldId] has across
     * [cards] — the lowest its Max Digits may be set to. Null when nothing is saved.
     */
    fun savedDigits(cards: List<ClickerCard>, fieldId: String): Int? =
        cards
            .flatMap { rows(ClickerValues.decode(it.valuesJson), fieldId) }
            .flatMap { listOf(it.first, it.second) }
            .maxOfOrNull { number -> number.count(Char::isDigit) }
            ?.takeIf { it > 0 }

    /**
     * Add [additions] as new items of Comparison field [fieldId] in the log
     * [log], the same way a form adds options: trimmed, auto-capitalized when
     * [capitalize] is on, and any that repeat an existing item (active or
     * retired, ignoring capitalization) dropped. Returns the updated log, or
     * null when nothing changed, and the new items' ids in order.
     */
    fun withNewOptions(
        log: ClickerLog,
        fieldId: String,
        additions: List<String>,
        capitalize: Boolean,
    ): Pair<ClickerLog?, List<String>> {
        val fields = decodeFields(log.fieldsJson) ?: return null to emptyList()
        val added = mutableListOf<String>()
        val updated = fields.map { field ->
            if (field.id != fieldId || field.type != ClickerFieldType.COMPARISON) return@map field
            var choices = field.choices
            additions
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (capitalize) TitleCase.apply(it) else it }
                .forEach { label ->
                    if (choices.matchingLabel(label) == null) {
                        val option = ChoiceOption.createNew(label)
                        choices = ChoiceEdits.withNew(choices, option)
                        added += option.id
                    }
                }
            field.copy(choices = choices)
        }
        return (if (updated == fields) null else log.copy(fieldsJson = encodeFields(updated))) to added
    }

    /**
     * Add [additions] to Comparison field [fieldId] of log [logId] and save the
     * log right away ([withNewOptions]); returns the new items' ids in order.
     */
    suspend fun addOptions(
        dao: ClickerDao,
        logId: Long,
        fieldId: String,
        additions: List<String>,
        capitalize: Boolean,
    ): List<String> {
        val log = dao.getLog(logId) ?: return emptyList()
        val (updated, added) = withNewOptions(log, fieldId, additions, capitalize)
        updated?.let { dao.updateLog(it) }
        return added
    }

    /** Restore retired item [optionId] of field [fieldId] in log [logId] and save it right away. */
    suspend fun restoreOption(dao: ClickerDao, logId: Long, fieldId: String, optionId: String) {
        val log = dao.getLog(logId) ?: return
        withRestoredOption(log, fieldId, optionId)?.let { dao.updateLog(it) }
    }

    /** The log with retired item [optionId] of field [fieldId] restored, keeping its id; null when unchanged. */
    fun withRestoredOption(log: ClickerLog, fieldId: String, optionId: String): ClickerLog? {
        val fields = decodeFields(log.fieldsJson) ?: return null
        val updated = fields.map { field ->
            if (field.id == fieldId) field.copy(choices = ChoiceEdits.restored(field.choices, optionId)) else field
        }
        return if (updated == fields) null else log.copy(fieldsJson = encodeFields(updated))
    }

    /** The log with item [optionId] of field [fieldId] permanently removed; null when unchanged. */
    fun withoutOptionInLog(log: ClickerLog, fieldId: String, optionId: String): ClickerLog? {
        val fields = decodeFields(log.fieldsJson) ?: return null
        val updated = fields.map { field ->
            if (field.id == fieldId) field.copy(choices = ChoiceEdits.withoutId(field.choices, optionId)) else field
        }
        return if (updated == fields) null else log.copy(fieldsJson = encodeFields(updated))
    }
}
