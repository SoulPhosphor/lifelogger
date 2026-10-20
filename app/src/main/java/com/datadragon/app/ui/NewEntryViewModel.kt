package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import com.datadragon.app.data.OptionDuplicates
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.data.TitleCase
import com.datadragon.app.data.ChoiceEdits
import com.datadragon.app.data.ChoiceOption
import com.datadragon.app.data.hasOptionList
import com.datadragon.app.data.matchingLabel
import com.datadragon.app.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Backs the New Entry screen. It loads the log's schema so the form can be
 * generated from it (Phase 4) and writes a new [LogEntry] on save.
 */
class NewEntryViewModel(app: Application) : AndroidViewModel(app) {

    private val templateDao = AppDatabase.getInstance(app).logTemplateDao()
    private val entryDao = AppDatabase.getInstance(app).logEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private var templateId: Long = -1

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val _logName = MutableStateFlow<String?>(null)
    val logName: StateFlow<String?> = _logName

    /**
     * The form's "Allow Additions from Form" setting: shows "+" on Dropdown/Multiple
     * fields and New Option on Comparison fields.
     */
    private val _allowAdditionsFromForm = MutableStateFlow(false)
    val allowAdditionsFromForm: StateFlow<Boolean> = _allowAdditionsFromForm

    // The entry being edited, when this screen was opened for an existing entry.
    private var editing: LogEntry? = null
    private val _initialValues = MutableStateFlow<JsonObject?>(null)
    /** Stored values to pre-fill the form when editing; null for a new entry. */
    val initialValues: StateFlow<JsonObject?> = _initialValues

    /** [entryId] is null for a new entry, or the id of an entry being edited. */
    fun load(id: Long, entryId: Long? = null) {
        templateId = id
        viewModelScope.launch {
            val template = templateDao.getById(id)
            _logName.value = template?.name
            _allowAdditionsFromForm.value = template?.allowAdditionsFromForm ?: false
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
            if (entryId != null) {
                val entry = entryDao.getById(entryId)
                editing = entry
                _initialValues.value = entry?.let { EntryValues.decode(it.valuesJson) }
            }
        }
    }

    /**
     * Add [additions] as new options of the Dropdown/Multiple/Comparison field
     * [fieldId] and save them to the form right away, so they are live on this
     * entry form and every later one. Each new option gets a new permanent id and
     * goes after the existing active options; they are auto-capitalized when that
     * setting is on, and any that repeat an existing option — active or retired,
     * ignoring capitalization — are dropped. Stored entries are untouched.
     * [onAdded] then receives the new options' ids, in order.
     */
    fun addOptions(fieldId: String, additions: List<String>, onAdded: (List<String>) -> Unit = {}) {
        if (templateId < 0 || additions.isEmpty()) return
        val capitalize = SettingsRepository(getApplication()).autoCapitalizeOptions
        val added = mutableListOf<String>()
        updateField(fieldId, onDone = { onAdded(added) }) { field ->
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
    }

    /**
     * Restore the retired option [optionId] of field [fieldId] with its original
     * id, so every answer already saved against it comes back with it. [onDone]
     * runs once the form holds it again.
     */
    fun restoreOption(fieldId: String, optionId: String, onDone: () -> Unit = {}) {
        if (templateId < 0) return
        updateField(fieldId, onDone) { field -> field.copy(choices = ChoiceEdits.restored(field.choices, optionId)) }
    }

    /** Apply [change] to field [fieldId] and save the form; [onDone] runs once it is saved. */
    private fun updateField(fieldId: String, onDone: () -> Unit = {}, change: (FieldDef) -> FieldDef) {
        viewModelScope.launch {
            val template = templateDao.getById(templateId) ?: return@launch
            val current = runCatching { json.decodeFromString<List<FieldDef>>(template.schemaJson) }
                .getOrNull() ?: return@launch
            val updated = current.map { field ->
                if (field.id != fieldId || !field.type.hasOptionList) field else change(field)
            }
            if (updated == current) return@launch
            templateDao.updateSchema(
                templateId,
                FormMarkdownParser.encodeFields(updated),
                FormMarkdownGenerator.generate(template.name, updated),
            )
            _fields.value = updated
            onDone()
        }
    }

    /**
     * Persist the entry. When editing an existing entry its original `createdAt`
     * is kept and `updatedAt` is stamped; otherwise a new entry is inserted with
     * the current time. [values] is keyed by field id plus the reserved notes
     * key; timestamps are ISO-8601 with offset.
     */
    fun save(values: Map<String, JsonElement>, onSaved: () -> Unit) {
        if (templateId < 0) return
        viewModelScope.launch {
            val existing = editing
            if (existing != null) {
                entryDao.update(
                    existing.copy(
                        valuesJson = EntryValues.encode(values),
                        updatedAt = BackupRepository.now(),
                    )
                )
            } else {
                val now = OffsetDateTime.now()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                entryDao.insert(
                    LogEntry(
                        templateId = templateId,
                        createdAt = now,
                        valuesJson = EntryValues.encode(values),
                    )
                )
            }
            onSaved()
        }
    }
}
