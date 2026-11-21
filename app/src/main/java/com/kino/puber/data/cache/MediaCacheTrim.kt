package com.kino.puber.data.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan

private const val MIB = 1024L * 1024L

/** Size cap of the media cache: the evictor of the cache and the startup trim both use it. */
const val MEDIA_CACHE_SIZE_BYTES = 256L * MIB

/**
 * Picks the least recently used spans to remove so the rest fits in [capBytes]. Pure: it removes
 * nothing, so the choice can be tested without a cache on disk.
 */
@OptIn(UnstableApi::class)
internal fun spansToEvict(spans: Collection<CacheSpan>, capBytes: Long): List<CacheSpan> {
    var remaining = spans.sumOf { it.length }
    if (remaining <= capBytes) return emptyList()
    val evicted = ArrayList<CacheSpan>()
    for (span in spans.sortedBy { it.lastTouchTimestamp }) {
        if (remaining <= capBytes) break
        evicted += span
        remaining -= span.length
    }
    return evicted
}

/**
 * Shrinks an existing media cache to [capBytes]. The cache's own evictor only runs when a write
 * needs room, and the main player only reads, so a cache left over from a larger cap would
 * otherwise keep its old size. Blocking: call it off the main thread.
 */
@OptIn(UnstableApi::class)
fun trimMediaCache(cache: Cache, capBytes: Long) {
    val spans = cache.keys.flatMap { key -> cache.getCachedSpans(key) }
    spansToEvict(spans, capBytes).forEach { span -> cache.removeSpan(span) }
}

/** The trim the sweeper runs: resolves the cache lazily so it is not opened before the first sweep. */
@OptIn(UnstableApi::class)
fun mediaCacheTrimAction(cache: Lazy<Cache>, capBytes: Long = MEDIA_CACHE_SIZE_BYTES): () -> Unit =
    { trimMediaCache(cache.value, capBytes) }
