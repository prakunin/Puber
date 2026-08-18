package com.kino.puber.core.contentlink

import java.net.URI

internal class ContentUriCodec {

    fun parse(value: String?): ContentTarget? {
        if (value.isNullOrBlank()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            INTERNAL_SCHEME -> parseInternal(uri)
            HTTPS_SCHEME -> parseKinoPub(uri)
            else -> null
        }
    }

    fun internalUri(target: ContentTarget): String {
        val path = when (target) {
            is ContentTarget.Details -> "/items/${target.itemId}"
            is ContentTarget.EpisodeDetails -> episodePath(
                target.itemId,
                target.seasonNumber,
                target.episodeNumber,
            )
            is ContentTarget.Playback -> target.seasonNumber?.let { season ->
                episodePath(target.itemId, season, checkNotNull(target.episodeNumber))
            } ?: "/items/${target.itemId}"
        }
        val query = if (target is ContentTarget.Playback) {
            buildList {
                add("action=play")
                target.videoNumber?.let { add("video=$it") }
            }.joinToString(separator = "&", prefix = "?")
        } else {
            ""
        }
        return "$INTERNAL_SCHEME://$INTERNAL_HOST$path$query"
    }

    fun publicUrl(target: ContentTarget): String {
        val path = when (target) {
            is ContentTarget.Details -> "/item/view/${target.itemId}"
            is ContentTarget.EpisodeDetails ->
                "/item/view/${target.itemId}/s${target.seasonNumber}e${target.episodeNumber}"
            is ContentTarget.Playback -> target.seasonNumber?.let { season ->
                "/item/view/${target.itemId}/s${season}e${checkNotNull(target.episodeNumber)}"
            } ?: "/item/view/${target.itemId}"
        }
        return "$HTTPS_SCHEME://$PUBLIC_HOST$path"
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun parseInternal(uri: URI): ContentTarget? {
        if (uri.host?.lowercase() != INTERNAL_HOST || !uri.hasSafeAuthorityAndFragment()) return null
        val segments = uri.pathSegmentsOrNull() ?: return null
        if (segments.size != ITEM_PATH_SIZE && segments.size != EPISODE_PATH_SIZE) return null
        if (segments[0] != "items") return null

        val itemId = segments[1].positiveIntOrNull() ?: return null
        val episodeCoordinates = if (segments.size == EPISODE_PATH_SIZE) {
            if (segments[2] != "seasons" || segments[4] != "episodes") return null
            val season = segments[3].positiveIntOrNull() ?: return null
            val episode = segments[5].positiveIntOrNull() ?: return null
            season to episode
        } else {
            null
        }

        val query = uri.strictQueryOrNull() ?: return null
        val action = query[QUERY_ACTION]
        val videoNumber = query[QUERY_VIDEO]?.positiveIntOrNull()
        if (QUERY_VIDEO in query && videoNumber == null) return null

        return when (action) {
            null -> if (videoNumber == null) {
                episodeCoordinates?.let { (season, episode) ->
                    ContentTarget.EpisodeDetails(itemId, season, episode)
                } ?: ContentTarget.Details(itemId)
            } else {
                null
            }
            ACTION_PLAY -> ContentTarget.Playback(
                itemId = itemId,
                seasonNumber = episodeCoordinates?.first,
                episodeNumber = episodeCoordinates?.second,
                videoNumber = videoNumber,
            )
            else -> null
        }
    }

    @Suppress("ReturnCount")
    private fun parseKinoPub(uri: URI): ContentTarget? {
        if (uri.host?.lowercase() != PUBLIC_HOST || !uri.hasSafeAuthorityAndFragment()) return null
        if (uri.queryContainsSensitiveKey()) return null
        val segments = uri.pathSegmentsOrNull() ?: return null
        if (segments.size !in WEB_MIN_PATH_SIZE..WEB_MAX_PATH_SIZE) return null
        if (segments[0] != "item" || segments[1] != "view") return null

        val itemId = segments[2].positiveIntOrNull() ?: return null
        if (segments.size == WEB_MIN_PATH_SIZE) return ContentTarget.Details(itemId)

        val episodeMatch = EPISODE_SEGMENT.matchEntire(segments[3])
        if (episodeMatch == null) return ContentTarget.Details(itemId)
        val season = episodeMatch.groupValues[1].positiveIntOrNull() ?: return null
        val episode = episodeMatch.groupValues[2].positiveIntOrNull() ?: return null
        return ContentTarget.EpisodeDetails(itemId, season, episode)
    }

    private fun URI.hasSafeAuthorityAndFragment(): Boolean =
        userInfo == null && port == NO_PORT && fragment == null

    private fun URI.pathSegmentsOrNull(): List<String>? {
        val path = rawPath ?: return null
        if (path.contains('%') || path.endsWith('/')) return null
        return path.split('/').filter(String::isNotEmpty)
    }

    private fun URI.strictQueryOrNull(): Map<String, String>? {
        val raw = rawQuery.orEmpty()
        if (raw.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        var valid = true
        for (parameter in raw.split('&')) {
            val pieces = parameter.split('=', limit = 2)
            valid = pieces.size == 2 &&
                pieces[0] in INTERNAL_QUERY_KEYS &&
                '%' !in parameter &&
                result.put(pieces[0], pieces[1]) == null
            if (!valid) break
        }
        return result.takeIf { valid }
    }

    private fun URI.queryContainsSensitiveKey(): Boolean {
        val raw = rawQuery ?: return false
        return raw.split('&').any { parameter ->
            parameter.substringBefore('=').lowercase() in SENSITIVE_WEB_QUERY_KEYS
        }
    }

    private fun String.positiveIntOrNull(): Int? =
        takeIf { isNotEmpty() && all(Char::isDigit) }
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    private fun episodePath(itemId: Int, season: Int, episode: Int): String =
        "/items/$itemId/seasons/$season/episodes/$episode"

    private companion object {
        const val INTERNAL_SCHEME = "puber"
        const val INTERNAL_HOST = "content"
        const val HTTPS_SCHEME = "https"
        const val PUBLIC_HOST = "kino.pub"
        const val QUERY_ACTION = "action"
        const val QUERY_VIDEO = "video"
        const val ACTION_PLAY = "play"
        const val NO_PORT = -1
        const val ITEM_PATH_SIZE = 2
        const val EPISODE_PATH_SIZE = 6
        const val WEB_MIN_PATH_SIZE = 3
        const val WEB_MAX_PATH_SIZE = 4

        val INTERNAL_QUERY_KEYS = setOf(QUERY_ACTION, QUERY_VIDEO)
        val EPISODE_SEGMENT = Regex("s([0-9]+)e([0-9]+)")
        val SENSITIVE_WEB_QUERY_KEYS = setOf(
            "action",
            "play",
            "token",
            "access_token",
            "refresh_token",
            "url",
            "stream",
            "cdn",
            "media",
        )
    }
}
