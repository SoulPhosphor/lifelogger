package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.EntryNote
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
import kotlinx.coroutines.flow.map
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
    private val noteDao = db.entryNoteDao()
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

    /** Append-only follow-up notes for this log, grouped by the entry they belong to. */
    val notesByEntry: StateFlow<Map<Long, List<EntryNote>>> = templateId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else noteDao.observeForTemplate(id)
        }
        .map { notes -> notes.groupBy { it.entryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

    /** Delete one entry and any follow-up notes attached to it. */
    fun deleteEntry(entry: LogEntry) {
        viewModelScope.launch {
            db.withTransaction {
                noteDao.deleteForEntry(entry.id)
                entryDao.delete(entry)
            }
        }
    }

    /** Append a time-stamped follow-up note to an entry (append-only). */
    fun addNote(entry: LogEntry, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            noteDao.insert(
                EntryNote(entryId = entry.id, createdAt = BackupRepository.now(), text = trimmed)
            )
        }
    }

    /**
     * One-way unlock: a locked log becomes editable. It can never be re-locked,
     * so a log still showing as locked has never been editable.
     */
    fun unlockLog() {
        val current = _template.value ?: return
        if (!current.locked) return
        viewModelScope.launch {
            templateDao.unlock(current.id)
            _template.value = current.copy(locked = false)
        }
    }

    /**
     * Delete this whole log and all of its entries, then invoke [onDeleted]
     * (used to navigate away). Runs in one transaction.
     */
    fun deleteLog(onDeleted: () -> Unit) {
        val template = _template.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                noteDao.deleteForTemplate(template.id)
                entryDao.deleteForTemplate(template.id)
                templateDao.delete(template)
            }
            onDeleted()
        }
    }
}
