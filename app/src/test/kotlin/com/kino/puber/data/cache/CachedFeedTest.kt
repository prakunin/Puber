package com.kino.puber.data.cache

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class CachedFeedTest {

    private val store = FakePayloadStore()
    private var now = 1_000_000L

    private fun feed(ttl: kotlin.time.Duration = 10.minutes) = CachedFeed(
        store = store,
        serializer = String.serializer(),
        ttl = ttl,
        keyPrefix = "",
        clock = { now },
    )

    @Test
    fun emptyKeyEmitsOnceFromTheLoader() = runTest {
        val emissions = feed().load("k") { "fresh" }.toList()

        assertEquals(listOf(Cached.Value("fresh", isStale = false)), emissions)
    }

    @Test
    fun theLoaderResultIsStoredWithTheCurrentTime() = runTest {
        feed().load("k") { "fresh" }.toList()

        assertEquals(now, store.read("k")?.updatedAt)
    }

    @Test
    fun freshStoredValueEmitsOnceWithoutCallingTheLoader() = runTest {
        feed().load("k") { "first" }.toList()
        var loaderCalls = 0

        val emissions = feed().load("k") { loaderCalls += 1; "second" }.toList()

        assertEquals(listOf(Cached.Value("first", isStale = false)), emissions)
        assertEquals(0, loaderCalls)
    }

    @Test
    fun staleStoredValueEmitsTheCachedValueThenTheFreshOne() = runTest {
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds

        val emissions = feed().load("k") { "second" }.toList()

        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun forceRevalidatesEvenWhenTheStoredValueIsFresh() = runTest {
        feed().load("k") { "first" }.toList()

        val emissions = feed().load("k", force = true) { "second" }.toList()

        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun aLoaderFailureAfterACachedEmissionIsReportedAndKeepsTheValue() = runTest {
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds
        val failure = IllegalStateException("offline")

        val emissions = feed().load("k") { throw failure }.toList()

        assertEquals(Cached.Value("first", isStale = true), emissions[0])
        assertEquals(Cached.RefreshFailed(failure), emissions[1])
        assertEquals("\"first\"", store.read("k")?.payload)
    }

    @Test
    fun aLoaderFailureWithNothingCachedThrows() = runTest {
        val failure = IllegalStateException("offline")

        assertThrows<IllegalStateException> {
            feed().load("k") { throw failure }.toList()
        }
    }

    @Test
    fun anEntryPastTheHardCeilingCountsAsAbsent() = runTest {
        feed().load("k") { "ancient" }.toList()
        now += 8.days.inWholeMilliseconds

        val emissions = feed().load("k") { "fresh" }.toList()

        assertEquals(listOf(Cached.Value("fresh", isStale = false)), emissions)
    }

    @Test
    fun anUndecodablePayloadCountsAsAbsentAndIsDropped() = runTest {
        // A model field can change without a schema version changing, so a payload written by an
        // older build must not be able to break the screen it feeds.
        store.write(key = "k", payload = "{not json", updatedAt = now)

        val emissions = CachedFeed(
            store = store,
            serializer = Boxed.serializer(),
            ttl = 10.minutes,
            keyPrefix = "",
            clock = { now },
        ).load("k") { Boxed(7) }.toList()

        assertEquals(listOf(Cached.Value(Boxed(7), isStale = false)), emissions)
    }

    @Test
    fun invalidateNamespaceDropsEveryKeyUnderThePrefix() = runTest {
        val subject = CachedFeed(
            store = store,
            serializer = String.serializer(),
            ttl = 10.minutes,
            keyPrefix = "home:",
            clock = { now },
        )
        subject.load("home:hot") { "a" }.toList()
        subject.load("home:fresh") { "b" }.toList()

        subject.invalidateNamespace()

        assertNull(store.read("home:hot"))
        assertNull(store.read("home:fresh"))
    }

    @Test
    fun concurrentLoadsOfOneKeyShareASingleLoaderCall() = runTest {
        val gate = CompletableDeferred<Unit>()
        var loaderCalls = 0
        val subject = feed()
        val loader: suspend () -> String = {
            loaderCalls += 1
            gate.await()
            "fresh"
        }

        val first = async { subject.load("k", loader = loader).toList() }
        val second = async { subject.load("k", loader = loader).toList() }
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, loaderCalls)
    }

    @Test
    fun markStaleKeepsThePayloadReadableAndForcesTheNextLoadToRevalidate() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.markStale("k")

        assertEquals("\"first\"", store.read("k")?.payload)
        val emissions = subject.load("k") { "second" }.toList()
        assertEquals(
            listOf(
                Cached.Value("first", isStale = true),
                Cached.Value("second", isStale = false),
            ),
            emissions,
        )
    }

    @Test
    fun markStaleLeavesTheEntryInsideTheHardCeiling() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.markStale("k")

        val age = now - (store.read("k")?.updatedAt ?: 0L)
        assertTrue(age < CachedFeed.HardCeiling.inWholeMilliseconds)
    }

    @Test
    fun invalidateRemovesTheEntryOutright() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()

        subject.invalidate("k")

        assertNull(store.read("k"))
        val emissions = subject.load("k") { "second" }.toList()
        assertEquals(listOf(Cached.Value("second", isStale = false)), emissions)
    }

    @Serializable
    private data class Boxed(val value: Int)

    private class FakePayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override suspend fun read(key: String): StoredPayload? = rows[key]

        override suspend fun write(key: String, payload: String, updatedAt: Long) {
            rows[key] = StoredPayload(payload = payload, updatedAt = updatedAt)
        }

        override suspend fun touch(key: String, updatedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(updatedAt = updatedAt) }
        }

        override suspend fun remove(key: String) {
            rows.remove(key)
        }

        override suspend fun removeByPrefix(prefix: String) {
            rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
        }

        override suspend fun clear() {
            rows.clear()
        }
    }
}
