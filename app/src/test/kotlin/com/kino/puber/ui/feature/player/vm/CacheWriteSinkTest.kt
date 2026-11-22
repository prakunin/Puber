package com.kino.puber.ui.feature.player.vm

import androidx.media3.datasource.cache.Cache
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CacheWriteSinkTest {

    private val cache = mockk<Cache>(relaxed = true)

    @Test
    fun theMainPlayerGetsNoWriteSinkSoItOnlyReadsTheCache() {
        assertNull(cacheWriteSink(cache, writeToCache = false))
    }

    @Test
    fun theWarmUpPlayerGetsAWriteSink() {
        assertNotNull(cacheWriteSink(cache, writeToCache = true))
    }
}
