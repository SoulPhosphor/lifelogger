package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.FormMarkdownParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Backs "Edit form": loads a log's current fields and writes back a new schema.
 * Editing may only add fields and reorder them (never rename or delete an
 * existing field), so stored entry values stay keyed correctly.
 */
class EditFormViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).logTemplateDao()
    private val json = Json { ignoreUnknownKeys = true }
    private var templateId: Long = -1

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    fun load(id: Long) {
        templateId = id
        viewModelScope.launch {
            val template = dao.getById(id)
            _name.value = template?.name
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
        }
    }

    fun save(fields: List<FieldDef>, onSaved: () -> Unit) {
        if (templateId < 0) return
        viewModelScope.launch {
            val markdown = FormMarkdownGenerator.generate(_name.value.orEmpty(), fields)
            dao.updateSchema(templateId, FormMarkdownParser.encodeFields(fields), markdown)
            onSaved()
        }
    }
}
