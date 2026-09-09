package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.IdeaEntry
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaValues
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
 * Backs the New Idea / Edit Idea screen. It loads the Idea Log's field schema so
 * the form can be generated from it, and writes the idea on save.
 *
 * Ideas are always editable, so saving an edit updates the existing row rather
 * than adding a second one. A new idea is always created active, even when the
 * archive view was on screen when "+" was pressed.
 */
class NewIdeaViewModel(app: Application) : AndroidViewModel(app) {

    private val logDao = AppDatabase.getInstance(app).ideaLogDao()
    private val entryDao = AppDatabase.getInstance(app).ideaEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private var ideaLogId: Long = -1

    private val _fields = MutableStateFlow<List<IdeaFieldDef>>(emptyList())
    val fields: StateFlow<List<IdeaFieldDef>> = _fields

    private val _automaticTimestamping = MutableStateFlow(false)
    val automaticTimestamping: StateFlow<Boolean> = _automaticTimestamping

    private var editing: IdeaEntry? = null

    private val _initialValues = MutableStateFlow<JsonObject?>(null)
    /** Stored values to pre-fill the form when editing; null for a new idea. */
    val initialValues: StateFlow<JsonObject?> = _initialValues

    /** [ideaId] is null for a new idea, or the id of the idea being edited. */
    fun load(logId: Long, ideaId: Long? = null) {
        ideaLogId = logId
        viewModelScope.launch {
            val log = logDao.getById(logId)
            _automaticTimestamping.value = log?.automaticTimestamping ?: false
            _fields.value = log
                ?.let { runCatching { json.decodeFromString<List<IdeaFieldDef>>(it.fieldsJson) }.getOrNull() }
                ?: emptyList()
            if (ideaId != null) {
                val entry = entryDao.getById(ideaId)
                editing = entry
                _initialValues.value = entry?.let { IdeaValues.decode(it.valuesJson) }
            }
        }
    }

    /**
     * Persist the idea. Editing keeps the original `createdAt` and stamps
     * `updatedAt`; a new idea is inserted with the current time, active.
     */
    fun save(values: Map<String, JsonElement>, onSaved: () -> Unit) {
        if (ideaLogId < 0) return
        viewModelScope.launch {
            val existing = editing
            if (existing != null) {
                entryDao.update(
                    existing.copy(
                        valuesJson = IdeaValues.encode(values),
                        updatedAt = BackupRepository.now(),
                    )
                )
            } else {
                val now = OffsetDateTime.now()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                entryDao.insert(
                    IdeaEntry(
                        ideaLogId = ideaLogId,
                        createdAt = now,
                        valuesJson = IdeaValues.encode(values),
                    )
                )
            }
            onSaved()
        }
    }
}
