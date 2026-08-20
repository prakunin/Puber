package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_BAD_GATEWAY = 502
private const val TRACK_FALLBACK_EXCLUSION_MS = 60_000L

/**
 * Load error handling for every stream the player opens, HLS and progressive alike:
 * - Retries with the backoff curve from [PlaybackNetworkTuning]
 * - No retry for parse/file-not-found errors
 * - Blacklist failing video variants on HTTP 400/502 for 60s
 *
 * Progressive streams are the transport the player falls back to when HLS fails, so leaving them on
 * media3's defaults meant the fallback retried on the slow curve this class exists to replace.
 * Nothing here is HLS-specific: the variant blacklisting simply finds no alternative track to pick
 * on a single-rendition source and returns null.
 */
@UnstableApi
internal class PlaybackErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is androidx.media3.common.ParserException || error is FileNotFoundException) {
            return C.TIME_UNSET
        }
        return PlaybackNetworkTuning.retryBackoffMs(loadErrorInfo.errorCount)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        PlaybackNetworkTuning.MINIMUM_RETRY_COUNT

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        val exception = loadErrorInfo.exception
        if (exception is HttpDataSource.InvalidResponseCodeException &&
            exception.responseCode in setOf(HTTP_BAD_REQUEST, HTTP_BAD_GATEWAY) &&
            fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)
        ) {
            return LoadErrorHandlingPolicy.FallbackSelection(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                TRACK_FALLBACK_EXCLUSION_MS,
            )
        }
        return null
    }
}
