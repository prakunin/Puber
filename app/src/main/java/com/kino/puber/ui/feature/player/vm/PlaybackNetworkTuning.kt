package com.kino.puber.ui.feature.player.vm

import kotlin.math.min

/**
 * The timing budget for fetching media over a flaky connection.
 *
 * Every number here is spent while the forward buffer drains at 1x and nothing refills it, so the
 * whole ladder — socket timeout plus retry backoff, repeated [MINIMUM_RETRY_COUNT] times — has to
 * fit inside the shallowest forward buffer `DeviceBufferConfig` can produce. Once it does not, the
 * renderer starves, the error surfaces, and the player throws the buffer away to try another URL.
 *
 * Collected in one object because the socket timeouts and the backoff curve are a single budget:
 * tuning either in isolation is how they drifted apart.
 */
internal object PlaybackNetworkTuning {
    const val CONNECT_TIMEOUT_SECONDS = 5L
    const val READ_TIMEOUT_SECONDS = 5L

    /**
     * Media3 surfaces the error once a load's error count *exceeds* this, so a single load makes
     * [MINIMUM_RETRY_COUNT] + 1 attempts before the player looks for another URL.
     */
    const val MINIMUM_RETRY_COUNT = 5

    private const val RETRY_BACKOFF_STEP_MS = 250L
    private const val MAX_RETRY_BACKOFF_MS = 2_000L

    /** Pause before retry number [errorCount] (1-based), as handed to the load error policy. */
    fun retryBackoffMs(errorCount: Int): Long =
        min(errorCount * RETRY_BACKOFF_STEP_MS, MAX_RETRY_BACKOFF_MS)
}
