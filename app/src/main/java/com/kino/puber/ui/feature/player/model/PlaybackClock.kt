package com.kino.puber.ui.feature.player.model

import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

/** `m:ss`, or `h:mm:ss` from one hour on; ASCII digits whatever the default locale. */
internal fun formatPlaybackClock(positionMs: Long): String {
    val totalSeconds = positionMs / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
