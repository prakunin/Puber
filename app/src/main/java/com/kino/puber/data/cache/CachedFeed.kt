package com.kino.puber.data.cache

import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.repository.PersistentPayloadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** What a [CachedFeed] hands its caller. */
sealed interface Cached<out V> {
    /** A usable value. [isStale] means a revalidation is on its way and a second value will follow. */
    data class Value<V>(val value: V, val isStale: Boolean) : Cached<V>

    /**
     * The revalidation failed after a value was already emitted. The caller keeps showing what it
     * has; this exists so it can say so quietly rather than replace content with an error.
     */
    data class RefreshFailed(val error: Throwable) : Cached<Nothing>
}

/**
 * Serves one namespace of cached values: whatever is stored is emitted at once, and the network is
 * consulted behind that emission.
 *
 * A failure only ever surfaces as an exception when there was nothing to show. Once a value has been
 * emitted, a failed refresh is reported as [Cached.RefreshFailed] and the value stands.
 */
class CachedFeed<V : Any>(
    private val store: PersistentPayloadStore,
    private val serializer: KSerializer<V>,
    private val ttl: Duration,
    private val keyPrefix: String,
    private val json: Json = DefaultJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Deduplicates loader calls so two screens asking for the same key at the same moment cost one
     * request. Its own TTL is the feed's, so a value it still holds is by definition fresh.
     */
    private val inFlight = TypedTtlCacheImpl<String, V>(
        defaultTtl = ttl,
        // One clock has to govern both tiers, or a test that advances the fake clock leaves the
        // in-memory tier frozen in real time and the two disagree about what is fresh.
        nowNanos = { clock() * NANOS_PER_MILLI },
    )

    /**
     * The store generation this feed's memory tier belongs to.
     *
     * A wipe empties the table but cannot reach [inFlight] — the wipe is owned by a global, and a
     * feed may well live in a screen scope the global has never heard of. So the feed asks the store
     * instead of waiting to be told: a generation that has moved means everything cached here
     * describes a session, or a domain, that is over.
     */
    private val seenGeneration = AtomicLong(store.generation)

    fun load(
        key: String,
        force: Boolean = false,
        loader: suspend () -> V,
    ): Flow<Cached<V>> = flow {
        dropMemoryTierIfStoreWasWiped()
        val stored = readUsable(key)
        var emitted = false
        if (stored != null) {
            val isStale = force || clock() - stored.updatedAt > ttl.inWholeMilliseconds
            emit(Cached.Value(stored.value, isStale = isStale))
            emitted = true
            if (!isStale) return@flow
        }
        if (force) {
            inFlight.remove(key)
        }
        try {
            val fresh = inFlight.getOrPut(key) {
                loader().also { value ->
                    store.write(
                        key = key,
                        payload = json.encodeToString(serializer, value),
                        updatedAt = clock(),
                    )
                }
            }
            emit(Cached.Value(fresh, isStale = false))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (!emitted) throw error
            emit(Cached.RefreshFailed(error))
        }
    }

    /**
     * Keeps the payload readable but guarantees the next [load] revalidates it.
     *
     * Used where something happened that probably changed the server's answer but the entry is still
     * worth drawing — a saved playback position, for instance. Deleting the row instead would trade a
     * slightly stale screen for a spinner.
     */
    suspend fun markStale(key: String) {
        inFlight.remove(key)
        store.touch(key = key, updatedAt = clock() - ttl.inWholeMilliseconds - 1)
    }

    /** Drops the entry, so the next [load] has nothing to emit before the network answers. */
    suspend fun invalidate(key: String) {
        inFlight.remove(key)
        store.remove(key)
    }

    /** Drops every entry in this feed's namespace. */
    suspend fun invalidateNamespace() {
        inFlight.clear()
        store.removeByPrefix(keyPrefix)
    }

    /**
     * Only the thread that wins the compare-and-set clears, so a burst of concurrent loads after a
     * wipe costs one clear rather than one per caller — and no caller can clear a tier that was
     * refilled under a generation it has already accounted for.
     */
    private fun dropMemoryTierIfStoreWasWiped() {
        val current = store.generation
        val seen = seenGeneration.get()
        if (current != seen && seenGeneration.compareAndSet(seen, current)) {
            inFlight.clear()
        }
    }

    @Suppress("ReturnCount")
    private suspend fun readUsable(key: String): Usable<V>? {
        val stored = store.read(key) ?: return null
        if (clock() - stored.updatedAt > HardCeiling.inWholeMilliseconds) return null
        val value = runCatching { json.decodeFromString(serializer, stored.payload) }.getOrElse {
            // Written by a build whose model differed. Nothing to salvage, and keeping it would make
            // every future read pay the same failed decode.
            store.remove(key)
            return null
        }
        return Usable(value = value, updatedAt = stored.updatedAt)
    }

    private class Usable<V>(val value: V, val updatedAt: Long)

    companion object {
        /**
         * Past this, a stored payload is treated as absent. A screen drawn from week-old data is
         * worse than one that admits it is loading.
         */
        val HardCeiling: Duration = 7.days

        val DefaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
