package com.kino.puber.core.tvhome

import com.kino.puber.core.contentlink.ContentTarget

internal data class PublishedProgram(
    val stableKey: String,
    val title: String,
    val artworkUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastEngagementTimeMs: Long,
    val target: ContentTarget.Playback,
)
