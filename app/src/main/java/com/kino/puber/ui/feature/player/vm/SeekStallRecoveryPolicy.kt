package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player

internal object SeekStallRecoveryPolicy {
    const val MIN_BUFFER_PROGRESS_MS = 1_000L

    fun shouldRecover(
        playbackState: Int,
        previousBufferedPositionMs: Long,
        currentBufferedPositionMs: Long,
    ): Boolean {
        return playbackState == Player.STATE_BUFFERING &&
            currentBufferedPositionMs - previousBufferedPositionMs < MIN_BUFFER_PROGRESS_MS
    }
}
