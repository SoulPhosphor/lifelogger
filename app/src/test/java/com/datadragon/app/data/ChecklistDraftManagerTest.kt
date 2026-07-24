package com.datadragon.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory [ChecklistStore] that records the order of every operation, so tests
 * can assert both final state and relative ordering (e.g. flush before cleanup).
 */
private class FakeChecklistStore : ChecklistStore {
    class ListRow(var name: String, var draft: Boolean, var createdAt: Long)
    class ItemRow(var text: String, var completed: Boolean, var indent: Int, var position: Int)

    val lists = LinkedHashMap<Long, ListRow>()
    val rows = LinkedHashMap<Long, ItemRow>()
    val itemList = LinkedHashMap<Long, Long>() // itemId -> listId
    val ops = mutableListOf<String>()
    var createCalls = 0

    private var nextListId = 100L
    private var nextItemId = 1000L

    override suspend fun getChecklist(id: Long): Checklist? =
        lists[id]?.let { Checklist(id = id, name = it.name, createdAt = it.createdAt, draft = it.draft) }

    override suspend fun getItemsOnce(checklistId: Long): List<ChecklistItem> =
        rows.filterKeys { itemList[it] == checklistId }
            .map { (id, r) -> ChecklistItem(id, checklistId, r.text, r.completed, r.indent, r.position) }
            .sortedBy { it.position }

    override suspend fun renameChecklist(id: Long, name: String) {
        lists[id]?.name = name
        ops.add("rename:$id:$name")
    }

    override suspend fun insertItem(item: ChecklistItem): Long {
        val id = nextItemId++
        rows[id] = ItemRow(item.text, item.completed, item.indent, item.position)
        itemList[id] = item.checklistId
        ops.add("insert:$id:${item.text}")
        return id
    }

    override suspend fun updateItemText(id: Long, text: String) {
        rows[id]?.text = text
        ops.add("updateText:$id:$text")
    }

    override suspend fun setItemCompleted(id: Long, completed: Boolean) {
        rows[id]?.completed = completed
        ops.add("complete:$id:$completed")
    }

    override suspend fun deleteItem(id: Long) {
        rows.remove(id)
        itemList.remove(id)
        ops.add("delete:$id")
    }

    override suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> rows[id]?.position = index }
        ops.add("order:${orderedIds.joinToString(",")}")
    }

    override suspend fun deleteBlankItems(checklistId: Long) {
        val blanks = rows.filterKeys { itemList[it] == checklistId }
            .filterValues { it.text.isBlank() }
            .keys.toList()
        blanks.forEach { rows.remove(it); itemList.remove(it) }
        ops.add("deleteBlank:$checklistId:${blanks.joinToString(",")}")
    }

    override suspend fun createDraftWithItems(
        name: String,
        createdAt: Long,
        items: List<ChecklistItem>,
    ): CreatedList {
        createCalls++
        val listId = nextListId++
        lists[listId] = ListRow(name, draft = true, createdAt = createdAt)
        val ids = items.mapIndexed { index, item ->
            val id = nextItemId++
            rows[id] = ItemRow(item.text, item.completed, item.indent, index)
            itemList[id] = listId
            id
        }
        ops.add("createDraft:$listId:$name:${items.joinToString(",") { it.text }}")
        return CreatedList(listId, ids)
    }

    override suspend fun finalizeChecklist(id: Long) {
        lists[id]?.draft = false
        ops.add("finalize:$id")
    }

    override suspend fun deleteChecklistWithItems(id: Long) {
        val itemIds = itemList.filterValues { it == id }.keys.toList()
        itemIds.forEach { rows.remove(it); itemList.remove(it) }
        lists.remove(id)
        ops.add("deleteList:$id:${itemIds.joinToString(",")}")
    }

    override suspend fun mostRecentDraft(): Checklist? =
        lists.entries.filter { it.value.draft }
            .maxByOrNull { it.value.createdAt }
            ?.let { Checklist(id = it.key, name = it.value.name, createdAt = it.value.createdAt, draft = true) }

    /** A pre-existing draft in the store, as if written before a process death. */
    fun seedDraft(name: String, createdAt: Long, itemTexts: List<String>): Long {
        val listId = nextListId++
        lists[listId] = ListRow(name, draft = true, createdAt = createdAt)
        itemTexts.forEachIndexed { index, text ->
            val id = nextItemId++
            rows[id] = ItemRow(text, false, 0, index)
            itemList[id] = listId
        }
        return listId
    }
}

class ChecklistDraftManagerTest {

    private fun manager(store: FakeChecklistStore, scope: CoroutineScope, surviving: CoroutineScope) =
        ChecklistDraftManager(store, scope, surviving, 300L) { 0L }

    @Test
    fun newListStartsAsUnsavedDraft() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)
        assertTrue(mgr.isDraft.value)
        assertFalse(mgr.persisted.value)
    }

    @Test
    fun persistsFirstNonBlankItemAsADraftHiddenFromHome() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        mgr.addItem { aId = it }
        mgr.addItem { bId = it }
        advanceUntilIdle()
        assertTrue(store.lists.isEmpty())

        mgr.setTitle("Groceries")
        mgr.updateText(aId, "Milk")
        advanceUntilIdle()

        assertEquals(1, store.createCalls)
        assertTrue(mgr.persisted.value)
        // Written as a draft (excluded from Home), NOT a saved list.
        assertTrue(mgr.isDraft.value)
        val listId = store.lists.keys.first()
        assertTrue(store.lists[listId]!!.draft)
        assertTrue("the new list is a recoverable draft", store.mostRecentDraft() != null)
    }

    @Test
    fun severalTextEventsCreateExactlyOneDraft() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        mgr.addItem { aId = it }
        mgr.addItem { bId = it }
        advanceUntilIdle()

        mgr.updateText(aId, "Milk")
        mgr.updateText(bId, "Bread")
        advanceUntilIdle()

        assertEquals(1, store.createCalls)
        assertEquals(1, store.lists.size)
    }

    @Test
    fun saveFinalizesDraftIntoASavedList() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        mgr.addItem { aId = it }
        mgr.updateText(aId, "Milk")
        advanceUntilIdle() // draft persisted

        val listId = store.lists.keys.first()
        assertTrue(store.lists[listId]!!.draft)

        var left = false
        mgr.save { left = true }
        advanceUntilIdle()

        assertTrue(left)
        assertFalse("draft flag must be cleared by Save", store.lists[listId]!!.draft)
        assertFalse(mgr.isDraft.value)
        assertNull(store.mostRecentDraft())
    }

    @Test
    fun discardDeletesThePersistedDraftAndItsItems() = runTest {
        val store = FakeChecklistStore()
        val surviving = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val mgr = manager(store, this, surviving)
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        mgr.addItem { aId = it }
        mgr.addItem { bId = it }
        mgr.updateText(aId, "Milk")
        mgr.updateText(bId, "Bread")
        advanceUntilIdle() // draft + items persisted

        val listId = store.lists.keys.first()
        assertTrue(store.lists.containsKey(listId))
        assertTrue(store.rows.isNotEmpty())

        var left = false
        mgr.discardDraft { left = true }
        advanceUntilIdle()

        assertTrue(left)
        assertFalse("draft list row must be deleted", store.lists.containsKey(listId))
        assertTrue("all items must be deleted", store.rows.isEmpty())
    }

    @Test
    fun recoveredDraftLoadsAndIsIdentifiedAsADraft() = runTest {
        // Simulates a process restart: a draft is already in the store, and a fresh
        // manager opens it by id.
        val store = FakeChecklistStore()
        val draftId = store.seedDraft("Camping", createdAt = 5, itemTexts = listOf("Tent", "Stove"))

        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(draftId, moveCompletedToBottom = false)
        advanceUntilIdle()

        assertTrue("a recovered draft must still read as a draft", mgr.isDraft.value)
        assertEquals("Camping", mgr.title.value)
        assertEquals(listOf("Tent", "Stove"), mgr.items.value.map { it.text })

        // Saving a recovered draft finalizes it like any other.
        mgr.save {}
        advanceUntilIdle()
        assertFalse(store.lists[draftId]!!.draft)
    }

    @Test
    fun establishedListLoadsAsNotADraft() = runTest {
        val store = FakeChecklistStore()
        // A normal saved list (draft = false).
        val id = store.seedDraft("Saved", createdAt = 1, itemTexts = listOf("x")).also {
            store.lists[it]!!.draft = false
        }
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(id, moveCompletedToBottom = false)
        advanceUntilIdle()
        assertFalse(mgr.isDraft.value)
    }

    @Test
    fun flushPersistsPendingEditMadeBeforeDebounceWindow() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        mgr.addItem { aId = it }
        mgr.updateText(aId, "Milk")
        advanceUntilIdle()
        val rowId = store.rows.keys.first()

        mgr.updateText(aId, "Milk and eggs")
        advanceTimeBy(100)
        assertEquals("Milk", store.rows[rowId]!!.text)

        mgr.flushPending()
        advanceUntilIdle()
        assertEquals("Milk and eggs", store.rows[rowId]!!.text)
    }

    @Test
    fun blankCleanupRunsAfterPendingTextIsFlushed() = runTest {
        val store = FakeChecklistStore()
        val mgr = manager(store, this, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        mgr.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        mgr.addItem { aId = it }
        mgr.addItem { bId = it }
        mgr.updateText(aId, "Milk")
        advanceUntilIdle()

        mgr.updateText(bId, "Bread")
        advanceTimeBy(50)
        mgr.onLeave()
        advanceUntilIdle()

        val bRow = store.rows.values.firstOrNull { it.text == "Bread" }
        assertTrue(bRow != null)

        val writeIndex = store.ops.indexOfLast { it.startsWith("updateText:") && it.endsWith(":Bread") }
        val cleanupIndex = store.ops.indexOfLast { it.startsWith("deleteBlank:") }
        assertTrue("expected a Bread write", writeIndex >= 0)
        assertTrue("expected a blank cleanup", cleanupIndex >= 0)
        assertTrue("cleanup must run after the flush", writeIndex < cleanupIndex)
    }
}
