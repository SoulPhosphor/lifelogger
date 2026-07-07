package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.BackupRepository
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Backs the Follow-Up Note screen. It loads the log (for its lock state and
 * fields), the entry (to show its data — read-only when the log is locked,
 * editable when it isn't), and, when editing an existing note, that note's text.
 *
 * Follow-up notes can be added or edited at any time, regardless of the log's
 * lock state; "locked" only keeps the entry's own field content from changing.
 */
class FollowUpNoteViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val templateDao = db.logTemplateDao()
    private val entryDao = db.logEntryDao()
    private val noteDao = db.entryNoteDao()
    private val json = Json { ignoreUnknownKeys = true }

    private var templateId: Long = -1
    private var entryId: Long = -1
    private var noteId: Long? = null
    private var editingEntry: LogEntry? = null

    private val _locked = MutableStateFlow(true)
    /** When true, the entry's field content is shown read-only. */
    val locked: StateFlow<Boolean> = _locked

    private val _fields = MutableStateFlow<List<FieldDef>>(emptyList())
    val fields: StateFlow<List<FieldDef>> = _fields

    private val _entryValues = MutableStateFlow<JsonObject?>(null)
    /** The entry's stored values, for the read-only readout or to pre-fill edits. */
    val entryValues: StateFlow<JsonObject?> = _entryValues

    private val _entryTimestamp = MutableStateFlow<String?>(null)
    val entryTimestamp: StateFlow<String?> = _entryTimestamp

    // Null until loaded; then the existing note's text, or "" for a new note.
    private val _initialNoteText = MutableStateFlow<String?>(null)
    val initialNoteText: StateFlow<String?> = _initialNoteText

    /** [noteId] is null to add a new note, or the id of a note being edited. */
    fun load(logId: Long, entryId: Long, noteId: Long?) {
        this.templateId = logId
        this.entryId = entryId
        this.noteId = noteId
        viewModelScope.launch {
            val template = templateDao.getById(logId)
            _locked.value = template?.locked ?: true
            _fields.value = template
                ?.let { runCatching { json.decodeFromString<List<FieldDef>>(it.schemaJson) }.getOrNull() }
                ?: emptyList()

            val entry = entryDao.getById(entryId)
            editingEntry = entry
            _entryValues.value = entry?.let { EntryValues.decode(it.valuesJson) }
            _entryTimestamp.value = entry?.createdAt

            _initialNoteText.value =
                if (noteId != null) noteDao.getById(noteId)?.text.orEmpty() else ""
        }
    }

    /**
     * Save the note (adding a new one or updating the one being edited). When
     * [entryValues] is non-null (the log is unlocked and the fields were shown
     * as editable), the entry's own field values are written back too.
     */
    fun save(
        noteText: String,
        entryValues: Map<String, JsonElement>?,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            if (entryValues != null) {
                editingEntry?.let { existing ->
                    entryDao.update(
                        existing.copy(
                            valuesJson = EntryValues.encode(entryValues),
                            updatedAt = BackupRepository.now(),
                        )
                    )
                }
            }

            val trimmed = noteText.trim()
            val editingNoteId = noteId
            if (editingNoteId != null) {
                noteDao.getById(editingNoteId)?.let { existing ->
                    noteDao.update(existing.copy(text = trimmed))
                }
            } else if (trimmed.isNotEmpty()) {
                noteDao.insert(
                    EntryNote(entryId = entryId, createdAt = BackupRepository.now(), text = trimmed)
                )
            }
            onSaved()
        }
    }
}
