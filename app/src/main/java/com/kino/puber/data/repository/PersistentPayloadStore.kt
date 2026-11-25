package com.kino.puber.data.repository

import com.kino.puber.data.db.CachedPayloadDao
import com.kino.puber.data.db.CachedPayloadEntity
import kotlinx.coroutines.CancellationException
import timber.log.Timber

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
    suspend fun readAll(keys: List<String>): Map<String, StoredPayload> = buildMap {
        keys.forEach { key -> read(key)?.let { put(key, it) } }
    }

    /** Returns false when the payload is over the row limit: nothing is written and the key's row is untouched. */
    suspend fun write(key: String, payload: String, updatedAt: Long): Boolean

    /** Returns the keys [write] reported as not stored; the rest are stored. */
    suspend fun writeAll(payloads: Map<String, StoredPayload>): Set<String> =
        payloads.filter { (key, value) -> !write(key, value.payload, value.updatedAt) }.keys

    suspend fun touch(key: String, updatedAt: Long)
    suspend fun remove(key: String)
    suspend fun removeByPrefix(prefix: String)

    /** Deletes the rows with `updatedAt < olderThan`, then every row but the `keepNewest` newest. */
    suspend fun sweep(olderThan: Long, keepNewest: Int)
    suspend fun clear()
}

class RoomPersistentPayloadStore(
    private val dao: CachedPayloadDao,
) : PersistentPayloadStore {

    companion object {
        /** Rows over this many UTF-8 bytes are neither written nor read: see [utf8Size]. */
        const val MAX_ROW_BYTES = 1 shl 20
    }

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
        val row = dao.read(key, MAX_ROW_BYTES)
        if (row == null) {
            deleteOversizedAmong(listOf(key))
            return null
        }
        return StoredPayload(payload = row.payload, updatedAt = row.updatedAt)
    }

    override suspend fun readAll(keys: List<String>): Map<String, StoredPayload> {
        if (keys.isEmpty()) return emptyMap()
        val rows = dao.readAll(keys, MAX_ROW_BYTES)
        // A key with no row is either unknown or too big for the cursor window; only the second needs
        // deleting, and the delete's own size predicate keeps it from touching a row written meanwhile.
        val missing = keys - rows.mapTo(HashSet()) { it.key }
        if (missing.isNotEmpty()) deleteOversizedAmong(missing)
        return rows.associate { row ->
            row.key to StoredPayload(payload = row.payload, updatedAt = row.updatedAt)
        }
    }

    /**
     * Selects first, so a miss on a key that was never stored opens no write transaction. A failed
     * cleanup is logged and dropped: the read already has its answer.
     */
    private suspend fun deleteOversizedAmong(keys: List<String>) {
        try {
            val oversized = dao.oversizedKeys(keys, MAX_ROW_BYTES)
            if (oversized.isNotEmpty()) dao.deleteOversized(oversized, MAX_ROW_BYTES)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w(e, "Could not delete oversized cached payloads")
        }
    }

    override suspend fun write(key: String, payload: String, updatedAt: Long): Boolean {
        if (isOversized(payload)) return false
        val generation = this.generation
        dao.upsert(CachedPayloadEntity(key = key, payload = payload, updatedAt = updatedAt))
        if (generation != this.generation) {
            dao.delete(key)
        }
        return true
    }

    override suspend fun writeAll(payloads: Map<String, StoredPayload>): Set<String> {
        if (payloads.isEmpty()) return emptySet()
        val (oversized, kept) = payloads.entries.partition { isOversized(it.value.payload) }
        val generation = this.generation
        if (kept.isNotEmpty()) {
            dao.upsertAll(
                kept.map { (key, value) ->
                    CachedPayloadEntity(key = key, payload = value.payload, updatedAt = value.updatedAt)
                }
            )
            if (generation != this.generation) {
                kept.forEach { dao.delete(it.key) }
            }
        }
        return oversized.mapTo(HashSet()) { it.key }
    }

    private fun isOversized(payload: String): Boolean = utf8Size(payload, MAX_ROW_BYTES) > MAX_ROW_BYTES

    override suspend fun touch(key: String, updatedAt: Long) {
        dao.touch(key = key, updatedAt = updatedAt)
    }

    override suspend fun remove(key: String) {
        dao.delete(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        dao.deleteByPrefix(prefix)
    }

    override suspend fun sweep(olderThan: Long, keepNewest: Int) {
        dao.deleteOlderThan(olderThan)
        dao.deleteBeyondNewest(keepNewest)
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

/**
 * The UTF-8 size of [text], exact while it is at most [stopAfter]; any value above [stopAfter] once
 * past it. SQLite measures the stored column in bytes, so the limit must be counted in bytes too: a
 * Cyrillic payload is twice its length in chars. Counts without encoding, so it does not allocate.
 */
private const val ONE_BYTE_BELOW = 0x80
private const val TWO_BYTES_BELOW = 0x800

private fun utf8Size(text: String, stopAfter: Int): Int {
    if (text.length > stopAfter) return text.length
    if (text.length.toLong() * 3 <= stopAfter) return 0
    var bytes = 0
    var i = 0
    while (i < text.length && bytes <= stopAfter) {
        val c = text[i]
        when {
            c.code < ONE_BYTE_BELOW -> bytes += 1
            c.code < TWO_BYTES_BELOW -> bytes += 2
            c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                bytes += 4
                i++
            }
            else -> bytes += 3
        }
        i++
    }
    return bytes
}
