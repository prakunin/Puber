package com.kino.puber.ui.feature.player.vm

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.sameSubtitleLanguage

/**
 * The picker rows the player reports for its text groups.
 *
 * [merged] is true when the item was built with side-loaded subtitles, which is when
 * `DefaultMediaSourceFactory` wraps it in a MergingMediaSource and prefixes every id (see
 * [subtitleSourceId]). Rendition matching never falls back to a position.
 */
@OptIn(UnstableApi::class)
internal fun buildSubtitleRows(
    textGroups: List<Tracks.Group>,
    renditions: List<HlsMultivariantPlaylist.Rendition>,
    merged: Boolean,
): List<SubtitleTrackUIState> = textGroups.flatMap { group ->
    val groupId = group.mediaTrackGroup.id
    (0 until group.length).map { trackIndex ->
        val format = group.getTrackFormat(trackIndex)
        val source = subtitleSourceId(format.id, groupId, merged)
        val rendition = if (source.fromManifest) findRendition(format, source.trackId, renditions) else null
        buildSubtitleRow(format, rendition?.format, rendition?.url?.toString(), source, groupId, trackIndex)
    }
}

private fun buildSubtitleRow(
    format: Format,
    renditionFormat: Format?,
    renditionUri: String?,
    source: SubtitleSourceId,
    groupId: String,
    trackIndex: Int,
): SubtitleTrackUIState {
    val flags = format.selectionFlags or (renditionFormat?.selectionFlags ?: 0)
    // SubtitleLabeler builds every visible label once the full row list is known.
    return SubtitleTrackUIState(
        label = "",
        language = format.language ?: renditionFormat?.language.orEmpty(),
        url = "",
        isForced = flags and C.SELECTION_FLAG_FORCED != 0,
        descriptiveLabel = format.label ?: renditionFormat?.label,
        playerTrackId = source.trackId ?: renditionFormat?.id,
        playerTrackGroupId = groupId,
        playerTrackUri = renditionUri,
        playerTrackIndex = trackIndex,
        fromManifest = source.fromManifest,
    )
}

@OptIn(UnstableApi::class)
private fun findRendition(
    format: Format,
    trackId: String?,
    renditions: List<HlsMultivariantPlaylist.Rendition>,
): HlsMultivariantPlaylist.Rendition? {
    val matchers = listOf(
        { renditions.filter { trackId != null && it.format.id == trackId } },
        {
            renditions.filter {
                it.format.label == format.label &&
                    sameSubtitleLanguage(it.format.language.orEmpty(), format.language.orEmpty())
            }
        },
    )
    return matchers.firstNotNullOfOrNull { matcher -> matcher().singleOrNull() }
}

@OptIn(UnstableApi::class)
internal fun List<Tracks.Group>.toPlayerTextTracks(): List<PlayerTextTrack> =
    flatMapIndexed { groupIndex, group ->
        (0 until group.length).map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            PlayerTextTrack(
                groupId = group.mediaTrackGroup.id,
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                formatId = format.id,
                formatLabel = format.label,
                language = format.language,
                isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
            )
        }
    }
