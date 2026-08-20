package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.ParserException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException

internal class PlaybackErrorPolicyTest {

    private val policy = PlaybackErrorPolicy()

    @Test
    fun transportFailure_prefersAnotherLocation_whenLocationAndTrackAreAvailable() {
        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 2, tracks = 2),
            loadError(IOException("source did not answer")),
        )

        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION, fallback?.type)
        assertEquals(SOURCE_EXCLUSION_MS, fallback?.exclusionDurationMs)
    }

    @Test
    fun transportFailure_usesAnotherTrack_whenNoLocationIsAvailable() {
        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 1, tracks = 2),
            loadError(IOException("source did not answer")),
        )

        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK, fallback?.type)
        assertEquals(SOURCE_EXCLUSION_MS, fallback?.exclusionDurationMs)
    }

    @Test
    fun transportFailure_prefersTrack_whenContentSteeringControlsLocations() {
        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 2, tracks = 2, locationSteeringActive = true),
            loadError(IOException("source did not answer")),
        )

        assertEquals(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK, fallback?.type)
    }

    @Test
    fun transportFailure_returnsNoFallback_whenEveryAlternativeIsExcluded() {
        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(
                locations = 2,
                excludedLocations = 1,
                tracks = 2,
                excludedTracks = 1,
            ),
            loadError(IOException("source did not answer")),
        )

        assertNull(fallback)
    }

    @Test
    fun signedLinkAuthFailure_keepsRefreshLinksRecoveryPath() {
        val authFailure = HttpDataSource.InvalidResponseCodeException(
            HTTP_FORBIDDEN,
            "Forbidden",
            null,
            emptyMap(),
            mockk<DataSpec>(),
            byteArrayOf(),
        )

        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 2, tracks = 2),
            loadError(authFailure),
        )

        assertNull(fallback)
    }

    @Test
    fun parserFailure_keepsTopLevelStreamFallbackPath() {
        val parserFailure = ParserException.createForMalformedManifest(
            "Malformed manifest",
            IOException("Invalid playlist"),
        )

        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 2, tracks = 2),
            loadError(parserFailure),
        )

        assertNull(fallback)
    }

    @Test
    fun fileNotFoundFailure_keepsTopLevelStreamFallbackPath() {
        val fallback = policy.getFallbackSelectionFor(
            fallbackOptions(locations = 2, tracks = 2),
            loadError(FileNotFoundException("Playlist is missing")),
        )

        assertNull(fallback)
    }

    private fun fallbackOptions(
        locations: Int,
        excludedLocations: Int = 0,
        tracks: Int,
        excludedTracks: Int = 0,
        locationSteeringActive: Boolean = false,
    ) = LoadErrorHandlingPolicy.FallbackOptions(
        locations,
        excludedLocations,
        tracks,
        excludedTracks,
        locationSteeringActive,
    )

    private fun loadError(error: IOException): LoadErrorHandlingPolicy.LoadErrorInfo {
        return LoadErrorHandlingPolicy.LoadErrorInfo(
            mockk(),
            mockk(),
            error,
            1,
        )
    }

    private companion object {
        const val SOURCE_EXCLUSION_MS = 60_000L
        const val HTTP_FORBIDDEN = 403
    }
}
