package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity

/** A stored payload and the moment its content was read from the server, in epoch milliseconds. */
data class StoredPayload(
    val payload: String,
    val updatedAt: Long,
)

interface PersistentPayloadStore {
    /**
     * A monotonic token that changes while and after this store is wiped.
     *
     * Published rather than kept private because a wipe has to reach further than the table: every
     * cache tier layered on top of this store is holding the same session's data and has no other way
     * to learn the session ended. A reader that remembers the generation it last saw can tell that
     * everything it cached before belongs to a session that is over.
     */
    val generation: Long

    suspend fun read(key: String): StoredPayload?
    suspend fun write(key: String, payload: String, updatedAt: Long)
    suspend fun touch(key: String, updatedAt: Long)
    suspend fun remove(key: String)
    suspend fun removeByPrefix(prefix: String)
    suspend fun clear()
}

class RoomPersistentPayloadStore(
    private val dao: CachedPayloadDao,
) : PersistentPayloadStore {

    /**
     * Bumped by every [clear], so a write that began under the previous session can notice the
     * session ended and take its row back out.
     *
     * The cache holds one account's viewing history. A revalidation already in flight when the user
     * signs out would otherwise land after the wipe and hand that history to the next account. Same
     * hazard, and the same remedy, as the watch-state sync.
     */
    @Volatile
    override var generation: Long = 0L
        private set

    override suspend fun read(key: String): StoredPayload? {
        return dao.read(key)?.let { row -> StoredPayload(payload = row.payload, updatedAt = row.updatedAt) }
    }

    override suspend fun write(key: String, payload: String, updatedAt: Long) {
        val generation = this.generation
        dao.upsert(CachedPayloadEntity(key = key, payload = payload, updatedAt = updatedAt))
        if (generation != this.generation) {
            dao.delete(key)
        }
    }

    override suspend fun touch(key: String, updatedAt: Long) {
        dao.touch(key = key, updatedAt = updatedAt)
    }

    override suspend fun remove(key: String) {
        dao.delete(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        dao.deleteByPrefix(prefix)
    }

    override suspend fun clear() {
        generation += 1
        try {
            dao.clear()
        } finally {
            // Readers that began after the first bump but before the database finished clearing
            // need a different token from both the pre-clear and in-progress states.
            generation += 1
        }
    }
}
