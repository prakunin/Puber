package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.interactor.player.StreamType

/** What a track report says about the subtitle rows. */
internal enum class SubtitleReportKind {
    /** Progressive or unknown stream: the picker keeps the API rows. */
    API_ROWS,

    /** A side-loaded HLS item reported no text group yet: the report says nothing about subtitles. */
    NOT_YET,

    /** The player's own text groups, possibly none, are the rows. */
    PLAYER_ROWS,
}

internal fun subtitleReportKind(
    streamType: StreamType?,
    hasSideLoads: Boolean,
    textGroupCount: Int,
): SubtitleReportKind = when {
    streamType != StreamType.HLS -> SubtitleReportKind.API_ROWS
    hasSideLoads && textGroupCount == 0 -> SubtitleReportKind.NOT_YET
    else -> SubtitleReportKind.PLAYER_ROWS
}
