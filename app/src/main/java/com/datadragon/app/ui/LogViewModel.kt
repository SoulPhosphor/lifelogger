package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.DebouncedFieldWriter
import com.datadragon.app.data.EntryNote
import com.datadragon.app.data.EntryValues
import com.datadragon.app.data.FieldDef
import com.datadragon.app.data.FieldType
import com.datadragon.app.data.FormMarkdownGenerator
import com.datadragon.app.data.LogEntry
import com.datadragon.app.data.LogTemplate
import com.datadragon.app.data.sortEligible
import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Backs the single-log (entry list) screen. It loads the template (for the log
 * name and field definitions) and observes that log's entries in the form's order.
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
    private var titleInputSequence = 0L
    private val titleWriter = DebouncedFieldWriter<Long>(viewModelScope, 300L) { id, name ->
        templateDao.rename(id, name.trim(), FormMarkdownGenerator.generate(name, _fields.value))
    }

    private val storedEntries: StateFlow<List<LogEntry>> = templateId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else entryDao.observeForTemplate(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The filter bar's picks. Both are null until the user changes them, which
    // means "use the form's default", and both die with this screen — reopening
    // the log always comes back to the form's own default ordering.
    private val _pickedCategoryLabel = MutableStateFlow<String?>(null)
    private val _pickedNewestFirst = MutableStateFlow<Boolean?>(null)

    /**
     * Every ordering the filter bar can offer for this form. Never empty — the
     * automatic entry timestamp is always available, before the form has even
     * loaded, so the sorting controls are there from the first frame.
     */
    val sortCategories: StateFlow<List<SortCategory>> =
        combine(_fields, _template) { fields, template -> sortCategoriesOf(fields, template) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(TIMESTAMP_CATEGORY))

    /** The ordering in force, which is the form's default until the user picks. */
    val selectedCategory: StateFlow<SortCategory?> =
        combine(_fields, _template, _pickedCategoryLabel) { fields, template, picked ->
            resolveCategory(fields, template, picked)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TIMESTAMP_CATEGORY)

    /** The direction in force, which is the form's default until the user picks. */
    val newestFirst: StateFlow<Boolean> =
        combine(_template, _pickedNewestFirst) { template, picked ->
            picked ?: template?.sortNewestFirst ?: true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Ordered by the selected sort category and direction. */
    val entries: StateFlow<List<LogEntry>> =
        combine(
            storedEntries,
            _fields,
            _template,
            _pickedCategoryLabel,
            _pickedNewestFirst,
        ) { entries, fields, template, pickedLabel, pickedDirection ->
            sortLogEntries(
                entries = entries,
                sortField = resolveCategory(fields, template, pickedLabel)?.field,
                newestFirst = pickedDirection ?: template?.sortNewestFirst ?: true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Order by one of [sortCategories] instead of the form's default. */
    fun selectSortCategory(category: SortCategory) {
        _pickedCategoryLabel.value = category.label
    }

    /** Run the current category newest first, or oldest first. */
    fun selectNewestFirst(value: Boolean) {
        _pickedNewestFirst.value = value
    }

    /** Drop both picks and go back to the form's own default ordering. */
    fun clearSort() {
        _pickedCategoryLabel.value = null
        _pickedNewestFirst.value = null
    }

    /** Update the visible title immediately and debounce its database write. */
    fun setTitle(name: String) {
        val current = _template.value ?: return
        _template.value = current.copy(name = name)
        titleWriter.schedule(current.id, name, ++titleInputSequence)
    }

    /** Persist the latest title immediately when its editor loses focus. */
    fun onTitleFocusLost() {
        val id = _template.value?.id ?: return
        viewModelScope.launch { titleWriter.flush(id) }
    }

    /** Flush the title before leaving this screen, then navigate. */
    fun leaveAfterTitleFlush(onFlushed: () -> Unit) {
        val id = _template.value?.id
        if (id == null) {
            onFlushed()
            return
        }
        viewModelScope.launch {
            titleWriter.flush(id)
            onFlushed()
        }
    }

    /** Flip an entry's manual star on or off. */
    fun toggleMark(entry: LogEntry) {
        viewModelScope.launch {
            entryDao.setMarked(entry.id, !entry.marked)
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

    /** Turn follow-up notes on/off for this log (toggleable any time). */
    fun setAllowAppendedNotes(allow: Boolean) {
        val current = _template.value ?: return
        viewModelScope.launch {
            templateDao.setAllowAppendedNotes(current.id, allow)
            _template.value = current.copy(allowAppendedNotes = allow)
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

/** The label the automatic entry timestamp goes by in the filter bar. */
const val AUTOMATIC_TIMESTAMP_LABEL = "Timestamp"

/**
 * One ordering the filter bar can offer. A null [field] is the automatic entry
 * timestamp, which every form always has.
 */
data class SortCategory(val label: String, val field: FieldDef?)

/** The automatic entry timestamp, which every form can always sort by. */
val TIMESTAMP_CATEGORY = SortCategory(AUTOMATIC_TIMESTAMP_LABEL, null)

/**
 * The orderings this form offers: the automatic entry timestamp first, then every
 * date-bearing field the user opted in with "Allow Order Filtering", plus the
 * form's own default sort field whether or not it was also opted in.
 */
internal fun sortCategoriesOf(fields: List<FieldDef>, template: LogTemplate?): List<SortCategory> {
    val default = defaultSortField(fields, template)
    val opted = fields.filter {
        it.type.sortEligible && (it.allowOrderFiltering || it.label == default?.label)
    }
    return listOf(TIMESTAMP_CATEGORY) + opted.map { SortCategory(it.label, it) }
}

/**
 * The category in force: the user's pick when it still exists, otherwise the
 * form's default sort field, otherwise the automatic entry timestamp.
 */
internal fun resolveCategory(
    fields: List<FieldDef>,
    template: LogTemplate?,
    pickedLabel: String?,
): SortCategory? {
    val categories = sortCategoriesOf(fields, template)
    pickedLabel?.let { label ->
        categories.firstOrNull { it.label == label }?.let { return it }
    }
    val default = defaultSortField(fields, template)
    return categories.firstOrNull { it.field?.label == default?.label } ?: categories.firstOrNull()
}

/**
 * The field the form sorts by, or null when it falls back to the hidden automatic
 * entry timestamp. A stored label only counts while a sort-eligible field still
 * carries it, so deleting or retyping that field reverts the form to createdAt.
 */
internal fun defaultSortField(fields: List<FieldDef>, template: LogTemplate?): FieldDef? =
    template?.sortTimestampLabel?.let { label ->
        fields.firstOrNull { it.type.sortEligible && it.label == label }
    }

/**
 * Order entries by [sortField], or by the automatic entry timestamp when it is
 * null. Entries with no usable value for the chosen field are never interleaved:
 * they collect at the bottom of the list in both directions.
 */
internal fun sortLogEntries(
    entries: List<LogEntry>,
    sortField: FieldDef?,
    newestFirst: Boolean,
): List<LogEntry> {
    val (timed, undated) = entries
        .map { it to entrySortTime(it, sortField) }
        .partition { (_, time) -> time != null }
    val ordered = if (newestFirst) {
        timed.sortedWith(compareByDescending<Pair<LogEntry, LocalDateTime?>> { it.second }.thenByDescending { it.first.id })
    } else {
        timed.sortedWith(compareBy<Pair<LogEntry, LocalDateTime?>> { it.second }.thenBy { it.first.id })
    }
    val trailing = if (newestFirst) undated.sortedByDescending { it.first.id } else undated.sortedBy { it.first.id }
    return (ordered + trailing).map { it.first }
}

/**
 * The instant one entry sorts at, or null when it has nothing to sort by. A date
 * field with no time sorts at the start of its day, so a date and a date & time
 * field order against each other consistently.
 */
private fun entrySortTime(entry: LogEntry, sortField: FieldDef?): LocalDateTime? {
    if (sortField == null) {
        return runCatching { OffsetDateTime.parse(entry.createdAt).toLocalDateTime() }.getOrNull()
    }
    val raw = EntryValues.rawValue(EntryValues.decode(entry.valuesJson), sortField.label)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return when (sortField.type) {
        FieldType.DATE ->
            runCatching { LocalDate.parse(raw, EntryValues.DATE_STORAGE).atStartOfDay() }.getOrNull()
        FieldType.DATETIME ->
            runCatching { LocalDateTime.parse(raw, EntryValues.DATETIME_STORAGE) }.getOrNull()
        else -> null
    }
}
