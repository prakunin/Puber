package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException
import java.io.IOException

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val SOURCE_FALLBACK_EXCLUSION_MS = 60_000L

/**
 * Load error handling for every stream the player opens, HLS and progressive alike:
 * - Retries with the backoff curve from [PlaybackNetworkTuning]
 * - No retry for parse/file-not-found errors
 * - Exclude an unreachable location or track for 60s so HLS can move to another CDN source
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
        if (error is ParserException || error is FileNotFoundException) {
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
        if (exception is ParserException ||
            exception is FileNotFoundException ||
            exception.isSignedLinkAuthFailure()
        ) {
            return null
        }

        val preferredFallbacks = if (fallbackOptions.locationSteeringActive) {
            // Content Steering owns location choice; changing representation first lets it keep
            // steering mirrors without the player fighting its decision.
            intArrayOf(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION,
            )
        } else {
            // A location is the same bytes through another URL, so prefer it over losing quality.
            intArrayOf(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION,
                LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
            )
        }

        val fallbackType = preferredFallbacks.firstOrNull(fallbackOptions::isFallbackAvailable)
            ?: return null
        return LoadErrorHandlingPolicy.FallbackSelection(
            fallbackType,
            SOURCE_FALLBACK_EXCLUSION_MS,
        )
    }

    private fun IOException.isSignedLinkAuthFailure(): Boolean {
        return this is HttpDataSource.InvalidResponseCodeException &&
                responseCode in setOf(HTTP_UNAUTHORIZED, HTTP_FORBIDDEN)
    }
}
