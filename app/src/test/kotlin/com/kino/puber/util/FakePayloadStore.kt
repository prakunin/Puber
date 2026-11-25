package com.kino.puber.util

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload

/** In-memory [PersistentPayloadStore] for tests: same semantics, no Room. */
class FakePayloadStore : PersistentPayloadStore {

    private val rows = mutableMapOf<String, StoredPayload>()

    /** Keys the store refuses to keep, as the Room store does for a payload over its row limit: the key's row stays. */
    var refusedKeys: Set<String> = emptySet()

    /** Payloads longer than this many chars are refused too, as the Room store refuses by bytes. */
    var maxPayloadLength: Int = Int.MAX_VALUE
    var readCount: Int = 0
    var onRead: (suspend (String) -> Unit)? = null

    /**
     * Runs after a bulk write has landed but before the writer resumes, so a test can interleave
     * whatever crossed it — an invalidate, a newer writer — with a write that is already durable.
     */
    var onWriteAll: (suspend (Set<String>) -> Unit)? = null

    override var generation: Long = 0L
        private set

    override suspend fun read(key: String): StoredPayload? {
        readCount += 1
        val stored = rows[key]
        onRead?.invoke(key)
        return stored
    }

    /** The single-key counterpart of [onWriteAll]: fires once the row has landed. */
    var onWrite: (suspend (String) -> Unit)? = null

    /** Fires before the row lands, so a test can model a writer this one then overwrites. */
    var onBeforeWrite: (suspend (String) -> Unit)? = null

    override suspend fun write(key: String, payload: String, updatedAt: Long): Boolean {
        val before = onBeforeWrite
        onBeforeWrite = null
        before?.invoke(key)
        val after = onWrite
        onWrite = null
        val stored = !refuses(key, payload)
        if (stored) rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        after?.invoke(key)
        return stored
    }

    /** Fires once [touch] has changed the row, before the caller resumes. */
    var onTouch: (suspend (String) -> Unit)? = null

    override suspend fun touch(key: String, updatedAt: Long) {
        val after = onTouch
        onTouch = null
        rows[key]?.let { row -> rows[key] = row.copy(updatedAt = updatedAt) }
        after?.invoke(key)
    }

    override suspend fun writeAll(payloads: Map<String, StoredPayload>): Set<String> {
        val hook = onWriteAll
        onWriteAll = null
        val refused = payloads.filter { (key, value) -> refuses(key, value.payload) }.keys
        payloads.forEach { (key, value) -> if (key !in refused) rows[key] = value }
        hook?.invoke(payloads.keys)
        return refused
    }

    private fun refuses(key: String, payload: String) = key in refusedKeys || payload.length > maxPayloadLength

    /** Fires once [remove] has dropped the row, before the caller resumes. */
    var onRemove: (suspend (String) -> Unit)? = null

    override suspend fun remove(key: String) {
        val after = onRemove
        onRemove = null
        rows.remove(key)
        after?.invoke(key)
    }

    val removedPrefixes = mutableListOf<String>()

    override suspend fun removeByPrefix(prefix: String) {
        removedPrefixes += prefix
        rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
    }

    var sweepCount: Int = 0

    /** Runs first in every [sweep], so a test can record the calling thread or throw through it. */
    var onSweep: (suspend () -> Unit)? = null

    override suspend fun sweep(olderThan: Long, keepNewest: Int) {
        sweepCount += 1
        onSweep?.invoke()
        rows.entries.filter { it.value.updatedAt < olderThan }.forEach { rows.remove(it.key) }
        rows.entries.sortedByDescending { it.value.updatedAt }.drop(keepNewest).forEach { rows.remove(it.key) }
    }

    override suspend fun clear() {
        generation += 1
        try {
            rows.clear()
        } finally {
            // Readers that began after the first bump but before the map finished clearing need a
            // different token from both the pre-clear and in-progress states.
            generation += 1
        }
    }
}
