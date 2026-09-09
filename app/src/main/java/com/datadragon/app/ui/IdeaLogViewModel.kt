package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.DebouncedFieldWriter
import com.datadragon.app.data.IdeaEntry
import com.datadragon.app.data.IdeaFieldDef
import com.datadragon.app.data.IdeaLog
import com.datadragon.app.data.IdeaSearch
import com.datadragon.app.data.IdeaValues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Which half of an Idea Log is on screen. */
enum class IdeaLocation { ACTIVE, ARCHIVE }

/** A search as the user submitted it. Null means no search is running. */
data class IdeaQuery(
    val text: String,
    val wholeWord: Boolean,
    val matchCase: Boolean,
    val allLocations: Boolean,
)

/**
 * Backs the Idea Log screen: the log itself, its field schema, and the ideas in
 * it — split into the active and archived views, ordered by the log's sorting
 * rules, and narrowed by a submitted search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdeaLogViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val logDao = db.ideaLogDao()
    private val entryDao = db.ideaEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val _log = MutableStateFlow<IdeaLog?>(null)
    val log: StateFlow<IdeaLog?> = _log

    private val _fields = MutableStateFlow<List<IdeaFieldDef>>(emptyList())
    val fields: StateFlow<List<IdeaFieldDef>> = _fields

    private val logId = MutableStateFlow<Long?>(null)
    private var titleInputSequence = 0L
    private val titleWriter = DebouncedFieldWriter<Long>(viewModelScope, 300L) { id, name ->
        logDao.rename(id, name.trim())
    }

    private val storedEntries: StateFlow<List<IdeaEntry>> = logId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else entryDao.observeForLog(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active list or archive. Ephemeral — reopening the log starts on the active list. */
    private val _location = MutableStateFlow(IdeaLocation.ACTIVE)
    val location: StateFlow<IdeaLocation> = _location

    /** The submitted search, or null when none is running. */
    private val _query = MutableStateFlow<IdeaQuery?>(null)
    val query: StateFlow<IdeaQuery?> = _query

    // The filter bar's picks. Null means "use the log's default", and both die
    // with this screen.
    private val _pickedCategoryLabel = MutableStateFlow<String?>(null)
    private val _pickedNewestFirst = MutableStateFlow<Boolean?>(null)

    /** Every ordering this log can offer. Never empty. */
    val sortCategories: StateFlow<List<IdeaSortCategory>> =
        combine(_fields, _log) { fields, log -> ideaSortCategories(fields, log) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                listOf(IDEA_TIMESTAMP_CATEGORY),
            )

    /** The ordering in force, which is the log's default until the user picks. */
    val selectedCategory: StateFlow<IdeaSortCategory> =
        combine(_fields, _log, _pickedCategoryLabel) { fields, log, picked ->
            resolveIdeaCategory(fields, log, picked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IDEA_TIMESTAMP_CATEGORY)

    /** The direction in force, which is the log's default until the user picks. */
    val newestFirst: StateFlow<Boolean> =
        combine(_log, _pickedNewestFirst) { log, picked ->
            picked ?: log?.sortNewestFirst ?: true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * The ideas on screen: the current location's ideas (or every location when
     * the search says so), narrowed by the submitted search and put in order.
     */
    val entries: StateFlow<List<IdeaEntry>> =
        combine(
            storedEntries,
            combine(_fields, _log) { fields, log -> fields to log },
            combine(_pickedCategoryLabel, _pickedNewestFirst) { label, dir -> label to dir },
            _location,
            _query,
        ) { entries, schema, picks, location, query ->
            val (fields, log) = schema
            val (pickedLabel, pickedDirection) = picks
            val matcher = query?.let { IdeaSearch.matcher(it.text, it.wholeWord, it.matchCase) }
            val searchEverywhere = query?.allLocations == true && log?.allowArchiving == true

            val inScope = entries.filter { entry ->
                when {
                    log?.allowArchiving != true -> !entry.archived
                    matcher != null && searchEverywhere -> true
                    else -> entry.archived == (location == IdeaLocation.ARCHIVE)
                }
            }
            val matched = if (matcher == null) {
                inScope
            } else {
                inScope.filter { entry ->
                    matcher.matchesAny(
                        IdeaValues.searchableText(IdeaValues.decode(entry.valuesJson), fields),
                    )
                }
            }
            sortIdeaEntries(
                entries = matched,
                sortField = resolveIdeaCategory(fields, log, pickedLabel).field,
                newestFirst = pickedDirection ?: log?.sortNewestFirst ?: true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Read the log and its schema. The screen calls this whenever it enters
     * composition, so returning from the Idea Log editor picks up the edited
     * settings and fields.
     */
    fun load(id: Long) {
        logId.value = id
        viewModelScope.launch { refresh(id) }
    }

    private suspend fun refresh(id: Long) {
        val log = logDao.getById(id)
        _log.value = log
        _fields.value = log
            ?.let { runCatching { json.decodeFromString<List<IdeaFieldDef>>(it.fieldsJson) }.getOrNull() }
            ?: emptyList()
        // An Idea Log whose archiving was turned off has no archive to be in.
        if (log?.allowArchiving != true) _location.value = IdeaLocation.ACTIVE
    }

    fun selectSortCategoryLabel(label: String) {
        _pickedCategoryLabel.value = label
    }

    fun selectNewestFirst(value: Boolean) {
        _pickedNewestFirst.value = value
    }

    fun clearSort() {
        _pickedCategoryLabel.value = null
        _pickedNewestFirst.value = null
    }

    /** Switch between the active list and the archive. */
    fun setLocation(location: IdeaLocation) {
        _location.value = location
    }

    /** Run a search. A blank query clears it instead. */
    fun submitSearch(text: String, wholeWord: Boolean, matchCase: Boolean, allLocations: Boolean) {
        _query.value = if (text.isBlank()) {
            null
        } else {
            IdeaQuery(text, wholeWord, matchCase, allLocations)
        }
    }

    fun clearSearch() {
        _query.value = null
    }

    /** Update the visible title immediately and debounce its database write. */
    fun setTitle(name: String) {
        val current = _log.value ?: return
        _log.value = current.copy(name = name)
        titleWriter.schedule(current.id, name, ++titleInputSequence)
    }

    fun onTitleFocusLost() {
        val id = _log.value?.id ?: return
        viewModelScope.launch { titleWriter.flush(id) }
    }

    /** Flush the title before leaving this screen, then navigate. */
    fun leaveAfterTitleFlush(onFlushed: () -> Unit) {
        val id = _log.value?.id
        if (id == null) {
            onFlushed()
            return
        }
        viewModelScope.launch {
            titleWriter.flush(id)
            onFlushed()
        }
    }

    fun toggleMark(entry: IdeaEntry) {
        viewModelScope.launch { entryDao.setMarked(entry.id, !entry.marked) }
    }

    /** Archive or unarchive one idea. Archiving never deletes anything. */
    fun setArchived(entry: IdeaEntry, archived: Boolean) {
        viewModelScope.launch { entryDao.setArchived(entry.id, archived) }
    }

    /** Permanently delete one idea — available archived or not. */
    fun deleteEntry(entry: IdeaEntry) {
        viewModelScope.launch { entryDao.delete(entry) }
    }

    /** Delete this whole Idea Log and every idea in it, then invoke [onDeleted]. */
    fun deleteLog(onDeleted: () -> Unit) {
        val log = _log.value ?: return
        viewModelScope.launch {
            db.withTransaction {
                entryDao.deleteForLog(log.id)
                logDao.delete(log)
            }
            onDeleted()
        }
    }
}
