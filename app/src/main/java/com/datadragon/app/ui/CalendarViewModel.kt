package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.Calendar
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Backs the calendar viewing screen: the form's name (for the "[Form Name]
 * Calendar" header) and its configured calendars (for the View Calendar dropdown
 * and the calendar being displayed).
 *
 * The day-coloring calculation and the day's logs are added by later phases.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val templateDao = db.logTemplateDao()
    private val calendarDao = db.calendarDao()
    private val entryDao = db.logEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private var loaded = false

    private val _formName = MutableStateFlow<String?>(null)
    val formName: StateFlow<String?> = _formName

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val _calendars = MutableStateFlow<List<Calendar>>(emptyList())
    val calendars: StateFlow<List<Calendar>> = _calendars

    /** Every entry of this form — the raw material the calculator reduces per day. */
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun load(templateId: Long) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val template = templateDao.getById(templateId)
            _formName.value = template?.name
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
        }
        viewModelScope.launch {
            calendarDao.observeForTemplate(templateId).collect { _calendars.value = it }
        }
        viewModelScope.launch {
            entryDao.observeForTemplate(templateId).collect { _entries.value = it }
        }
    }
}
