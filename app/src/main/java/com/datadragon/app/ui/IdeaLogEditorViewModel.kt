package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Backs both "New Idea Log" and "Edit Idea Log". Creating writes a new row;
 * editing loads the existing one and writes its settings and fields back.
 *
 * Turning **Allow Archiving** off is refused while the log still holds archived
 * ideas: they would otherwise become unreachable, and silently unarchiving them
 * would move the user's data without being asked. [ArchivedIdeasExist] reports
 * that back to the screen so it can say so and leave the switch on.
 */
class IdeaLogEditorViewModel(app: Application) : AndroidViewModel(app) {

    private val logDao = AppDatabase.getInstance(app).ideaLogDao()
    private val entryDao = AppDatabase.getInstance(app).ideaEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private var editingId: Long = -1

    private val _loaded = MutableStateFlow<IdeaLog?>(null)
    /** The log being edited, once read; null while creating or still loading. */
    val loaded: StateFlow<IdeaLog?> = _loaded

    private val _loadedFields = MutableStateFlow<List<IdeaFieldDef>>(emptyList())
    val loadedFields: StateFlow<List<IdeaFieldDef>> = _loadedFields

    fun load(id: Long) {
        if (editingId == id) return
        editingId = id
        viewModelScope.launch {
            val log = logDao.getById(id)
            _loadedFields.value = log
                ?.let { runCatching { json.decodeFromString<List<IdeaFieldDef>>(it.fieldsJson) }.getOrNull() }
                ?: emptyList()
            // Published last: the screen treats a non-null log as "everything is ready".
            _loaded.value = log
        }
    }

    /** Persist a brand-new Idea Log, then invoke [onSaved]. */
    fun create(
        name: String,
        fields: List<IdeaFieldDef>,
        automaticTimestamping: Boolean,
        allowArchiving: Boolean,
        showEntireIdeaCard: Boolean,
        previewLines: Int,
        sortTimestampFieldId: String?,
        sortNewestFirst: Boolean,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            logDao.insert(
                IdeaLog(
                    name = name.trim(),
                    createdAt = System.currentTimeMillis(),
                    fieldsJson = json.encodeToString(fields),
                    automaticTimestamping = automaticTimestamping,
                    allowArchiving = allowArchiving,
                    showEntireIdeaCard = showEntireIdeaCard,
                    previewLines = previewLines,
                    sortTimestampFieldId = sortTimestampFieldId,
                    sortNewestFirst = sortNewestFirst,
                )
            )
            onSaved()
        }
    }

    /**
     * Write an edited Idea Log back. When [allowArchiving] is being turned off
     * while archived ideas still exist, nothing is written and [onBlocked] is
     * invoked instead — the caller keeps the switch on and explains why.
     */
    fun update(
        name: String,
        fields: List<IdeaFieldDef>,
        automaticTimestamping: Boolean,
        allowArchiving: Boolean,
        showEntireIdeaCard: Boolean,
        previewLines: Int,
        sortTimestampFieldId: String?,
        sortNewestFirst: Boolean,
        onBlocked: () -> Unit,
        onSaved: () -> Unit,
    ) {
        val id = editingId
        if (id < 0) return
        viewModelScope.launch {
            val wasAllowed = _loaded.value?.allowArchiving ?: false
            if (wasAllowed && !allowArchiving && entryDao.archivedCount(id) > 0) {
                onBlocked()
                return@launch
            }
            val savedName = name.trim()
            logDao.updateLog(
                id = id,
                name = savedName,
                fieldsJson = json.encodeToString(fields),
                automaticTimestamping = automaticTimestamping,
                allowArchiving = allowArchiving,
                showEntireIdeaCard = showEntireIdeaCard,
                previewLines = previewLines,
                sortTimestampFieldId = sortTimestampFieldId,
                sortNewestFirst = sortNewestFirst,
            )
            _loaded.value = _loaded.value?.copy(
                name = savedName,
                allowArchiving = allowArchiving,
            )
            _loadedFields.value = fields
            onSaved()
        }
    }
}
