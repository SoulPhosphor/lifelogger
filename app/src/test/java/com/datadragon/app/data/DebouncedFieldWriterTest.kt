package com.datadragon.app.data

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DebouncedFieldWriterTest {

    @Test
    fun rapidTypingCoalescesToLatestValueWithOneWrite() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val writer = DebouncedFieldWriter<String>(this, 300L) { k, v -> writes.add(k to v) }

        writer.schedule("a", "h", 1)
        writer.schedule("a", "he", 2)
        writer.schedule("a", "hel", 3)

        // Nothing written until the debounce window elapses.
        advanceTimeBy(299)
        runCurrent()
        assertEquals(emptyList<Pair<String, String>>(), writes)

        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(listOf("a" to "hel"), writes)
    }

    @Test
    fun outOfOrderScheduleDoesNotOverwriteNewerValue() = runTest {
        val writes = mutableListOf<String>()
        val writer = DebouncedFieldWriter<String>(this, 300L) { _, v -> writes.add(v) }

        // A newer keystroke (version 5) arrives, then a delayed older one (version 4).
        writer.schedule("a", "newer", 5)
        writer.schedule("a", "older", 4)
        advanceUntilIdle()

        assertEquals(listOf("newer"), writes)
    }

    @Test
    fun alreadyWrittenValueIsNotRewrittenByFlush() = runTest {
        val writes = mutableListOf<String>()
        val writer = DebouncedFieldWriter<String>(this, 300L) { _, v -> writes.add(v) }

        writer.schedule("a", "v1", 1)
        advanceUntilIdle()
        assertEquals(listOf("v1"), writes)

        // Nothing new scheduled: an explicit flush must not write the same value again.
        writer.flush("a")
        advanceUntilIdle()
        assertEquals(listOf("v1"), writes)
    }

    @Test
    fun flushPersistsLatestValueBeforeDebounceExpires() = runTest {
        val writes = mutableListOf<String>()
        val writer = DebouncedFieldWriter<String>(this, 300L) { _, v -> writes.add(v) }

        writer.schedule("a", "typed", 1)
        advanceTimeBy(50) // well within the 300ms window
        writer.flush("a")
        assertEquals(listOf("typed"), writes)

        // The cancelled debounce timer must not fire a duplicate write later.
        advanceUntilIdle()
        assertEquals(listOf("typed"), writes)
    }

    @Test
    fun cancelDropsPendingWrite() = runTest {
        val writes = mutableListOf<String>()
        val writer = DebouncedFieldWriter<String>(this, 300L) { _, v -> writes.add(v) }

        writer.schedule("a", "gone", 1)
        writer.cancel("a")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), writes)
    }
}
