package com.kino.puber.data.cache

import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

        assertEquals(listOf(Cached.Value("fresh", isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun aFreshValueCarriesTheTimeItWasAccepted() = runTest {
        val emissions = feed().load("k") { "fresh" }.toList()

        assertEquals(now, (emissions.single() as Cached.Value).updatedAt)
    }

    @Test
    fun aStoredValueCarriesTheTimeItWasStored() = runTest {
        val storedAt = now
        feed().load("k") { "first" }.toList()
        now += 1.minutes.inWholeMilliseconds

        val emissions = feed().load("k") { "second" }.toList()

        assertEquals(storedAt, (emissions.single() as Cached.Value).updatedAt)
    }

    @Test
    fun aValueServedFromTheMemoryTierKeepsTheStampItWasAcceptedWith() = runTest {
        // The stamp is the value's age, not the reader's arrival time: two screens joining the same
        // load, or reading it minutes apart, must be told the same thing about how old it is.
        val subject = feed()
        subject.load("k") { "fresh" }.toList()
        store.remove("k")
        val acceptedAt = now
        now += 5.minutes.inWholeMilliseconds

        val emissions = subject.load("k") { "another" }.toList()

        assertEquals(acceptedAt, (emissions.single() as Cached.Value).updatedAt)
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

        assertEquals(listOf(Cached.Value("first", isStale = false, updatedAt = now)), emissions)
        assertEquals(0, loaderCalls)
    }

    @Test
    fun aFreshMemoryValueDoesNotReadThePersistentTierAgain() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()
        store.readCount = 0

        val emissions = subject.load("k") { "unexpected" }.toList()

        assertEquals(listOf(Cached.Value("first", isStale = false, updatedAt = now)), emissions)
        assertEquals(0, store.readCount)
    }

    @Test
    fun aFreshPersistentValueIsPromotedWithoutExtendingItsOriginalTtl() = runTest {
        val storedAt = now
        feed().load("k") { "stored" }.toList()
        now += 5.minutes.inWholeMilliseconds
        val subject = feed()
        store.readCount = 0

        subject.load("k") { "unexpected" }.toList()
        subject.load("k") { "unexpected" }.toList()

        assertEquals(1, store.readCount)
        now = storedAt + 11.minutes.inWholeMilliseconds
        val emissions = subject.load("k") { "fresh" }.toList()
        assertEquals(
            listOf(
                Cached.Value("stored", isStale = true, updatedAt = storedAt),
                Cached.Value("fresh", isStale = false, updatedAt = now),
            ),
            emissions,
        )
    }

    @Test
    fun staleStoredValueEmitsTheCachedValueThenTheFreshOne() = runTest {
        val storedAt = now
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds

        val emissions = feed().load("k") { "second" }.toList()

        assertEquals(
            listOf(
                Cached.Value("first", isStale = true, updatedAt = storedAt),
                Cached.Value("second", isStale = false, updatedAt = now),
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
                Cached.Value("first", isStale = true, updatedAt = now),
                Cached.Value("second", isStale = false, updatedAt = now),
            ),
            emissions,
        )
    }

    @Test
    fun forceCannotBeSatisfiedByAStoredValuePromotedDuringItsRoomRead() = runTest {
        val storedAt = now
        feed().load("k") { "stored" }.toList()
        val subject = feed()
        val forceReadStarted = CompletableDeferred<Unit>()
        val releaseForceRead = CompletableDeferred<Unit>()
        var reads = 0
        store.onRead = { key ->
            if (key == "k" && ++reads == 1) {
                forceReadStarted.complete(Unit)
                releaseForceRead.await()
            }
        }
        var forcedLoaderCalls = 0
        val forced = async {
            subject.load("k", force = true) {
                forcedLoaderCalls += 1
                "fresh"
            }.toList()
        }
        forceReadStarted.await()

        val normal = subject.load("k") { "unexpected" }.toList()
        releaseForceRead.complete(Unit)

        assertEquals(listOf(Cached.Value("stored", isStale = false, updatedAt = storedAt)), normal)
        assertEquals(
            listOf(
                Cached.Value("stored", isStale = true, updatedAt = storedAt),
                Cached.Value("fresh", isStale = false, updatedAt = now),
            ),
            forced.await(),
        )
        assertEquals(1, forcedLoaderCalls)
    }

    @Test
    fun aLoaderFailureAfterACachedEmissionIsReportedAndKeepsTheValue() = runTest {
        val storedAt = now
        feed().load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds
        val failure = IllegalStateException("offline")

        val emissions = feed().load("k") { throw failure }.toList()

        assertEquals(Cached.Value("first", isStale = true, updatedAt = storedAt), emissions[0])
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

        assertEquals(listOf(Cached.Value("fresh", isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun anEntryPastTheHardCeilingIsRemovedFromTheStore() = runTest {
        // Nothing will ever read a row this old again, so leaving it behind lets the table grow
        // without bound — the details namespace stores whole season/video/file payloads. The
        // assertion works the same way anUndecodablePayloadIsRemovedFromTheStore does: the loader
        // throws with nothing cached to emit, so only readUsable's removal can make the row vanish.
        feed().load("k") { "ancient" }.toList()
        now += 8.days.inWholeMilliseconds

        assertThrows<IllegalStateException> {
            feed().load("k") { throw IllegalStateException("offline") }.toList()
        }

        assertNull(store.read("k"))
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

        assertEquals(listOf(Cached.Value(Boxed(7), isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun anUndecodablePayloadIsRemovedFromTheStore() = runTest {
        // The decode failure leaves nothing to emit, so a failing loader must surface as a thrown
        // exception rather than a RefreshFailed. What this test actually pins down is the store: only
        // readUsable's removal of the undecodable row could make the second read below find it gone.
        store.write(key = "k", payload = "{not json", updatedAt = now)
        val failure = IllegalStateException("offline")

        assertThrows<IllegalStateException> {
            CachedFeed(
                store = store,
                serializer = Boxed.serializer(),
                ttl = 10.minutes,
                keyPrefix = "",
                clock = { now },
            ).load("k") { throw failure }.toList()
        }

        assertNull(store.read("k"))
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
        val markedStaleAt = checkNotNull(store.read("k")).updatedAt
        val emissions = subject.load("k") { "second" }.toList()
        assertEquals(
            listOf(
                Cached.Value("first", isStale = true, updatedAt = markedStaleAt),
                Cached.Value("second", isStale = false, updatedAt = now),
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
        assertEquals(listOf(Cached.Value("second", isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun aRoomReadCrossingKeyInvalidationCannotPromoteTheRemovedValue() = runTest {
        feed().load("k") { "stored" }.toList()
        val subject = feed()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        store.onRead = { key ->
            if (key == "k") {
                readStarted.complete(Unit)
                releaseRead.await()
            }
        }
        val reading = async { subject.load("k") { "fresh" }.toList() }
        readStarted.await()

        subject.invalidate("k")
        store.onRead = null
        releaseRead.complete(Unit)

        assertEquals(listOf(Cached.Value("fresh", isStale = false, updatedAt = now)), reading.await())
        assertEquals("\"fresh\"", store.read("k")?.payload)
    }

    @Test
    fun aRoomReadCrossingNamespaceInvalidationCannotPromoteTheRemovedValue() = runTest {
        feed().load("k") { "stored" }.toList()
        val subject = feed()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        store.onRead = { key ->
            if (key == "k") {
                readStarted.complete(Unit)
                releaseRead.await()
            }
        }
        val reading = async { subject.load("k") { "fresh" }.toList() }
        readStarted.await()

        subject.invalidateNamespace()
        store.onRead = null
        releaseRead.complete(Unit)

        assertEquals(listOf(Cached.Value("fresh", isStale = false, updatedAt = now)), reading.await())
        assertEquals("\"fresh\"", store.read("k")?.payload)
    }

    @Test
    fun aRoomReadCrossingAStoreWipeCannotPromoteThePreviousGeneration() = runTest {
        feed().load("k") { "previous session" }.toList()
        val subject = feed()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        store.onRead = { key ->
            if (key == "k") {
                readStarted.complete(Unit)
                releaseRead.await()
            }
        }
        val reading = async { subject.load("k") { "next session" }.toList() }
        readStarted.await()

        store.clear()
        store.onRead = null
        releaseRead.complete(Unit)

        assertEquals(
            listOf(Cached.Value("next session", isStale = false, updatedAt = now)),
            reading.await(),
        )
        assertEquals("\"next session\"", store.read("k")?.payload)
    }

    /**
     * The interleaving these tests care about: a load is running, something invalidates the key
     * underneath it — one key, the namespace, or the whole store — and the loader then completes
     * holding a payload that predates that. The write must not land, or the entry comes back with a
     * fresh timestamp and the next read believes it.
     *
     * The in-flight cache reissues the loader once its own entry is invalidated, so the retry is held
     * open for the whole test: the only write that can be observed here is the superseded one.
     */
    private suspend fun TestScope.runSupersededLoad(subject: CachedFeed<String>, supersede: suspend () -> Unit) {
        val firstLoaderStarted = CompletableDeferred<Unit>()
        val releaseFirstLoader = CompletableDeferred<Unit>()
        val retryNeverFinishes = CompletableDeferred<Unit>()
        var loaderCalls = 0
        val loading = async {
            subject.load("k") {
                loaderCalls += 1
                if (loaderCalls == 1) {
                    firstLoaderStarted.complete(Unit)
                    releaseFirstLoader.await()
                    "superseded"
                } else {
                    retryNeverFinishes.await()
                    "retried"
                }
            }.toList()
        }
        firstLoaderStarted.await()

        supersede()
        releaseFirstLoader.complete(Unit)
        runCurrent()

        loading.cancel()
    }

    @Test
    fun invalidatingDuringALoadKeepsThatLoadsResultOutOfTheStore() = runTest {
        val subject = feed()

        runSupersededLoad(subject) { subject.invalidate("k") }

        assertNull(store.read("k"))
    }

    @Test
    fun invalidatingTheNamespaceDuringALoadKeepsThatLoadsResultOutOfTheStore() = runTest {
        val subject = CachedFeed(
            store = store,
            serializer = String.serializer(),
            ttl = 10.minutes,
            keyPrefix = "k",
            clock = { now },
        )

        runSupersededLoad(subject) { subject.invalidateNamespace() }

        assertNull(store.read("k"))
    }

    @Test
    fun markingStaleDuringALoadKeepsThatLoadFromRefreshingTheTimestamp() = runTest {
        val subject = feed()
        subject.load("k") { "first" }.toList()
        now += 11.minutes.inWholeMilliseconds

        runSupersededLoad(subject) { subject.markStale("k") }

        // The superseded write would have replaced the payload and stamped it with the current
        // clock, quietly undoing the markStale that raced it.
        assertEquals("\"first\"", store.read("k")?.payload)
        val age = now - (store.read("k")?.updatedAt ?: 0L)
        assertTrue(age > 10.minutes.inWholeMilliseconds) { "the entry was made fresh again, age was $age" }
    }

    @Test
    fun wipingTheStoreDuringALoadKeepsThatLoadsResultOutOfTheStore() = runTest {
        // Logout wipes the table while the screen's loads are still alive, and the store's own
        // generation guard cannot catch them: it captures the generation when the write reaches it,
        // by which point the bump has already happened. Left unguarded, the previous account's
        // payload lands in the freshly wiped table and is served to the next one as fresh.
        val subject = feed()

        runSupersededLoad(subject) { store.clear() }

        assertNull(store.read("k"))
    }

    @Test
    fun aLoaderThatLandsAfterAWipeCannotRefillTheMemoryTier() = runTest {
        // The other half of the same hole. The generation check at the top of load() fires once per
        // generation, so a load that consumes it leaves a leader still out on the network free to
        // repopulate the memory tier with the previous session's value — and nothing will ever clear
        // it again.
        val subject = feed()
        val firstLoaderStarted = CompletableDeferred<Unit>()
        val releaseFirstLoader = CompletableDeferred<Unit>()
        val retryNeverFinishes = CompletableDeferred<Unit>()
        var loaderCalls = 0
        val loading = async {
            subject.load("k") {
                loaderCalls += 1
                if (loaderCalls == 1) {
                    firstLoaderStarted.complete(Unit)
                    releaseFirstLoader.await()
                    "previous session"
                } else {
                    retryNeverFinishes.await()
                    "retried"
                }
            }.toList()
        }
        firstLoaderStarted.await()

        store.clear()
        // Another key's load consumes the one-shot generation check, so nothing else is left to
        // notice the wipe by the time the in-flight leader lands.
        subject.load("other") { "after the wipe" }.toList()
        releaseFirstLoader.complete(Unit)
        runCurrent()
        loading.cancel()

        var nextSessionLoaderCalls = 0
        val emissions = subject.load("k") { nextSessionLoaderCalls += 1; "next session" }.toList()

        assertEquals(1, nextSessionLoaderCalls)
        assertEquals(listOf(Cached.Value("next session", isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun clearingTheStoreDropsTheMemoryTierSoTheNextLoadGoesBackToTheLoader() = runTest {
        // A wipe (logout, domain switch) empties the table, but the loader-deduplicating memory tier
        // is a separate map that the wipe cannot reach directly. Left alone it answers the next load
        // with the previous session's value for up to a full TTL, so the feed has to notice the
        // store's generation moved and drop it.
        val subject = feed()
        subject.load("k") { "first" }.toList()

        store.clear()

        var loaderCalls = 0
        val emissions = subject.load("k") { loaderCalls += 1; "second" }.toList()

        assertEquals(1, loaderCalls)
        assertEquals(listOf(Cached.Value("second", isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun aStoreThatWasNeverClearedKeepsDeduplicatingLoaderCalls() = runTest {
        // The generation check must not become a reason to drop the memory tier on every load.
        val subject = feed()
        subject.load("k") { "first" }.toList()
        store.remove("k")

        var loaderCalls = 0
        subject.load("k") { loaderCalls += 1; "second" }.toList()

        assertEquals(0, loaderCalls)
    }

    @Serializable
    private data class Boxed(val value: Int)

    private class FakePayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()
        var readCount: Int = 0
        var onRead: (suspend (String) -> Unit)? = null

        override var generation: Long = 0L
            private set

        override suspend fun read(key: String): StoredPayload? {
            readCount += 1
            val stored = rows[key]
            onRead?.invoke(key)
            return stored
        }

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
            generation += 1
            try {
                rows.clear()
            } finally {
                generation += 1
            }
        }
    }
}
