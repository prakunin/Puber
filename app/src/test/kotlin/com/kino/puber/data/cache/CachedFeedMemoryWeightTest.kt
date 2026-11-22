package com.kino.puber.data.cache

import com.kino.puber.util.FakePayloadStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CachedFeedMemoryWeightTest {

    private val store = FakePayloadStore()

    // A ten-character value encodes to twelve characters with its quotes.
    private fun feed() = CachedFeed(
        store = store,
        serializer = String.serializer(),
        ttl = 10.minutes,
        keyPrefix = "",
        clock = { 1_000_000L },
        maximumMemoryWeight = 30L,
        memoryWeigher = { it },
    )

    @Test
    fun aValuePastTheWeightCapEvictsTheOldestFromMemory() = runTest {
        val subject = feed()
        subject.put("oldest", "aaaaaaaaaa")
        subject.put("middle", "bbbbbbbbbb")
        subject.put("newest", "cccccccccc")
        store.readCount = 0

        subject.load("newest") { error("served from memory") }.toList()
        assertEquals(0, store.readCount)

        subject.load("oldest") { error("served from the store") }.toList()
        assertEquals(1, store.readCount)
    }
}
