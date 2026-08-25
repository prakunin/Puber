package com.kino.puber.ui.feature.player.vm

import android.os.Handler
import androidx.media3.common.Player

internal class SeekStallRecovery(
    private val handler: Handler,
    private val player: () -> Player?,
    private val streamUrl: () -> String?,
    private val onStalled: (streamUrl: String) -> Unit,
) {
    private val timeout = Runnable(::check)
    private var active = false
    private var scheduled = false
    private var bufferedPositionMs = 0L
    private val attemptedUrls = mutableSetOf<String>()

    fun start(player: Player) {
        active = true
        attemptedUrls.clear()
        schedule(player)
    }

    fun onStreamSwitched(player: Player) {
        if (active) schedule(player)
    }

    fun cancel() {
        if (scheduled) handler.removeCallbacks(timeout)
        scheduled = false
        active = false
        bufferedPositionMs = 0L
        attemptedUrls.clear()
    }

    private fun schedule(player: Player) {
        val currentUrl = streamUrl() ?: return
        if (currentUrl in attemptedUrls) return
        handler.removeCallbacks(timeout)
        bufferedPositionMs = player.bufferedPosition
        scheduled = true
        handler.postDelayed(timeout, STALL_TIMEOUT_MS)
    }

    private fun check() {
        scheduled = false
        val currentPlayer = player() ?: return cancel()
        val currentUrl = streamUrl() ?: return cancel()
        if (!active) return

        when {
            currentPlayer.playbackState == Player.STATE_READY -> cancel()
            SeekStallRecoveryPolicy.shouldRecover(
                playbackState = currentPlayer.playbackState,
                previousBufferedPositionMs = bufferedPositionMs,
                currentBufferedPositionMs = currentPlayer.bufferedPosition,
            ) -> {
                attemptedUrls += currentUrl
                onStalled(currentUrl)
            }
            else -> schedule(currentPlayer)
        }
    }

    private companion object {
        const val STALL_TIMEOUT_MS = 8_000L
    }
}
