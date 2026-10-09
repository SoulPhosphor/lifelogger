package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ChecklistDraftManager
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.RoomChecklistStore
import com.datadragon.app.data.SettingsRepository
import com.datadragon.app.export.ChecklistExport
import com.datadragon.app.export.ChecklistExportFormat
import com.datadragon.app.export.ExportContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.datadragon.app.data.RoomLuckyListStore
import com.datadragon.app.export.LuckyListExport

/**
 * Backs a single open list. The editing logic lives in [ChecklistDraftManager]
 * (Android-free and unit-tested); this exposes its state to the screen and reads
 * the global list-behavior settings.
 *
 * Two modes, chosen by [load]:
 * - **New list** (`load(null)`): an in-memory draft. It persists itself the moment
 *   its first item gets non-blank text; before that, backing out discards it.
 * - **Established list** (`load(id)`): loaded from the database and auto-saved —
 *   item/title text is debounced, structural changes save immediately.
 */
open class ChecklistViewModel(app: Application, private val lucky: Boolean = false) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val _exclusion = MutableStateFlow(false)
    val exclusion: StateFlow<Boolean> = _exclusion
    private val _ready = MutableStateFlow(!lucky)
    val ready: StateFlow<Boolean> = _ready
    private val store = if (lucky) RoomLuckyListStore(db) { _exclusion.value } else RoomChecklistStore(db)
    private val settings = SettingsRepository(app)
    private val manager = ChecklistDraftManager(store, viewModelScope, cleanupScope)

    val title: StateFlow<String> = manager.title
    val items: StateFlow<List<ChecklistItem>> = manager.items
    /** True while the list is logically unsaved (a new or recovered draft). */
    val isDraft: StateFlow<Boolean> = manager.isDraft

    // Global list behavior, read when the list opens.
    private val _completeIcon = MutableStateFlow(settings.completeIcon)
    val completeIcon: StateFlow<CompleteIcon> = _completeIcon

    private val _crossOut = MutableStateFlow(settings.crossOutWhenCompleted)
    val crossOut: StateFlow<Boolean> = _crossOut

    private var initialized = false
    private var loadedId: Long? = null

    /** [id] null means a brand-new (unsaved) list; otherwise load an existing one. */
    fun load(id: Long?) {
        if (initialized && loadedId == id) return
        initialized = true
        loadedId = id
        _completeIcon.value = settings.completeIcon
        _crossOut.value = settings.crossOutWhenCompleted
        manager.load(id, if (lucky) false else settings.moveCompletedToBottom)
        if (lucky) viewModelScope.launch {
            _exclusion.value = id?.let { db.luckyListDao().getList(it)?.excludePreviouslySelected } ?: false
            _ready.value = true
        }
    }

    private var exclusionWrite: Job? = null

    fun setExclusion(value: Boolean) {
        _exclusion.value = value
        exclusionWrite = cleanupScope.launch {
            manager.flushPending()
            manager.persistedId()?.let { db.luckyListDao().setExclusion(it, _exclusion.value) }
        }
    }

    fun setTitle(text: String) = manager.setTitle(text)
    fun addItem(onAdded: (Long) -> Unit = {}) = manager.addItem(onAdded)
    fun addSubItem(afterId: Long, onAdded: (Long) -> Unit = {}) = manager.addSubItem(afterId, onAdded)
    fun updateText(itemId: Long, text: String) = manager.updateText(itemId, text)
    fun setCompleted(itemId: Long, completed: Boolean) = manager.setCompleted(itemId, completed)
    fun deleteItem(itemId: Long) = manager.deleteItem(itemId)
    fun reorder(orderedIds: List<Long>) = manager.reorder(orderedIds)
    fun onItemFocusLost(itemId: Long) = manager.onItemFocusLost(itemId)
    fun onTitleFocusLost() = manager.onTitleFocusLost()

    /** Flush pending text and wait for it, before an explicit Back/navigation. */
    suspend fun flushPending() {
        manager.flushPending()
        exclusionWrite?.join()
    }

    /** Best-effort flush when the app is backgrounded. */
    fun flushOnBackground() = manager.flushOnBackground()

    /** Flush pending text then drop blank rows when leaving an established list. */
    fun onLeave() = manager.onLeave()

    /** Save: finalize the draft into a normal saved list, flush, then report done. */
    fun save(onSaved: () -> Unit) { viewModelScope.launch { flushPending(); manager.save(onSaved) } }

    /** Discard: delete the draft and its items, then report done. */
    fun discardDraft(onDiscarded: () -> Unit) = manager.discardDraft(onDiscarded)

    /** Delete List (⋮ menu): delete this list and its items, then report done. */
    fun deleteList(onDeleted: () -> Unit) = manager.deleteList(onDeleted)

    /**
     * Build a downloadable file of this list in [format], or null if the list has
     * never been persisted (nothing to export yet). Pending debounced text is
     * flushed first, then the list is read back from the database, so the file
     * matches what's on screen.
     */
    suspend fun buildExport(format: ChecklistExportFormat): ExportContent? {
        manager.flushPending()
        val id = manager.persistedId() ?: return null
        if (lucky) {
            val list = db.luckyListDao().getList(id) ?: return null
            return LuckyListExport.build(list, db.luckyListDao().getItemsOnce(id), format)
        }
        val checklist = store.getChecklist(id) ?: return null
        val items = store.getItemsOnce(id)
        return when (format) {
            ChecklistExportFormat.MARKDOWN -> ChecklistExport.markdown(checklist, items)
            ChecklistExportFormat.JSON -> ChecklistExport.json(checklist, items)
            ChecklistExportFormat.TEXT -> ChecklistExport.text(checklist, items)
            ChecklistExportFormat.PDF -> ChecklistExport.pdf(checklist, items)
        }
    }

    companion object {
        // Process-lifetime scope so a flush/cleanup started on the way out still
        // finishes after the ViewModel (and its viewModelScope) is cleared.
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

class LuckyListViewModel(app: Application) : ChecklistViewModel(app, lucky = true)
