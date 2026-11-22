package com.kino.puber.data.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val MIB = 1024L * 1024L

class MediaCacheTrimTest {

    private fun span(key: String, length: Long, touched: Long) =
        CacheSpan(key, 0L, length, touched, null)

    @Test
    fun nothingIsEvictedWhenTheCacheFitsTheCap() {
        val spans = listOf(span("a", 100, 1), span("b", 100, 2))

        assertTrue(spansToEvict(spans, capBytes = 200).isEmpty())
    }

    @Test
    fun theLeastRecentlyTouchedSpansGoFirstUntilTheRestFits() {
        val spans = listOf(span("new", 100, 30), span("old", 100, 10), span("mid", 100, 20))

        val evicted = spansToEvict(spans, capBytes = 150)

        assertEquals(listOf("old", "mid"), evicted.map { it.key })
    }

    @Test
    fun evictionStopsAsSoonAsTheRemainderFits() {
        val spans = listOf(span("old", 100, 10), span("new", 100, 20))

        assertEquals(listOf("old"), spansToEvict(spans, capBytes = 100).map { it.key })
    }

    private fun cacheOf(vararg spans: CacheSpan): Cache {
        val cache = mockk<Cache>(relaxed = true)
        every { cache.keys } returns spans.map { it.key }.toSet()
        every { cache.getCachedSpans(any()) } answers {
            java.util.TreeSet(spans.filter { it.key == firstArg<String>() })
        }
        return cache
    }

    @Test
    fun trimRemovesExactlyTheLeastRecentSpansAboveTheCap() {
        val old = span("old", 100, 10)
        val mid = span("mid", 100, 20)
        val new = span("new", 100, 30)
        val cache = cacheOf(new, old, mid)

        trimMediaCache(cache, capBytes = 150)

        verify(exactly = 1) { cache.removeSpan(old) }
        verify(exactly = 1) { cache.removeSpan(mid) }
        verify(exactly = 0) { cache.removeSpan(new) }
    }

    @Test
    fun trimRemovesNothingWhenTheCacheFits() {
        val cache = cacheOf(span("a", 100, 1), span("b", 100, 2))

        trimMediaCache(cache, capBytes = 200)

        verify(exactly = 0) { cache.removeSpan(any()) }
    }

    @Test
    fun theWiredTrimUsesTheMediaCacheCapAndOpensTheCacheOnlyWhenRun() {
        val old = span("old", 200 * MIB, 10)
        val new = span("new", 100 * MIB, 20)
        val cache = cacheOf(old, new)
        var opened = false
        val trim = mediaCacheTrimAction(lazy { opened = true; cache })

        assertTrue(!opened)
        trim()

        assertEquals(256L * MIB, MEDIA_CACHE_SIZE_BYTES)
        verify(exactly = 1) { cache.removeSpan(old) }
        verify(exactly = 0) { cache.removeSpan(new) }
    }
}
