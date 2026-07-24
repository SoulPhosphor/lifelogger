package com.datadragon.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coalesces frequent value changes for a set of keys into at most one database
 * write per key per debounce window, and guarantees a newer value can never be
 * overwritten by an older one.
 *
 * Each key (a form field, a list item) is versioned by a caller-supplied,
 * monotonically increasing sequence number assigned in input order. The writer
 * always persists the *latest* value seen for a key, and records the highest
 * version written, so a late or out-of-order write is dropped rather than
 * clobbering fresher text. The on-screen state stays the immediate source of
 * truth; this only governs when that state reaches the database.
 *
 * All persistence for one key is serialized through a per-key [Mutex]; the small
 * bookkeeping maps are guarded by an intrinsic lock. [write] runs outside that
 * lock (only inside the per-key mutex) so a slow database call never blocks
 * scheduling on other keys.
 */
class DebouncedFieldWriter<K>(
    private val scope: CoroutineScope,
    private val debounceMillis: Long,
    private val write: suspend (K, String) -> Unit,
) {
    private data class Latest(val value: String, val version: Long)

    private val lock = Any()
    private val latest = HashMap<K, Latest>()
    private val jobs = HashMap<K, Job>()
    private val written = HashMap<K, Long>()
    private val mutexes = HashMap<K, Mutex>()

    private fun mutexFor(key: K): Mutex = synchronized(lock) { mutexes.getOrPut(key) { Mutex() } }

    /**
     * Record [value] as the newest text for [key] and (re)start its debounce
     * timer. [version] must increase with input order; an out-of-order (older)
     * version is ignored so a delayed keystroke can't undo a newer one.
     */
    fun schedule(key: K, value: String, version: Long) {
        synchronized(lock) {
            val current = latest[key]
            if (current != null && version <= current.version) return
            latest[key] = Latest(value, version)
            jobs.remove(key)?.cancel()
            jobs[key] = scope.launch {
                delay(debounceMillis)
                commit(key)
            }
        }
    }

    private suspend fun commit(key: K) {
        mutexFor(key).withLock {
            val target = synchronized(lock) { latest[key] } ?: return
            val alreadyWritten = synchronized(lock) { written[key] ?: Long.MIN_VALUE }
            if (target.version <= alreadyWritten) return
            write(key, target.value)
            synchronized(lock) {
                if (target.version > (written[key] ?: Long.MIN_VALUE)) written[key] = target.version
            }
        }
    }

    /** Cancel the debounce timer and persist the latest value for [key] now. */
    suspend fun flush(key: K) {
        synchronized(lock) { jobs.remove(key) }?.cancel()
        commit(key)
    }

    /** Flush every key that has a pending value. */
    suspend fun flushAll() {
        val keys = synchronized(lock) { latest.keys.toList() }
        keys.forEach { flush(it) }
    }

    /**
     * Drop any pending write for [key] entirely (used when the row it belongs to
     * is deleted, so a queued write can't resurrect it).
     */
    fun cancel(key: K) {
        synchronized(lock) {
            jobs.remove(key)?.cancel()
            latest.remove(key)
        }
    }

    /** Drop every pending write (used when the whole list is discarded). */
    fun cancelAll() {
        synchronized(lock) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            latest.clear()
        }
    }
}
