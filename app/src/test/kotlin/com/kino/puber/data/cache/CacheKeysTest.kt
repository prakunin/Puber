package com.kino.puber.data.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CacheKeysTest {

    @Test
    fun itemKeysCarryTheItemNamespace() {
        assertEquals("item:42", CacheKeys.item(42))
        assertTrue(CacheKeys.item(42).startsWith(CacheKeys.ItemPrefix))
    }

    @Test
    fun similarKeysAreDistinctFromItemKeys() {
        assertEquals("similar:42", CacheKeys.similar(42))
    }

    @Test
    fun homeKeysCarryTheHomeNamespace() {
        assertEquals("home:hot", CacheKeys.home("hot"))
        assertTrue(CacheKeys.home("hot").startsWith(CacheKeys.HomePrefix))
    }

    @Test
    fun continueWatchingIsRefreshedFarSoonerThanTheRestOfHome() {
        // It is the one row a finished episode makes wrong immediately.
        assertEquals(2.minutes, CacheTtl.ContinueWatching)
        assertTrue(CacheTtl.ContinueWatching < CacheTtl.HomeSection)
    }

    @Test
    fun sectionKeysCarryTheSectionNamespace() {
        assertEquals("section:popular", CacheKeys.section("popular"))
        assertTrue(CacheKeys.section("popular").startsWith(CacheKeys.SectionPrefix))
    }

    @Test
    fun watchlistKeyCarriesTheWatchlistNamespace() {
        assertEquals("watchlist:subscribed", CacheKeys.watchlist())
        assertTrue(CacheKeys.watchlist().startsWith(CacheKeys.WatchlistPrefix))
    }

    @Test
    fun historyPageKeysCarryTheHistoryNamespace() {
        assertEquals("history:1", CacheKeys.historyPage(1))
        assertTrue(CacheKeys.historyPage(1).startsWith(CacheKeys.HistoryPrefix))
    }

    @Test
    fun watchlistAndHistoryRevalidateFarSoonerThanCatalogueSections() {
        // Both are rewritten by the user's own playback, unlike the catalogue rows.
        assertEquals(2.minutes, CacheTtl.Watchlist)
        assertEquals(2.minutes, CacheTtl.HistoryPage)
        assertTrue(CacheTtl.Watchlist < CacheTtl.CatalogueSection)
        assertTrue(CacheTtl.HistoryPage < CacheTtl.CatalogueSection)
    }
}
