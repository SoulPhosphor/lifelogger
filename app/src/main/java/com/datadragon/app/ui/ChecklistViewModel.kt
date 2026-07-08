package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.ChecklistItem
import com.datadragon.app.data.CompleteIcon
import com.datadragon.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs a single open list (its title and items).
 *
 * The in-memory [items] list is the source of truth while the screen is open, so
 * typing and dragging stay instant; every change is mirrored to the database in
 * the background. Item order in the list *is* the display order — each item's
 * stored `position` is renumbered to match after any structural change.
 */
class ChecklistViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).checklistDao()
    private val settings = SettingsRepository(app)

    private var checklistId: Long = -1
    private var loaded = false

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _items = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val items: StateFlow<List<ChecklistItem>> = _items

    // Global list behavior, read when the list opens.
    private val _completeIcon = MutableStateFlow(settings.completeIcon)
    val completeIcon: StateFlow<CompleteIcon> = _completeIcon

    private val _crossOut = MutableStateFlow(settings.crossOutWhenCompleted)
    val crossOut: StateFlow<Boolean> = _crossOut

    private val _moveCompletedToBottom = MutableStateFlow(settings.moveCompletedToBottom)
    val moveCompletedToBottom: StateFlow<Boolean> = _moveCompletedToBottom

    fun load(id: Long) {
        if (loaded && checklistId == id) return
        checklistId = id
        loaded = true
        _completeIcon.value = settings.completeIcon
        _crossOut.value = settings.crossOutWhenCompleted
        _moveCompletedToBottom.value = settings.moveCompletedToBottom
        viewModelScope.launch {
            _title.value = dao.getChecklist(id)?.name ?: ""
            _items.value = dao.getItemsOnce(id)
        }
    }

    fun setTitle(text: String) {
        _title.value = text
        viewModelScope.launch { dao.renameChecklist(checklistId, text.trim()) }
    }

    /** Append a new blank top-level item; reports its id so the caller can focus it. */
    fun addItem(onAdded: (Long) -> Unit = {}) = addItemInternal(indent = 0, afterId = null, onAdded)

    /** Insert a new blank sub-item directly under [afterId] (one level deep). */
    fun addSubItem(afterId: Long, onAdded: (Long) -> Unit = {}) =
        addItemInternal(indent = 1, afterId = afterId, onAdded)

    private fun addItemInternal(indent: Int, afterId: Long?, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val newRow = ChecklistItem(
                checklistId = checklistId,
                indent = indent,
                position = _items.value.size,
            )
            val newId = dao.insertItem(newRow)
            val created = newRow.copy(id = newId)
            val list = _items.value.toMutableList()
            val insertAt = if (afterId == null) {
                list.size
            } else {
                (list.indexOfFirst { it.id == afterId } + 1).coerceIn(0, list.size)
            }
            list.add(insertAt, created)
            _items.value = list
            persistOrder()
            onAdded(newId)
        }
    }

    fun updateText(itemId: Long, text: String) {
        _items.value = _items.value.map { if (it.id == itemId) it.copy(text = text) else it }
        viewModelScope.launch { dao.updateItemText(itemId, text) }
    }

    fun setCompleted(itemId: Long, completed: Boolean) {
        var list = _items.value.map { if (it.id == itemId) it.copy(completed = completed) else it }
        if (completed && _moveCompletedToBottom.value) {
            val moving = list.firstOrNull { it.id == itemId }
            if (moving != null) {
                list = list.filterNot { it.id == itemId } + moving
            }
        }
        _items.value = list
        viewModelScope.launch {
            dao.setItemCompleted(itemId, completed)
            persistOrder()
        }
    }

    fun deleteItem(itemId: Long) {
        _items.value = _items.value.filterNot { it.id == itemId }
        viewModelScope.launch {
            dao.deleteItem(itemId)
            persistOrder()
        }
    }

    /** Reorder after a drag: [orderedIds] is the new full top-to-bottom order. */
    fun reorder(orderedIds: List<Long>) {
        val byId = _items.value.associateBy { it.id }
        _items.value = orderedIds.mapNotNull { byId[it] }
        viewModelScope.launch { dao.applyOrder(orderedIds) }
    }

    /**
     * Called when leaving the list. Drops items left blank; if the whole list
     * ended up with no title and no real items, drops the list itself so an
     * abandoned, never-filled-in list doesn't linger.
     */
    fun onLeave() {
        val id = checklistId
        if (id < 0) return
        // Runs on a process-lifetime scope so it still completes after this
        // ViewModel (and its viewModelScope) is cleared by the back navigation.
        cleanupScope.launch {
            dao.deleteBlankItems(id)
            val remaining = dao.getItemsOnce(id)
            val checklist = dao.getChecklist(id)
            if ((checklist?.name.isNullOrBlank()) && remaining.isEmpty()) {
                dao.deleteChecklist(id)
            }
        }
    }

    private suspend fun persistOrder() {
        dao.applyOrder(_items.value.map { it.id })
    }

    companion object {
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
