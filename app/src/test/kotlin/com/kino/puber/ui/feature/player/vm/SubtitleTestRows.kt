package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

/** An HLS rendition row as the player reports it: no API url, the group id carries the name. */
internal fun manifestRendition(
    language: String,
    name: String,
    forced: Boolean = false,
    label: String? = name,
    merged: Boolean = true,
) = SubtitleTrackUIState(
    label = "",
    language = language,
    url = "",
    isForced = forced,
    descriptiveLabel = label,
    playerTrackId = "subs:$name",
    playerTrackGroupId = (if (merged) "0:" else "") + "subtitle:$name",
    playerTrackIndex = 0,
    fromManifest = true,
)
