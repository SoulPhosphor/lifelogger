package com.datadragon.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory [ChecklistStore] that records the order of every operation, so tests
 * can assert both final state and relative ordering (e.g. flush before cleanup).
 */
private class FakeChecklistStore : ChecklistStore {
    class Row(var text: String, var completed: Boolean, var indent: Int, var position: Int)

    val lists = LinkedHashMap<Long, String>()
    val rows = LinkedHashMap<Long, Row>()
    val itemList = LinkedHashMap<Long, Long>() // itemId -> listId
    val ops = mutableListOf<String>()
    var createCalls = 0

    private var nextListId = 100L
    private var nextItemId = 1000L

    override suspend fun getChecklist(id: Long): Checklist? =
        lists[id]?.let { Checklist(id = id, name = it, createdAt = 0) }

    override suspend fun getItemsOnce(checklistId: Long): List<ChecklistItem> =
        rows.filterKeys { itemList[it] == checklistId }
            .map { (id, r) -> ChecklistItem(id, checklistId, r.text, r.completed, r.indent, r.position) }
            .sortedBy { it.position }

    override suspend fun renameChecklist(id: Long, name: String) {
        lists[id] = name
        ops.add("rename:$id:$name")
    }

    override suspend fun insertItem(item: ChecklistItem): Long {
        val id = nextItemId++
        rows[id] = Row(item.text, item.completed, item.indent, item.position)
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

    override suspend fun createListWithItems(
        name: String,
        createdAt: Long,
        items: List<ChecklistItem>,
    ): CreatedList {
        createCalls++
        val listId = nextListId++
        lists[listId] = name
        val ids = items.mapIndexed { index, item ->
            val id = nextItemId++
            rows[id] = Row(item.text, item.completed, item.indent, index)
            itemList[id] = listId
            id
        }
        ops.add("create:$listId:$name:${items.joinToString(",") { it.text }}")
        return CreatedList(listId, ids)
    }
}

class ChecklistDraftManagerTest {

    @Test
    fun persistsOnFirstNonBlankItemIncludingTitleAndAllRows() = runTest {
        val store = FakeChecklistStore()
        val surviving = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val manager = ChecklistDraftManager(store, this, surviving, 300L) { 0L }
        manager.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        manager.addItem { aId = it }
        manager.addItem { bId = it }
        advanceUntilIdle()

        // Still a pure in-memory draft — nothing in the database yet.
        assertTrue(store.lists.isEmpty())
        assertFalse(manager.persisted.value)

        manager.setTitle("Groceries")
        manager.updateText(aId, "Milk")
        advanceUntilIdle()

        assertEquals(1, store.createCalls)
        assertTrue(manager.persisted.value)
        assertEquals("Groceries", store.lists.values.first())
        // The first persistence includes the title and all current rows (Milk + the still-blank B).
        val persistedTexts = store.getItemsOnce(store.lists.keys.first()).map { it.text }
        assertEquals(2, persistedTexts.size)
        assertTrue(persistedTexts.contains("Milk"))
        assertTrue(persistedTexts.contains(""))
    }

    @Test
    fun severalTextEventsCreateExactlyOneList() = runTest {
        val store = FakeChecklistStore()
        val surviving = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val manager = ChecklistDraftManager(store, this, surviving, 300L) { 0L }
        manager.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        manager.addItem { aId = it }
        manager.addItem { bId = it }
        advanceUntilIdle()

        // Two nonblank text events queued before either coroutine runs.
        manager.updateText(aId, "Milk")
        manager.updateText(bId, "Bread")
        advanceUntilIdle()

        assertEquals(1, store.createCalls)
        assertEquals(1, store.lists.size)
    }

    @Test
    fun flushPersistsPendingEditMadeBeforeDebounceWindow() = runTest {
        val store = FakeChecklistStore()
        val surviving = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val manager = ChecklistDraftManager(store, this, surviving, 300L) { 0L }
        manager.load(null, moveCompletedToBottom = false)

        var aId = 0L
        manager.addItem { aId = it }
        manager.updateText(aId, "Milk")
        advanceUntilIdle() // persists with "Milk"
        val rowId = store.rows.keys.first()

        // Type more, then leave before the 300ms debounce fires.
        manager.updateText(aId, "Milk and eggs")
        advanceTimeBy(100)
        assertEquals("Milk", store.rows[rowId]!!.text) // not yet written

        manager.flushPending()
        advanceUntilIdle()
        assertEquals("Milk and eggs", store.rows[rowId]!!.text)
    }

    @Test
    fun blankCleanupRunsAfterPendingTextIsFlushed() = runTest {
        val store = FakeChecklistStore()
        val surviving = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val manager = ChecklistDraftManager(store, this, surviving, 300L) { 0L }
        manager.load(null, moveCompletedToBottom = false)

        var aId = 0L
        var bId = 0L
        manager.addItem { aId = it }
        manager.addItem { bId = it }
        manager.updateText(aId, "Milk")
        advanceUntilIdle() // persists: A="Milk", B="" (blank)

        // Type into the blank row B, then leave before its debounce fires.
        manager.updateText(bId, "Bread")
        advanceTimeBy(50)
        manager.onLeave()
        advanceUntilIdle()

        // B's text reached the database and B was NOT deleted as blank.
        val bRow = store.rows.values.firstOrNull { it.text == "Bread" }
        assertTrue(bRow != null)

        // Cleanup happened strictly after the pending text write.
        val writeIndex = store.ops.indexOfLast { it.startsWith("updateText:") && it.endsWith(":Bread") }
        val cleanupIndex = store.ops.indexOfLast { it.startsWith("deleteBlank:") }
        assertTrue("expected a Bread write", writeIndex >= 0)
        assertTrue("expected a blank cleanup", cleanupIndex >= 0)
        assertTrue("cleanup must run after the flush", writeIndex < cleanupIndex)
    }
}
