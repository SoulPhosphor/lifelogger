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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
class ChecklistViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RoomChecklistStore(AppDatabase.getInstance(app))
    private val settings = SettingsRepository(app)
    private val manager = ChecklistDraftManager(store, viewModelScope, cleanupScope)

    val title: StateFlow<String> = manager.title
    val items: StateFlow<List<ChecklistItem>> = manager.items
    val persisted: StateFlow<Boolean> = manager.persisted

    // Global list behavior, read when the list opens.
    private val _completeIcon = MutableStateFlow(settings.completeIcon)
    val completeIcon: StateFlow<CompleteIcon> = _completeIcon

    private val _crossOut = MutableStateFlow(settings.crossOutWhenCompleted)
    val crossOut: StateFlow<Boolean> = _crossOut

    /** [id] null means a brand-new (unsaved) list; otherwise load an existing one. */
    fun load(id: Long?) {
        _completeIcon.value = settings.completeIcon
        _crossOut.value = settings.crossOutWhenCompleted
        manager.load(id, settings.moveCompletedToBottom)
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
    suspend fun flushPending() = manager.flushPending()

    /** Best-effort flush when the app is backgrounded. */
    fun flushOnBackground() = manager.flushOnBackground()

    /** Flush pending text then drop blank rows when leaving an established list. */
    fun onLeave() = manager.onLeave()

    /** Persist a brand-new list and its items, then report done. */
    fun save(onSaved: () -> Unit) = manager.save(onSaved)

    companion object {
        // Process-lifetime scope so a flush/cleanup started on the way out still
        // finishes after the ViewModel (and its viewModelScope) is cleared.
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
