package com.kino.puber.ui.feature.player.vm

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamCandidate
import com.kino.puber.domain.interactor.player.StreamType
import java.util.Locale

internal data class SubtitleSpec(
    val uri: String,
    val mimeType: String,
    val language: String,
    val selectionFlags: Int,
    val id: String,
    val label: String,
)

internal data class MediaItemSpec(
    val uri: String,
    /** [MimeTypes.APPLICATION_M3U8] for [StreamType.HLS], null otherwise. */
    val mimeType: String?,
    val subtitles: List<SubtitleSpec>,
) {
    /** True when the item carries side-loaded subtitles, which is when the player merges sources. */
    val hasSideLoads: Boolean get() = subtitles.isNotEmpty()
}

/**
 * Builds the [MediaItem] every stream is played from.
 *
 * Every API subtitle is attached, on both transports, so the player's own
 * `DefaultMediaSourceFactory` side-loads them. HLS is declared by its MIME type because the
 * stream type comes from the API, not from the URL.
 */
internal class PlaybackMediaItemFactory {

    /** Pure: no `Uri`, no `MediaItem`, so it is unit-tested on the JVM. */
    fun spec(stream: StreamCandidate, subtitles: List<SubtitleLink>?): MediaItemSpec = MediaItemSpec(
        uri = stream.url,
        mimeType = MimeTypes.APPLICATION_M3U8.takeIf { stream.type == StreamType.HLS },
        subtitles = subtitles.sideLoadable().map { sub ->
            val stableKey = sub.url.stableSubtitleKey()
            SubtitleSpec(
                uri = sub.url,
                mimeType = subtitleMimeType(sub.url),
                language = sub.lang,
                selectionFlags = if (sub.forced == true) C.SELECTION_FLAG_FORCED else 0,
                id = stableKey,
                label = stableKey,
            )
        },
    )

    fun build(spec: MediaItemSpec): MediaItem = spec.toMediaItem()

    private fun MediaItemSpec.toMediaItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        mimeType?.let(builder::setMimeType)
        if (subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(sub.uri.toUri())
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setSelectionFlags(sub.selectionFlags)
                        .setLabel(sub.label)
                        .setId(sub.id)
                        .build()
                },
            )
        }
        return builder.build()
    }
}

/** A link without a url has nothing to load: it is neither a picker row nor a side-load. */
internal fun List<SubtitleLink>?.sideLoadable(): List<SubtitleLink> =
    orEmpty().filter { it.url.isNotBlank() }

internal fun subtitleMimeType(url: String): String {
    val normalizedUrl = url
        .substringBefore('?')
        .substringBefore('#')
        .lowercase(Locale.ROOT)
    return when {
        normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
        normalizedUrl.endsWith(".ass") || normalizedUrl.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        normalizedUrl.endsWith(".ttml") || normalizedUrl.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}
