package com.kino.puber.data.repository

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchCompletionPolicyTest {

    @Test
    fun aVideoLeftDuringTheCreditsCountsAsFinished() {
        // Almost nobody sits through the credits, and the credits-skip feature cuts them off
        // earlier still, so demanding the full duration would name almost nothing finished.
        assertTrue(WatchCompletionPolicy.isFinished(time = 5_700, duration = 6_000))
        assertTrue(WatchCompletionPolicy.isFinished(time = 6_000, duration = 6_000))
    }

    @Test
    fun aVideoAbandonedPartWayThroughDoesNot() {
        assertFalse(WatchCompletionPolicy.isFinished(time = 4_800, duration = 6_000))
        assertFalse(WatchCompletionPolicy.isFinished(time = 0, duration = 6_000))
    }

    @Test
    fun anUnknownPositionOrLengthDecidesNothing() {
        assertFalse(WatchCompletionPolicy.isFinished(time = null, duration = 6_000))
        assertFalse(WatchCompletionPolicy.isFinished(time = 5_700, duration = null))
        // A zero length would make every position look complete.
        assertFalse(WatchCompletionPolicy.isFinished(time = 0, duration = 0))
    }
}
