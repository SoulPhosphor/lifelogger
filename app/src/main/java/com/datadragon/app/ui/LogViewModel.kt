package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Backs the single-log (entry list) screen. It loads the template (for the log
 * name and field definitions) and observes that log's entries, newest first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val templateDao = db.logTemplateDao()
    private val entryDao = db.logEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _template = MutableStateFlow<LogTemplate?>(null)
    val template: StateFlow<LogTemplate?> = _template

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val templateId = MutableStateFlow<Long?>(null)

    val entries: StateFlow<List<LogEntry>> = templateId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else entryDao.observeForTemplate(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(id: Long) {
        templateId.value = id
        viewModelScope.launch {
            val template = templateDao.getById(id)
            _template.value = template
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
        }
    }

    /** Delete one entry. The entry list updates automatically. */
    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch { entryDao.delete(entry) }
    }

    /**
     * Delete this whole log and all of its entries, then invoke [onDeleted]
     * (used to navigate away). Runs in one transaction.
     */
    fun deleteLog(onDeleted: () -> Unit) {
        val template = _template.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                entryDao.deleteForTemplate(template.id)
                templateDao.delete(template)
            }
            onDeleted()
        }
    }
}
