package com.kino.puber.ui.feature.player.vm

import com.kino.puber.domain.model.BufferPreset
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The retry ladder is spent with nothing refilling the buffer, so every bound here is measured
 * against how long the forward buffer survives at 1x. Exceed it and the renderer starves, the
 * error escapes to the player, and the whole buffer is discarded to try another URL.
 */
internal class PlaybackNetworkTuningTest {

    @Test
    fun `a stalled read is abandoned before a shallow buffer runs dry`() {
        val cost = PlaybackNetworkTuning.READ_TIMEOUT_SECONDS * MS_PER_SECOND +
            PlaybackNetworkTuning.retryBackoffMs(1)

        assertTrue(
            cost <= SHALLOW_BUFFER_MS,
            "a dead socket costs ${cost}ms before the first retry, more than the ${SHALLOW_BUFFER_MS}ms " +
                "of forward buffer the shallowest preset holds at a high bitrate",
        )
    }

    @Test
    fun `a stalled connect is abandoned before a shallow buffer runs dry`() {
        val cost = PlaybackNetworkTuning.CONNECT_TIMEOUT_SECONDS * MS_PER_SECOND +
            PlaybackNetworkTuning.retryBackoffMs(1)

        assertTrue(
            cost <= SHALLOW_BUFFER_MS,
            "a hung connect costs ${cost}ms before the first retry, more than the ${SHALLOW_BUFFER_MS}ms " +
                "of forward buffer the shallowest preset holds at a high bitrate",
        )
    }

    @Test
    fun `the backoff ladder alone cannot drain a healthy buffer`() {
        val total = ladder().sum()

        assertTrue(
            total <= MAX_TOTAL_BACKOFF_MS,
            "pauses alone add up to ${total}ms across the ladder, over the ${MAX_TOTAL_BACKOFF_MS}ms budget",
        )
    }

    @Test
    fun `a dead endpoint is given up on in seconds, not minutes`() {
        // Media3 surfaces the error once errorCount exceeds the minimum, so the ladder runs one
        // more attempt than it pauses.
        val worstCase = ATTEMPTS_BEFORE_GIVING_UP *
            PlaybackNetworkTuning.READ_TIMEOUT_SECONDS * MS_PER_SECOND + ladder().sum()

        assertTrue(
            worstCase <= MAX_OUTAGE_BEFORE_GIVING_UP_MS,
            "the player waits ${worstCase}ms before it even considers another URL, " +
                "over the ${MAX_OUTAGE_BEFORE_GIVING_UP_MS}ms budget",
        )
    }

    @Test
    fun `backoff still grows with each failure and stays capped`() {
        val ladder = ladder()

        assertTrue(ladder.first() > 0, "the first retry must still pause: $ladder")
        assertTrue(
            ladder.sorted() == ladder,
            "backoff must not shrink as failures pile up: $ladder",
        )
        assertTrue(
            PlaybackNetworkTuning.retryBackoffMs(HUGE_ERROR_COUNT) ==
                PlaybackNetworkTuning.retryBackoffMs(HUGE_ERROR_COUNT + 1),
            "backoff must saturate instead of growing without bound",
        )
    }

    private fun ladder(): List<Long> =
        (1..PlaybackNetworkTuning.MINIMUM_RETRY_COUNT).map(PlaybackNetworkTuning::retryBackoffMs)

    private companion object {
        const val MS_PER_SECOND = 1_000L

        // A 4K remux, the worst bitrate the byte budget has to survive.
        const val HIGH_BITRATE_BYTES_PER_MS = 5_000L

        // The hardware this runs on: both target TVs report a 384 MB large heap.
        val REFERENCE_DEVICE = DeviceBufferConfig.DeviceMemory(heapLimitMb = 384, isLowRam = false)

        /**
         * What the default preset actually holds on that hardware at that bitrate — read off
         * [DeviceBufferConfig] rather than hardcoded, so shrinking the buffer re-tightens these
         * bounds instead of silently invalidating them. A single failed attempt has to cost less
         * than this, or the stall is visible however transient the failure was.
         */
        val SHALLOW_BUFFER_MS = with(DeviceBufferConfig.resolve(REFERENCE_DEVICE, BufferPreset.AUTO)) {
            (targetBufferBytes - backBufferDurationMs * HIGH_BITRATE_BYTES_PER_MS) /
                HIGH_BITRATE_BYTES_PER_MS
        }

        // Pauses are dead time on top of the timeouts; they must not eat a buffer on their own.
        const val MAX_TOTAL_BACKOFF_MS = 5_000L

        // Past this the player is better off wiping the buffer for another URL than waiting —
        // by then the user has already reached for the remote.
        const val MAX_OUTAGE_BEFORE_GIVING_UP_MS = 35_000L

        val ATTEMPTS_BEFORE_GIVING_UP = PlaybackNetworkTuning.MINIMUM_RETRY_COUNT + 1

        const val HUGE_ERROR_COUNT = 100
    }
}
