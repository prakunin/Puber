package com.kino.puber.data.api.network.diagnostics

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BoundedDownloadTest {

    @Test
    fun bitsPerSecond_convertsBytesAndMillisToBits() {
        val sample = ThroughputSample(bytes = 1_250_000, elapsedMillis = 1_000)

        assertEquals(10_000_000.0, sample.bitsPerSecond)
    }

    /** A sample taken faster than the clock can see is not an infinite link. */
    @Test
    fun bitsPerSecond_isZero_whenNoTimePassed() {
        val sample = ThroughputSample(bytes = 4_096, elapsedMillis = 0)

        assertEquals(0.0, sample.bitsPerSecond)
    }

    @Test
    fun readAtMost_stopsAtTheCap_whenTheSourceHasMore() {
        val source = Buffer().write(ByteArray(10_000))

        val read = source.readAtMost(maxBytes = 4_096) { true }

        assertEquals(4_096L, read)
    }

    @Test
    fun readAtMost_returnsWhatArrived_whenTheSourceEndsEarly() {
        val source = Buffer().write(ByteArray(1_500))

        val read = source.readAtMost(maxBytes = 4_096) { true }

        assertEquals(1_500L, read)
    }

    /**
     * Cancelling mid-download has to free the socket rather than read on to the cap, so the loop
     * asks before every chunk and stops the moment the answer is no.
     */
    @Test
    fun readAtMost_stopsEarly_whenTheCallerIsCancelled() {
        val source = Buffer().write(ByteArray(300_000))
        var chunks = 0

        val read = source.readAtMost(maxBytes = 300_000) { chunks++ < 2 }

        assertEquals(2 * DOWNLOAD_CHUNK_BYTES, read)
    }
}
