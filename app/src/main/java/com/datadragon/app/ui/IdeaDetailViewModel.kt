package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.IdeaEntry
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
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
 * Backs the full Idea Detail view — the complete, read-only rendering of one
 * idea. It watches the idea itself so an edit, a mark or an archive made from
 * here is reflected immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdeaDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val logDao = AppDatabase.getInstance(app).ideaLogDao()
    private val entryDao = AppDatabase.getInstance(app).ideaEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _log = MutableStateFlow<IdeaLog?>(null)
    val log: StateFlow<IdeaLog?> = _log

    private val _fields = MutableStateFlow<List<IdeaFieldDef>>(emptyList())
    val fields: StateFlow<List<IdeaFieldDef>> = _fields

    private val entryId = MutableStateFlow<Long?>(null)

    val entry: StateFlow<IdeaEntry?> = entryId
        .flatMapLatest { id -> if (id == null) flowOf(null) else entryDao.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun load(logId: Long, ideaId: Long) {
        entryId.value = ideaId
        viewModelScope.launch {
            val log = logDao.getById(logId)
            _log.value = log
            _fields.value = log
                ?.let { runCatching { json.decodeFromString<List<IdeaFieldDef>>(it.fieldsJson) }.getOrNull() }
                ?: emptyList()
        }
    }

    fun toggleMark() {
        val current = entry.value ?: return
        viewModelScope.launch { entryDao.setMarked(current.id, !current.marked) }
    }

    fun setArchived(archived: Boolean) {
        val current = entry.value ?: return
        viewModelScope.launch { entryDao.setArchived(current.id, archived) }
    }

    /** Permanently delete this idea, then invoke [onDeleted] to leave the screen. */
    fun delete(onDeleted: () -> Unit) {
        val current = entry.value ?: return
        viewModelScope.launch {
            entryDao.delete(current)
            onDeleted()
        }
    }
}
