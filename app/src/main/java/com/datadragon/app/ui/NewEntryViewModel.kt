package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

    fun load(id: Long) {
        templateId = id
        viewModelScope.launch {
            val template = templateDao.getById(id)
            _logName.value = template?.name
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()
        }
    }

    /**
     * Persist a new entry. [values] is keyed by field label (plus the reserved
     * notes key); the timestamp is generated here, ISO-8601 with offset.
     */
    fun save(values: Map<String, JsonElement>, onSaved: () -> Unit) {
        if (templateId < 0) return
        viewModelScope.launch {
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
            onSaved()
        }
    }
}
