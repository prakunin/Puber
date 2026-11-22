package com.kino.puber.ui.feature.player.vm

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink

/** The main player only reads the media cache; the warm-up player is the one that writes it. */
@OptIn(UnstableApi::class)
internal fun cacheWriteSink(cache: Cache, writeToCache: Boolean): DataSink.Factory? =
    if (writeToCache) CacheDataSink.Factory().setCache(cache) else null
