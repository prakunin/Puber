package com.kino.puber.util

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload

/** In-memory [PersistentPayloadStore] for tests: same semantics, no Room. */
class FakePayloadStore : PersistentPayloadStore {

    private val rows = mutableMapOf<String, StoredPayload>()
    var readCount: Int = 0
    var onRead: (suspend (String) -> Unit)? = null

    override var generation: Long = 0L
        private set

    override suspend fun read(key: String): StoredPayload? {
        readCount += 1
        val stored = rows[key]
        onRead?.invoke(key)
        return stored
    }

    override suspend fun write(key: String, payload: String, updatedAt: Long) {
        rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
    }

    override suspend fun touch(key: String, updatedAt: Long) {
        rows[key]?.let { row -> rows[key] = row.copy(updatedAt = updatedAt) }
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
