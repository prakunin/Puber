package com.kino.puber.ui.feature.player.vm

import java.net.URI

private const val SUBTITLES_PATH_MARKER = "/subtitles/"

internal fun String.stableSubtitleKey(): String {
    if (isEmpty()) return ""
    val path = normalizedSubtitlePath()
    return path.substringAfter(SUBTITLES_PATH_MARKER, path)
}

internal fun sameSubtitleIdentity(first: String, second: String): Boolean {
    if (first == second) return true
    val firstPath = first.subtitleIdentityPathOrNull()
    val secondPath = second.subtitleIdentityPathOrNull()
    return firstPath != null && secondPath != null && (
        firstPath == secondPath ||
            firstPath.endsWith("/$secondPath") ||
            secondPath.endsWith("/$firstPath") ||
            firstPath.stableSubtitleKey() == secondPath.stableSubtitleKey()
        )
}

private fun String.subtitleIdentityPathOrNull(): String? {
    if (isEmpty()) return null
    val path = normalizedSubtitlePath().trim('/')
    return path.takeIf { candidate ->
        candidate.contains('/') || SUBTITLE_FILE_EXTENSION.containsMatchIn(candidate)
    }
}

/** KinoPub exposes an HLS rendition for a subtitle file as `<file>/index.m3u8`. */
private fun String.normalizedSubtitlePath(): String {
    val path = runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#')
    return HLS_SUBTITLE_PLAYLIST_SUFFIX.replace(path) { match -> match.groupValues[1] }
}

internal val SUBTITLE_FILE_EXTENSION = Regex(
    pattern = """\.(srt|vtt|webvtt|ass|ssa|ttml|xml)$""",
    option = RegexOption.IGNORE_CASE,
)

private val HLS_SUBTITLE_PLAYLIST_SUFFIX = Regex(
    pattern = """(\.(?:srt|vtt|webvtt|ass|ssa|ttml|xml))/index\.m3u8$""",
    option = RegexOption.IGNORE_CASE,
)

/** MergingMediaSource rewrites child ids to "<childIndex>:<id>"; strips exactly one such prefix. */
internal fun String.withoutMergedSourcePrefix(): String = MERGED_SOURCE_PREFIX.replace(this, "")

private val MERGED_SOURCE_PREFIX = Regex("""^\d+:""")

internal data class SubtitleSourceId(val trackId: String?, val fromManifest: Boolean)

/**
 * Source identity of a player text track.
 *
 * Only an item built with side-loads is wrapped in a MergingMediaSource, so only then does the
 * format id carry a merge prefix; an HLS rendition id is "<GROUP-ID>:<NAME>", and stripping a
 * numeric GROUP-ID from an unmerged id would corrupt it. Child 0 is the main (manifest) source.
 */
internal fun subtitleSourceId(formatId: String?, groupId: String, merged: Boolean): SubtitleSourceId =
    SubtitleSourceId(
        trackId = if (merged) formatId?.withoutMergedSourcePrefix() else formatId,
        fromManifest = !merged || groupId.startsWith("0:"),
    )
