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
    maximumSize: Int = DEFAULT_MAXIMUM_SIZE,
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
        maximumSize = maximumSize,
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

    /**
     * How many times each key was *destroyed* — [invalidate], [markStale], a namespace drop or a
     * store wipe — as opposed to merely written over by a competing writer.
     *
     * [keyEpochs] alone cannot tell the two apart, and the difference decides whether a writer whose
     * barrier moved underneath it may delete the row. Only a destruction may: two [putAll] calls
     * that overlap on one key are both writing legitimate values, so a writer that reads a moved
     * barrier and deletes would erase the other's row and leave every list referencing it dangling.
     */
    private val keyInvalidations = ConcurrentHashMap<String, Long>()

    /**
     * Which write last claimed each key.
     *
     * A writer may take its own row back out when a destruction crossed its database write, but only
     * its own: a writer that landed after that destruction answered a later question, and its row is
     * the one every list written alongside it names. [keyInvalidations] cannot separate the two — it
     * says a destruction happened, never whether a write landed after it — so without this ticket a
     * writer that resumes late deletes whatever row happens to be there.
     *
     * Never cleared, unlike [keyEpochs]: a ticket has to stay comparable across a namespace drop, or
     * a writer that crossed one could no longer tell whether it still owns the row.
     */
    private val keyWrites = ConcurrentHashMap<String, Long>()

    /**
     * The write ticket held by the last write that also destroyed what it replaced.
     *
     * Ownership alone cannot settle these. A destroying write takes a ticket like any other, so a
     * writer that crossed it reads "someone else owns the row" and leaves it — even when its own
     * row is the one that landed last, which puts the state the destroying write discarded straight
     * back. Comparing against this instead answers the question that actually matters: has anything
     * legitimate been written since the destruction, or is the row still the one it left behind?
     */
    private val keySupersedeWrites = ConcurrentHashMap<String, Long>()
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
    private data class Epoch(
        val generation: Long,
        val key: Long,
        val namespace: Long,
        val invalidation: Long,
        val write: Long,
        val supersedeWrite: Long,
    ) {
        /**
         * True when a writer holding [previous] must take its row back out.
         *
         * The entry was destroyed after [previous] was captured, and either no write has claimed the
         * key since — so the row is still this writer's own — or the last write to claim it was the
         * destroying one, so nothing legitimate stands between the destruction and now. Any other
         * writer's row is not this writer's to delete: it answers a later question, every list
         * naming the key points at it, and that writer runs this same test when it settles.
         */
        fun supersedesWrite(previous: Epoch): Boolean = destroyedSince(previous) &&
            (write == previous.write || write == supersedeWrite)

        private fun destroyedSince(previous: Epoch): Boolean = generation != previous.generation ||
            namespace != previous.namespace ||
            invalidation != previous.invalidation
    }

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
        // Set by the loader when what it produced must not be left behind as this key's value. It
        // cannot act on that itself: the memory tier records a flight's value once loadFresh
        // returns, so a removal performed inside the loader is put straight back — which is how an
        // invalidation that crossed a details write ended up undone by the very write it crossed.
        var discardValue = false
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
                        // the guard above passed. The old result must not survive in either tier —
                        // though the row goes only when this writer still owns it, since a newer
                        // writer's row is a real value and every list naming this key points at it.
                        val settledAfterWrite = epochOf(key)
                        if (settledAfterWrite.supersedesWrite(epoch)) store.remove(key)
                        discardValue = settledAfterWrite != epoch
                    } else if (settled.generation != epoch.generation) {
                        discardValue = true
                    }
                }
            }
            if (discardValue) detachSupersededValue(key)
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

    /**
     * Reads a usable value without starting a loader and without promoting it.
     *
     * Promotion is deliberately skipped. The memory tier doubles as the single-flight coordinator,
     * so an entry a look-ahead read placed there — pinned to the hard ceiling, since a peek has no
     * TTL of its own — would go on answering the very load that read was preparing for.
     */
    @Suppress("ReturnCount")
    suspend fun peek(key: String): Cached.Value<V>? {
        dropMemoryTierIfStoreWasWiped()
        memory.get(key)?.let { current ->
            return Cached.Value(current.value, isStale = false, updatedAt = current.updatedAt)
        }
        val epoch = epochOf(key)
        val stored = readUsable(key) ?: return null
        if (epochOf(key) != epoch) return null
        return Cached.Value(stored.value, isStale = false, updatedAt = stored.updatedAt)
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
    suspend fun put(key: String, value: V, updatedAt: Long = clock(), supersede: Boolean = false) {
        putAll(mapOf(key to value), updatedAt, supersede)
    }

    /**
     * Stores several values in one persistent transaction while preserving invalidation barriers.
     *
     * @param supersede when this write also destroys what was there — a record rewritten to mean
     *   "reload me" — rather than merely carrying newer data. An ordinary write lets a load already
     *   in flight keep its own row, because that row answers a later question; a superseding one
     *   must not, or the very state the caller was cancelling survives the write that replaced it.
     */
    suspend fun putAll(values: Map<String, V>, updatedAt: Long = clock(), supersede: Boolean = false) {
        if (values.isEmpty()) return
        dropMemoryTierIfStoreWasWiped()
        values.keys.forEach { key ->
            if (supersede) keyInvalidations.merge(key, 1L, Long::plus)
            val ticket = advanceKey(key)
            if (supersede) keySupersedeWrites[key] = ticket
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
            val settled = epochOf(key)
            when {
                settled == epoch -> {
                    memory.put(key, Stamped(value, updatedAt), ttl = HardCeiling)
                    if (epochOf(key) != epoch) memory.remove(key)
                }
                settled.supersedesWrite(epoch) -> {
                    // An invalidate or session wipe crossed the database write, and this row is
                    // still the one it left behind. The payload belongs to the superseded snapshot
                    // and must not be handed to the next reader.
                    store.remove(key)
                    memory.remove(key)
                }
                // Someone else has written this key since — item payloads are shared, so two
                // sections loading concurrently collide on every title they have in common, and a
                // reload after an invalidate lands here too. Their row is the current one; dropping
                // only the memory claim lets the next reader take whatever settled in the store,
                // whereas a delete would leave every list naming this key pointing at nothing.
                else -> memory.remove(key)
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
        invalidation = keyInvalidations[key] ?: 0L,
        write = keyWrites[key] ?: 0L,
        supersedeWrite = keySupersedeWrites[key] ?: 0L,
    )

    /** Barrier for a destructive change: in-flight values become unwritable, and their rows go. */
    private fun supersedeKey(key: String) {
        keyInvalidations.merge(key, 1L, Long::plus)
        keyEpochs.merge(key, 1L, Long::plus)
    }

    /**
     * Barrier for a competing write: in-flight values must not be written, but nothing is lost.
     *
     * Takes the key's next write ticket, which is what later makes this writer's row distinguishable
     * from one a newer writer put there.
     */
    private fun advanceKey(key: String): Long {
        keyEpochs.merge(key, 1L, Long::plus)
        return keyWrites.merge(key, 1L, Long::plus) ?: 1L
    }

    private fun supersedeNamespace() {
        // The namespace counter, rather than a bump of every key, because the key an in-flight load
        // is working on need not have an entry in the map yet — and a key epoch it never saw could
        // not supersede it.
        namespaceEpoch.incrementAndGet()
        keyEpochs.clear()
        keyInvalidations.clear()
    }

    /**
     * Takes the flight's value back out, so the memory coordinator refuses what this load carried
     * and the next reader reissues the loader.
     *
     * Called after [loadFresh] returns rather than from inside the loader, because the tier records
     * the value on the way out and would put back anything removed before that.
     *
     * A wipe is what makes this unavoidable. [invalidate] and [invalidateNamespace] clear the memory
     * tier themselves, synchronously, before this load can settle — but a wipe happens on the store,
     * which has no way to reach in here, and [dropMemoryTierIfStoreWasWiped] fires once per
     * generation, so a load that consumed it leaves a leader still out on the network free to
     * repopulate the tier with the previous session's value and nothing left to clear it again.
     *
     * The removal can also evict a legitimate entry that landed under the same key in the meantime.
     * That costs one extra request; serving the previous account's data, or details a bookmark
     * toggle has just invalidated, would cost rather more.
     */
    private fun detachSupersededValue(key: String) {
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
        // An entry the tier still considers valid can be stale for *this* caller: put/putAll and
        // peekAll pin to the hard ceiling because they have no reader's TTL to work from. Left to
        // getOrPut, such an entry would answer the revalidation this call exists to perform. Asked
        // of the tier rather than tracked from the read above so that a second reader arriving after
        // the first has already displaced it joins that flight instead of displacing it again.
        val memoryIsStale = memory.get(key)?.let { current ->
            clock() - current.updatedAt > ttl.inWholeMilliseconds
        } == true
        return if (force || memoryIsStale) {
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
            // Asked of the stamp rather than of the tier: entries written by put/putAll or promoted
            // by peekAll are pinned to the hard ceiling regardless of the TTL a reader governs this
            // key by, so the tier's own expiry would report a week-old payload as fresh.
            val isStale = force || clock() - current.updatedAt > ttl.inWholeMilliseconds
            return Cached.Value(current.value, isStale = isStale, updatedAt = current.updatedAt)
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

        /**
         * Sized for a normalized cache: one entry per card payload plus one per list, and a single
         * home load alone writes seven lists' worth of cards. Too small a tier evicts the list
         * entries that coordinate single-flight loads long before their TTL runs out.
         */
        const val DEFAULT_MAXIMUM_SIZE: Int = 1024

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
