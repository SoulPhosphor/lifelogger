package com.datadragon.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.datadragon.app.data.AppDatabase
import com.datadragon.app.data.Checklist
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
 * Two modes, chosen by [load]:
 * - **New list** (`load(null)`): a draft held entirely in memory. Nothing touches
 *   the database until [save] is called — so backing out discards it. Temporary
 *   negative ids stand in for rows until they're really inserted on save.
 * - **Established list** (`load(id)`): loaded from the database and **auto-saved** —
 *   every edit (text, check, reorder, add/delete) writes straight through, with no
 *   Save button. Item order in the list is the display order; each item's stored
 *   `position` is renumbered to match after any structural change.
 */
class ChecklistViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).checklistDao()
    private val settings = SettingsRepository(app)

    private var checklistId: Long = -1
    private var isNew: Boolean = true
    private var loaded = false

    // Hands out temporary ids for draft rows in a brand-new list.
    private var nextTempId = -1L

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

    /** [id] null means a brand-new (unsaved) list; otherwise load an existing one. */
    fun load(id: Long?) {
        if (loaded && checklistId == (id ?: -1)) return
        loaded = true
        _completeIcon.value = settings.completeIcon
        _crossOut.value = settings.crossOutWhenCompleted
        _moveCompletedToBottom.value = settings.moveCompletedToBottom
        if (id == null) {
            isNew = true
            checklistId = -1
            _title.value = ""
            _items.value = emptyList()
        } else {
            isNew = false
            checklistId = id
            viewModelScope.launch {
                _title.value = dao.getChecklist(id)?.name ?: ""
                _items.value = dao.getItemsOnce(id)
            }
        }
    }

    fun setTitle(text: String) {
        _title.value = text
        if (!isNew) viewModelScope.launch { dao.renameChecklist(checklistId, text.trim()) }
    }

    /** Append a new blank top-level item; reports its id so the caller can focus it. */
    fun addItem(onAdded: (Long) -> Unit = {}) = addItemInternal(indent = 0, afterId = null, onAdded)

    /** Insert a new blank sub-item directly under [afterId] (one level deep). */
    fun addSubItem(afterId: Long, onAdded: (Long) -> Unit = {}) =
        addItemInternal(indent = 1, afterId = afterId, onAdded)

    private fun addItemInternal(indent: Int, afterId: Long?, onAdded: (Long) -> Unit) {
        viewModelScope.launch {
            val position = _items.value.size
            val newId = if (isNew) nextTempId-- else {
                dao.insertItem(
                    ChecklistItem(checklistId = checklistId, indent = indent, position = position),
                )
            }
            val created = ChecklistItem(
                id = newId,
                checklistId = checklistId,
                indent = indent,
                position = position,
            )
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
        if (!isNew) viewModelScope.launch { dao.updateItemText(itemId, text) }
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
        if (!isNew) viewModelScope.launch {
            dao.setItemCompleted(itemId, completed)
            persistOrder()
        }
    }

    fun deleteItem(itemId: Long) {
        _items.value = _items.value.filterNot { it.id == itemId }
        if (!isNew) viewModelScope.launch {
            dao.deleteItem(itemId)
            persistOrder()
        }
    }

    /** Reorder after a drag: [orderedIds] is the new full top-to-bottom order. */
    fun reorder(orderedIds: List<Long>) {
        val byId = _items.value.associateBy { it.id }
        _items.value = orderedIds.mapNotNull { byId[it] }
        if (!isNew) viewModelScope.launch { dao.applyOrder(orderedIds) }
    }

    /** Persist a brand-new list and its non-blank items, then report done. */
    fun save(onSaved: () -> Unit) {
        if (!isNew) { onSaved(); return }
        viewModelScope.launch {
            val newId = dao.insertChecklist(
                Checklist(name = _title.value.trim(), createdAt = System.currentTimeMillis()),
            )
            var position = 0
            _items.value.forEach { item ->
                if (item.text.isNotBlank()) {
                    dao.insertItem(
                        item.copy(id = 0, checklistId = newId, position = position),
                    )
                    position++
                }
            }
            onSaved()
        }
    }

    /**
     * Called when leaving an established list: drop rows left blank. A brand-new
     * list persists nothing until [save], so there's nothing to clean up.
     */
    fun onLeave() {
        if (isNew) return
        val id = checklistId
        if (id < 0) return
        // Process-lifetime scope so this still finishes after the ViewModel clears.
        cleanupScope.launch { dao.deleteBlankItems(id) }
    }

    private suspend fun persistOrder() {
        if (!isNew) dao.applyOrder(_items.value.map { it.id })
    }

    companion object {
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
