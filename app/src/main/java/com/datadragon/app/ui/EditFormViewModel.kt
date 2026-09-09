package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Backs "Edit Form": loads a form's current title and fields and writes edits
 * back while preserving stored entry values.
 */
class EditFormViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).logTemplateDao()
    private val entryDao = AppDatabase.getInstance(app).logEntryDao()
    private val json = Json { ignoreUnknownKeys = true }
    private var templateId: Long = -1

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val _automaticTimestamping = MutableStateFlow(false)
    val automaticTimestamping: StateFlow<Boolean> = _automaticTimestamping

    private val _sortTimestampLabel = MutableStateFlow<String?>(null)
    val sortTimestampLabel: StateFlow<String?> = _sortTimestampLabel

    private val _sortNewestFirst = MutableStateFlow(true)
    val sortNewestFirst: StateFlow<Boolean> = _sortNewestFirst

    fun load(id: Long) {
        templateId = id
        viewModelScope.launch {
            val template = dao.getById(id)
            _automaticTimestamping.value = template?.automaticTimestamping ?: false
            _sortTimestampLabel.value = template?.sortTimestampLabel
            _sortNewestFirst.value = template?.sortNewestFirst ?: true
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
            // Name is the screen's loaded sentinel, so publish it after the
            // settings and fields it gates have been populated.
            _name.value = template?.name
        }
    }

    /**
     * Write back the edited schema. When a field's label or an option was renamed,
     * [labelRenames] (old→new label) and [optionRenames] (current label → old→new
     * option) re-key the already-submitted entries first, so their values stay
     * attached under the new spelling without changing the values themselves.
     */
    fun save(
        name: String,
        fields: List<FieldDef>,
        automaticTimestamping: Boolean,
        sortTimestampLabel: String?,
        sortNewestFirst: Boolean,
        labelRenames: Map<String, String> = emptyMap(),
        optionRenames: Map<String, Map<String, String>> = emptyMap(),
        onSaved: () -> Unit,
    ) {
        if (templateId < 0) return
        viewModelScope.launch {
            if (labelRenames.isNotEmpty() || optionRenames.isNotEmpty()) {
                entryDao.getForTemplateOnce(templateId).forEach { entry ->
                    val rekeyed = EntryValues.rekey(entry.valuesJson, labelRenames, optionRenames)
                    if (rekeyed != entry.valuesJson) {
                        entryDao.update(entry.copy(valuesJson = rekeyed))
                    }
                }
            }
            val savedName = name.trim()
            val markdown = FormMarkdownGenerator.generate(savedName, fields)
            dao.updateForm(
                templateId,
                savedName,
                FormMarkdownParser.encodeFields(fields),
                markdown,
                automaticTimestamping,
                sortTimestampLabel,
                sortNewestFirst,
            )
            _name.value = savedName
            _fields.value = fields
            _automaticTimestamping.value = automaticTimestamping
            _sortTimestampLabel.value = sortTimestampLabel
            _sortNewestFirst.value = sortNewestFirst
            onSaved()
        }
    }
}
