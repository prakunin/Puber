package com.kino.puber.data.cache

import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/** What a [CachedFeed] hands its caller. */
sealed interface Cached<out V> {
    /**
     * A usable value. [isStale] means a revalidation is on its way and a second value will follow.
     *
     * [updatedAt] is when this value was accepted, not when it was handed over: two readers joining
     * one load, or reading the same entry minutes apart, are told the same age. Callers that record
     * the value somewhere ordered by time — the watch-state index — must pass this rather than their
     * own clock, or a cached item outranks a newer observation simply because it was read later.
     * It defaults to the epoch, which orders behind everything, so a value of unknown age can never
     * win against a real observation.
     */
    data class Value<V>(val value: V, val isStale: Boolean, val updatedAt: Long = 0L) : Cached<V>

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
    ttl: Duration,
    private val keyPrefix: String,
    private val json: Json = DefaultJson,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val defaultTtl = ttl

    /**
     * The current-memory tier and the single-flight coordinator behind it.
     *
     * A completed value is kept only for the part of its TTL that remains according to its original
     * server timestamp. Room and memory therefore cannot disagree about freshness or silently
     * extend one another's lifetime.
     */
    private val memory = TypedTtlCacheImpl<String, Stamped<V>>(
        defaultTtl = defaultTtl,
        // One clock has to govern both tiers, or a test that advances the fake clock leaves the
        // in-memory tier frozen in real time and the two disagree about what is fresh.
        nowNanos = { clock() * NANOS_PER_MILLI },
    )

    /**
     * The store generation this feed's memory tier belongs to.
     *
     * A wipe empties the table but cannot reach [memory] — the wipe is owned by a global, and a
     * feed may well live in a screen scope the global has never heard of. So the feed asks the store
     * instead of waiting to be told: a generation that has moved means everything cached here
     * describes a session, or a domain, that is over.
     */
    private val seenGeneration = AtomicLong(store.generation)

    /**
     * How many times each key, and the namespace as a whole, has been superseded.
     *
     * The store's generation only moves on a full wipe, so it says nothing about a single key being
     * invalidated. Without a per-key counter, an [invalidate] that lands while a load is out has no
     * effect on that load: the loader finishes with a payload from before the invalidation and writes
     * it back stamped with the current clock, and the next read takes it for fresh. The user toggling
     * a bookmark against a title whose details are being revalidated in the background is exactly
     * that sequence.
     */
    private val keyEpochs = ConcurrentHashMap<String, Long>()
    private val namespaceEpoch = AtomicLong(0L)

    /**
     * Everything that can make a load's result obsolete while it is out on the network.
     *
     * The store's generation belongs here as much as this feed's own counters do. A wipe is the
     * strongest invalidation there is — it ends a session — and the store cannot catch a write that
     * crosses it: [PersistentPayloadStore.write] reads the generation on entry, which is already the
     * post-wipe value, so its compensating delete never fires. Logout is the case that matters, since
     * the wipe runs while the screen's loads are still alive.
     */
    private data class Epoch(val generation: Long, val key: Long, val namespace: Long)

    fun load(
        key: String,
        force: Boolean = false,
        ttl: Duration = defaultTtl,
        loader: suspend () -> V,
    ): Flow<Cached<V>> = flow {
        dropMemoryTierIfStoreWasWiped()

        val cached = readCached(key = key, force = force, ttl = ttl)
        if (cached != null) {
            emit(cached)
            if (!cached.isStale) return@flow
        }
        try {
            val fresh = loadFresh(key = key, force = force, ttl = ttl) {
                // Captured where the fetch begins, so it answers "was this key invalidated while I
                // was away?". A retry the in-flight cache issues after an invalidation runs this
                // lambda again and captures the new epoch, so legitimate reloads still write.
                val epoch = epochOf(key)
                // Stamped once, here, and carried by everyone who joins this flight — including a
                // reader that arrives after the memory tier has held the value for a while.
                Stamped(loader(), clock()).also { (value, updatedAt) ->
                    val settled = epochOf(key)
                    if (settled == epoch) {
                        store.write(
                            key = key,
                            payload = json.encodeToString(serializer, value),
                            updatedAt = updatedAt,
                        )
                        // A store mutation can land while the database write is suspended, after
                        // the guard above passed. The old result must not survive in either tier.
                        if (epochOf(key) != epoch) {
                            store.remove(key)
                            memory.remove(key)
                        }
                    } else if (settled.generation != epoch.generation) {
                        detachAfterWipe(key)
                    }
                }
            }
            emit(Cached.Value(fresh.value, isStale = false, updatedAt = fresh.updatedAt))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (cached == null) throw error
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
    suspend fun markStale(key: String, ttl: Duration = defaultTtl) {
        supersedeKey(key)
        memory.remove(key)
        try {
            store.touch(key = key, updatedAt = clock() - ttl.inWholeMilliseconds - 1)
        } finally {
            // A reader can start after the first barrier but before the suspending store mutation
            // settles. The second barrier rejects that reader's snapshot as well.
            supersedeKey(key)
            memory.remove(key)
        }
    }

    /** Drops the entry, so the next [load] has nothing to emit before the network answers. */
    suspend fun invalidate(key: String) {
        supersedeKey(key)
        memory.remove(key)
        try {
            store.remove(key)
        } finally {
            supersedeKey(key)
            memory.remove(key)
        }
    }

    /** Reads a usable value without starting a loader. */
    suspend fun peek(key: String): Cached.Value<V>? {
        dropMemoryTierIfStoreWasWiped()
        return readCached(key = key, force = false, ttl = HardCeiling)
    }

    /** Reads several independent entries with one persistent-store lookup on a cold start. */
    suspend fun peekAll(keys: List<String>): Map<String, Cached.Value<V>> {
        dropMemoryTierIfStoreWasWiped()
        if (keys.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, Cached.Value<V>>()
        val missing = keys.distinct().filter { key ->
            val current = memory.get(key)
            if (current != null) {
                result[key] = Cached.Value(current.value, isStale = false, updatedAt = current.updatedAt)
            }
            current == null
        }
        val epochs = missing.associateWith(::epochOf)
        store.readAll(missing).forEach { (key, stored) ->
            val epoch = epochs.getValue(key)
            if (epochOf(key) != epoch) return@forEach
            val usable = decodeUsable(key, stored) ?: return@forEach
            val selected = promoteToMemory(key, usable, epoch, HardCeiling) ?: return@forEach
            if (epochOf(key) == epoch) {
                result[key] = Cached.Value(selected.value, isStale = false, updatedAt = selected.updatedAt)
            } else {
                memory.remove(key)
            }
        }
        return result
    }

    /** Stores a value produced as part of another cached request. */
    suspend fun put(key: String, value: V, updatedAt: Long = clock()) {
        putAll(mapOf(key to value), updatedAt)
    }

    /** Stores several values in one persistent transaction while preserving invalidation barriers. */
    suspend fun putAll(values: Map<String, V>, updatedAt: Long = clock()) {
        if (values.isEmpty()) return
        dropMemoryTierIfStoreWasWiped()
        values.keys.forEach { key ->
            supersedeKey(key)
            memory.remove(key)
        }
        val epochs = values.keys.associateWith(::epochOf)
        store.writeAll(
            values.mapValues { (_, value) ->
                StoredPayload(
                    payload = json.encodeToString(serializer, value),
                    updatedAt = updatedAt,
                )
            }
        )
        values.forEach { (key, value) ->
            val epoch = epochs.getValue(key)
            if (epochOf(key) == epoch) {
                memory.put(key, Stamped(value, updatedAt), ttl = HardCeiling)
                if (epochOf(key) != epoch) memory.remove(key)
            } else {
                // An invalidate or session wipe crossed the database write. This payload belongs
                // to the superseded snapshot and must not be handed to the next reader.
                store.remove(key)
                memory.remove(key)
            }
        }
    }

    /** Drops every entry in this feed's namespace. */
    suspend fun invalidateNamespace() {
        supersedeNamespace()
        memory.clear()
        try {
            store.removeByPrefix(keyPrefix)
        } finally {
            supersedeNamespace()
            memory.clear()
        }
    }

    private fun epochOf(key: String): Epoch = Epoch(
        generation = store.generation,
        key = keyEpochs[key] ?: 0L,
        namespace = namespaceEpoch.get(),
    )

    private fun supersedeKey(key: String) {
        keyEpochs.merge(key, 1L, Long::plus)
    }

    private fun supersedeNamespace() {
        // The namespace counter, rather than a bump of every key, because the key an in-flight load
        // is working on need not have an entry in the map yet — and a key epoch it never saw could
        // not supersede it.
        namespaceEpoch.incrementAndGet()
        keyEpochs.clear()
    }

    /**
     * Takes the flight back, so the memory coordinator refuses the value this load is carrying and
     * reissues the loader under the new generation.
     *
     * Needed only for a wipe. [invalidate] and [invalidateNamespace] clear the memory tier
     * themselves, synchronously, before this load can settle — but a wipe happens on the store,
     * which has no way to reach in here, and [dropMemoryTierIfStoreWasWiped] fires once per
     * generation, so a load that consumed it leaves a leader still out on the network free to
     * repopulate the tier with the previous session's value and nothing left to clear it again.
     *
     * The removal can also evict a legitimate post-wipe entry that landed under the same key in the
     * meantime. That costs one extra request; serving the previous account's data would cost rather
     * more.
     */
    private fun detachAfterWipe(key: String) {
        memory.remove(key)
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
            memory.clear()
        }
    }

    private fun remainingFreshness(updatedAt: Long, ttl: Duration): Duration {
        val age = (clock() - updatedAt).coerceAtLeast(0L)
        return (ttl.inWholeMilliseconds - age).coerceAtLeast(0L).milliseconds
    }

    private suspend fun loadFresh(
        key: String,
        force: Boolean,
        ttl: Duration,
        loader: suspend () -> Stamped<V>,
    ): Stamped<V> {
        val freshness: (Stamped<V>) -> Duration = { value -> remainingFreshness(value.updatedAt, ttl) }
        return if (force) {
            memory.reload(key, ttl = freshness, defaultValue = loader)
        } else {
            memory.getOrPut(key, ttl = freshness, defaultValue = loader)
        }
    }

    // Each guard is a concurrency boundary: flattening them would make it easier to promote a value
    // after the epoch it was read under had already been superseded.
    @Suppress("ReturnCount")
    private suspend fun readCached(key: String, force: Boolean, ttl: Duration): Cached.Value<V>? {
        memory.get(key)?.let { current ->
            return Cached.Value(current.value, isStale = force, updatedAt = current.updatedAt)
        }

        val epoch = epochOf(key)
        val stored = readUsable(key) ?: return null
        if (epochOf(key) != epoch) return null
        val isStale = force || clock() - stored.updatedAt > ttl.inWholeMilliseconds
        val selected = if (isStale) stored else promoteToMemory(key, stored, epoch, ttl) ?: return null
        if (epochOf(key) != epoch) {
            memory.remove(key)
            return null
        }
        return Cached.Value(selected.value, isStale = isStale, updatedAt = selected.updatedAt)
    }

    private fun promoteToMemory(
        key: String,
        stored: Usable<V>,
        epoch: Epoch,
        ttl: Duration,
    ): Usable<V>? {
        val promoted = Stamped(stored.value, stored.updatedAt)
        if (memory.putIfAbsent(key, promoted, ttl = remainingFreshness(stored.updatedAt, ttl))) {
            return stored.takeIf { epochOf(key) == epoch }.also { accepted ->
                if (accepted == null) memory.remove(key)
            }
        }
        if (epochOf(key) != epoch) return null
        return memory.get(key)?.let { current -> Usable(current.value, current.updatedAt) } ?: stored
    }

    @Suppress("ReturnCount")
    private suspend fun readUsable(key: String): Usable<V>? {
        val stored = store.read(key) ?: return null
        return decodeUsable(key, stored)
    }

    @Suppress("ReturnCount")
    private suspend fun decodeUsable(key: String, stored: StoredPayload): Usable<V>? {
        if (clock() - stored.updatedAt > HardCeiling.inWholeMilliseconds) {
            // Past the ceiling nothing will read this row again, so keeping it only grows the
            // table — a details payload carries the item's seasons, videos and files. Dropped for
            // the same reason an undecodable one is.
            store.remove(key)
            return null
        }
        val value = runCatching { json.decodeFromString(serializer, stored.payload) }.getOrElse {
            // Written by a build whose model differed. Nothing to salvage, and keeping it would make
            // every future read pay the same failed decode.
            store.remove(key)
            return null
        }
        return Usable(value = value, updatedAt = stored.updatedAt)
    }

    private class Usable<V>(val value: V, val updatedAt: Long)

    /** A value together with the moment it was accepted, so the memory tier keeps ages too. */
    private data class Stamped<V : Any>(val value: V, val updatedAt: Long)

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
