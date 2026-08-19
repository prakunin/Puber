package com.kino.puber.util

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload

/** In-memory [PersistentPayloadStore] for tests: same semantics, no Room. */
class FakePayloadStore : PersistentPayloadStore {

    private val rows = mutableMapOf<String, StoredPayload>()
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

    override suspend fun write(key: String, payload: String, updatedAt: Long) {
        val before = onBeforeWrite
        onBeforeWrite = null
        before?.invoke(key)
        val after = onWrite
        onWrite = null
        rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        after?.invoke(key)
    }

    override suspend fun touch(key: String, updatedAt: Long) {
        rows[key]?.let { row -> rows[key] = row.copy(updatedAt = updatedAt) }
    }

    override suspend fun writeAll(payloads: Map<String, StoredPayload>) {
        val hook = onWriteAll
        onWriteAll = null
        payloads.forEach { (key, value) -> rows[key] = value }
        hook?.invoke(payloads.keys)
    }

    override suspend fun remove(key: String) {
        rows.remove(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
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
