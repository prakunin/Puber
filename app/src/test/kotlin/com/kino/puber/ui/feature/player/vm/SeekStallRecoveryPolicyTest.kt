package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SeekStallRecoveryPolicyTest {

    @Test
    fun bufferingWithoutMeaningfulProgress_recovers() {
        assertTrue(
            SeekStallRecoveryPolicy.shouldRecover(
                playbackState = Player.STATE_BUFFERING,
                previousBufferedPositionMs = 30_000L,
                currentBufferedPositionMs = 30_500L,
            ),
        )
    }

    @Test
    fun bufferingThatContinuesToAdvance_waitsForAnotherInterval() {
        assertFalse(
            SeekStallRecoveryPolicy.shouldRecover(
                playbackState = Player.STATE_BUFFERING,
                previousBufferedPositionMs = 30_000L,
                currentBufferedPositionMs = 31_000L,
            ),
        )
    }

    @Test
    fun readyPlayer_neverRecovers() {
        assertFalse(
            SeekStallRecoveryPolicy.shouldRecover(
                playbackState = Player.STATE_READY,
                previousBufferedPositionMs = 30_000L,
                currentBufferedPositionMs = 30_000L,
            ),
        )
    }
}
