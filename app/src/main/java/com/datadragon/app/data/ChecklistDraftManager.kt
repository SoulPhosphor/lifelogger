package com.datadragon.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Debounced-write target: a list's title, or one of its items (by stable local id). */
sealed interface ChecklistFieldKey {
    data object Title : ChecklistFieldKey
    data class Item(val localId: Long) : ChecklistFieldKey
}

/**
 * Owns editing a single list and getting it into the database.
 *
 * Design goals (see the change that introduced this):
 * - On-screen state is the immediate source of truth; typing never waits for the
 *   database. Every row carries a *stable local id* (a negative temp id for a row
 *   created this session, or its real id for a row loaded from the database) that
 *   never changes, so a row's identity — and its keyboard focus — survive the
 *   list being persisted. [dbId] maps that stable id to the real database id.
 * - A brand-new list is a pure in-memory draft until its first item gets
 *   non-blank text; at that instant the whole draft (title + all current rows) is
 *   persisted atomically in one transaction. [creationMutex] plus the [persisted]
 *   double-check make sure several text events arriving at once create exactly
 *   one list.
 * - Once persisted, the list is an ordinary autosaved list: title and item text
 *   are debounced (~[debounceMillis]); structural changes (check, add, delete,
 *   reorder) save immediately.
 * - Text writes flush on focus loss, on leaving, and best-effort on backgrounding.
 *   Leaving flushes pending text *before* blank-item cleanup, so cleanup can't
 *   delete a row whose latest text hasn't reached the database yet.
 *
 * [scope] is the screen/ViewModel scope (cancelled when the editor goes away).
 * [survivingScope] must outlive screen teardown so a flush started on the way out
 * still finishes; it is best-effort and is not a guarantee against whole-process
 * death.
 */
class ChecklistDraftManager(
    private val store: ChecklistStore,
    private val scope: CoroutineScope,
    private val survivingScope: CoroutineScope,
    private val debounceMillis: Long = 300L,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _items = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val items: StateFlow<List<ChecklistItem>> = _items

    private val _persisted = MutableStateFlow(false)
    /** True once the list has a real database id (a brand-new draft flips this on its first non-blank item). */
    val persisted: StateFlow<Boolean> = _persisted

    private var listDbId: Long = -1
    private var nextTempId: Long = -1
    private var inputSeq: Long = 0
    private var moveCompletedToBottom: Boolean = false
    private var loaded = false
    private var loadedKey: Long? = null

    // Stable local id -> real database id, for rows that have been persisted.
    private val dbId = HashMap<Long, Long>()
    private val creationMutex = Mutex()

    private val writer = DebouncedFieldWriter<ChecklistFieldKey>(scope, debounceMillis) { key, value ->
        when (key) {
            is ChecklistFieldKey.Title -> store.renameChecklist(listDbId, value.trim())
            is ChecklistFieldKey.Item -> dbId[key.localId]?.let { store.updateItemText(it, value) }
        }
    }

    /** [id] null starts a brand-new in-memory draft; otherwise load an established list. */
    fun load(id: Long?, moveCompletedToBottom: Boolean) {
        this.moveCompletedToBottom = moveCompletedToBottom
        if (loaded && loadedKey == id) return
        loaded = true
        loadedKey = id
        if (id == null) {
            _persisted.value = false
            listDbId = -1
            _title.value = ""
            _items.value = emptyList()
        } else {
            _persisted.value = true
            listDbId = id
            scope.launch {
                _title.value = store.getChecklist(id)?.name ?: ""
                val loadedItems = store.getItemsOnce(id)
                loadedItems.forEach { dbId[it.id] = it.id }
                _items.value = loadedItems
            }
        }
    }

    fun setTitle(text: String) {
        val seq = ++inputSeq
        _title.value = text
        if (_persisted.value) scope.launch { writer.schedule(ChecklistFieldKey.Title, text, seq) }
    }

    fun addItem(onAdded: (Long) -> Unit = {}) = addInternal(indent = 0, afterId = null, onAdded)

    fun addSubItem(afterId: Long, onAdded: (Long) -> Unit = {}) =
        addInternal(indent = 1, afterId = afterId, onAdded)

    private fun addInternal(indent: Int, afterId: Long?, onAdded: (Long) -> Unit) {
        val localId = nextTempId--
        val current = _items.value.toMutableList()
        val row = ChecklistItem(id = localId, checklistId = 0, indent = indent, position = current.size)
        val insertAt = if (afterId == null) {
            current.size
        } else {
            (current.indexOfFirst { it.id == afterId } + 1).coerceIn(0, current.size)
        }
        current.add(insertAt, row)
        _items.value = current
        onAdded(localId)
        scope.launch {
            if (_persisted.value) {
                ensureRowPersisted(localId)
                persistOrderNow()
            }
        }
    }

    fun updateText(itemId: Long, text: String) {
        val seq = ++inputSeq
        _items.value = _items.value.map { if (it.id == itemId) it.copy(text = text) else it }
        scope.launch {
            if (!_persisted.value && _items.value.any { it.text.isNotBlank() }) ensurePersisted()
            if (_persisted.value) {
                ensureRowPersisted(itemId)
                writer.schedule(ChecklistFieldKey.Item(itemId), text, seq)
            }
        }
    }

    fun setCompleted(itemId: Long, completed: Boolean) {
        var list = _items.value.map { if (it.id == itemId) it.copy(completed = completed) else it }
        if (completed && moveCompletedToBottom) {
            val moving = list.firstOrNull { it.id == itemId }
            if (moving != null) list = list.filterNot { it.id == itemId } + moving
        }
        _items.value = list
        scope.launch {
            if (_persisted.value) {
                ensureRowPersisted(itemId)
                dbId[itemId]?.let { store.setItemCompleted(it, completed) }
                persistOrderNow()
            }
        }
    }

    fun deleteItem(itemId: Long) {
        _items.value = _items.value.filterNot { it.id == itemId }
        writer.cancel(ChecklistFieldKey.Item(itemId))
        scope.launch {
            if (_persisted.value) {
                dbId.remove(itemId)?.let { store.deleteItem(it) }
                persistOrderNow()
            }
        }
    }

    fun reorder(orderedIds: List<Long>) {
        val byId = _items.value.associateBy { it.id }
        _items.value = orderedIds.mapNotNull { byId[it] }
        scope.launch {
            if (_persisted.value) {
                orderedIds.forEach { ensureRowPersisted(it) }
                store.applyOrder(orderedIds.mapNotNull { dbId[it] })
            }
        }
    }

    /** Persist this item's latest text now (e.g. its editor lost focus). */
    fun onItemFocusLost(itemId: Long) {
        scope.launch { writer.flush(ChecklistFieldKey.Item(itemId)) }
    }

    /** Persist the title's latest text now. */
    fun onTitleFocusLost() {
        scope.launch { writer.flush(ChecklistFieldKey.Title) }
    }

    /**
     * Flush all pending text and wait for it to finish, for an explicit
     * Back/navigation. Runs on [survivingScope] so the writes complete even if the
     * caller's scope is cancelled as the screen tears down.
     */
    suspend fun flushPending() {
        survivingScope.launch { writer.flushAll() }.join()
    }

    /** Best-effort flush when the app is backgrounded. Not a guarantee against process death. */
    fun flushOnBackground() {
        survivingScope.launch { writer.flushAll() }
    }

    /** On leaving an established list: flush pending text first, then drop rows left blank. */
    fun onLeave() {
        if (!_persisted.value) return
        val id = listDbId
        survivingScope.launch {
            writer.flushAll()
            store.deleteBlankItems(id)
        }
    }

    /** Explicit Save on a brand-new draft: persist if needed, flush, then report done. */
    fun save(onSaved: () -> Unit) {
        scope.launch {
            if (!_persisted.value && _items.value.any { it.text.isNotBlank() }) ensurePersisted()
            flushPending()
            onSaved()
        }
    }

    private suspend fun ensurePersisted() {
        if (_persisted.value) return
        creationMutex.withLock {
            if (_persisted.value) return
            val snapshot = _items.value
            val toInsert = snapshot.mapIndexed { index, row ->
                row.copy(id = 0, checklistId = 0, position = index)
            }
            val created = store.createListWithItems(_title.value.trim(), now(), toInsert)
            listDbId = created.listId
            snapshot.forEachIndexed { index, row ->
                created.itemIds.getOrNull(index)?.let { dbId[row.id] = it }
            }
            _persisted.value = true
        }
    }

    private suspend fun ensureRowPersisted(localId: Long) {
        if (dbId.containsKey(localId)) return
        val list = _items.value
        val index = list.indexOfFirst { it.id == localId }
        if (index < 0) return
        val newId = store.insertItem(list[index].copy(id = 0, checklistId = listDbId, position = index))
        dbId[localId] = newId
    }

    private suspend fun persistOrderNow() {
        val ids = _items.value.mapNotNull { dbId[it.id] }
        if (ids.isNotEmpty()) store.applyOrder(ids)
    }
}
