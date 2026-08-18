package com.kino.puber.core.tvhome

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.WatchCompletionPolicy
import com.kino.puber.core.contentlink.ContentTarget
import java.net.URI

internal class ContinueWatchingSource(
    private val api: KinoPubApiClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(): Result<List<PublishedProgram>> = api.getHistoryData(FIRST_PAGE).map { page ->
        val now = clock()
        page.items
            .asSequence()
            .mapIndexedNotNull { index, history -> history.toPublishedProgram(now - index) }
            .distinctBy { program -> program.target.itemId }
            .take(MAX_PROGRAMS)
            .toList()
    }

    @Suppress("ReturnCount")
    private fun History.toPublishedProgram(lastEngagementTimeMs: Long): PublishedProgram? {
        if (item.id <= 0 || item.type == ItemType.UNKNOWN_VALUE || item.title.isBlank()) return null
        val media = video ?: return null
        val positionSeconds = time?.takeIf { it > 0 } ?: return null
        val durationSeconds = media.duration?.takeIf { it > 0 } ?: return null
        if (WatchCompletionPolicy.isFinished(positionSeconds, durationSeconds)) return null
        val artwork = listOfNotNull(item.posters?.wide, item.posters?.big, item.posters?.medium)
            .firstOrNull(::isSafeArtworkUri)
            ?: return null

        val target = if (item.type.isSeriesLike()) {
            val seasonNumber = season?.takeIf { it > 0 } ?: return null
            val episodeNumber = media.number?.takeIf { it > 0 } ?: return null
            ContentTarget.Playback(item.id, seasonNumber, episodeNumber)
        } else {
            ContentTarget.Playback(item.id, videoNumber = media.number?.takeIf { it > 0 })
        }
        return PublishedProgram(
            stableKey = target.stableKey(),
            title = item.title,
            artworkUri = artwork,
            positionMs = positionSeconds * MILLIS_PER_SECOND,
            durationMs = durationSeconds * MILLIS_PER_SECOND,
            lastEngagementTimeMs = lastEngagementTimeMs,
            target = target,
        )
    }

    private fun ContentTarget.Playback.stableKey(): String = buildString {
        append(itemId)
        seasonNumber?.let { append(":s").append(it) }
        episodeNumber?.let { append(":e").append(it) }
        videoNumber?.let { append(":v").append(it) }
    }

    private fun isSafeArtworkUri(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && uri.host != null && uri.userInfo == null
    }.getOrDefault(false)

    private companion object {
        const val FIRST_PAGE = 1
        const val MAX_PROGRAMS = 10
        const val MILLIS_PER_SECOND = 1_000L
    }
}
