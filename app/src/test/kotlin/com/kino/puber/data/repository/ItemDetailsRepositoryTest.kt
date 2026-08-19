package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentCacheRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.minutes

class ItemDetailsRepositoryTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchStateRepository = mockk<WatchStateRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()
    private var now = 1_000_000L
    private val repository = ItemDetailsRepository(
        api = api,
        watchStateRepository = watchStateRepository,
        contentCache = ContentCacheRepository(store = store, clock = { now }),
    )

    @Test
    fun observeItemDetailsEmitsOnceWhenNothingIsStored() = runTest {
        givenApiReturns(item(42, "Fresh"))

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun observeItemDetailsServesTheStoredItemOnASecondOpen() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false, updatedAt = now)), emissions)
        coVerify(exactly = 1) { api.getItemDetails(42) }
    }

    @Test
    fun warmingFillsTheCacheTheNextOpenReads() = runTest {
        givenApiReturns(item(42, "Fresh"))

        repository.warmItemDetails(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false, updatedAt = now)), emissions)
        coVerify(exactly = 1) { api.getItemDetails(42) }
    }

    @Test
    fun anObserverJoinsAWarmThatIsStillInFlight() = runTest {
        // The case the whole feature is built on: the user presses OK while the warm its card
        // started is still out. A second request here would mean the press waited for its own.
        val release = CompletableDeferred<Unit>()
        coEvery { api.getItemDetails(42) } coAnswers {
            release.await()
            Result.success(ApiResponse(item = item(42, "Fresh")))
        }

        val warming = async { repository.warmItemDetails(42) }
        runCurrent()
        val observing = async { repository.observeItemDetails(42).toList() }
        runCurrent()
        release.complete(Unit)
        warming.await()

        assertEquals(
            listOf(Cached.Value(item(42, "Fresh"), isStale = false, updatedAt = now)),
            observing.await(),
        )
        coVerify(exactly = 1) { api.getItemDetails(42) }
    }

    @Test
    fun warmingDoesNotTouchTheWatchStateIndex() = runTest {
        // A prefetch is work nobody asked for, so it must not tick settledChanges, redraw watched
        // badges, or re-page a list with hideWatched on.
        givenApiReturns(item(42, "Fresh"))

        repository.warmItemDetails(42)

        coVerify(exactly = 0) { watchStateRepository.recordFromServer(any(), any()) }
    }

    @Test
    fun cacheOnlyReadReturnsTheSharedValueWithoutTouchingTheWatchStateIndex() = runTest {
        val expected = item(42, "Preview")
        givenApiReturns(expected)

        val actual = repository.getItemDetailsCacheOnly(42)

        assertEquals(expected, actual)
        coVerify(exactly = 0) { watchStateRepository.recordFromServer(any(), any()) }
    }

    @Test
    fun readingAWarmedItemRecordsItWithTheStampTheValueCarries() = runTest {
        // Recording happens on the observing path, so a Details screen that only joined a prefetch
        // still sharpens the index — with the value's own age, not the moment it happened to open.
        val fetched = item(42, "Fresh")
        givenApiReturns(fetched)
        repository.warmItemDetails(42)
        val warmedAt = now
        now += 1.minutes.inWholeMilliseconds

        repository.observeItemDetails(42).toList()

        coVerify(exactly = 1) { watchStateRepository.recordFromServer(listOf(fetched), warmedAt) }
    }

    @Test
    fun readingAStaleItemRecordsOnlyTheRevalidatedValue() = runTest {
        // A stale value is about to be replaced, and it can be days old. Restamping the index with
        // it would let it outrank observations that are actually newer.
        val stale = item(42, "Fresh")
        givenApiReturns(stale)
        repository.observeItemDetails(42).toList()
        repository.markStale(42)
        val newer = item(42, "Newer")
        givenApiReturns(newer)
        now += 1.minutes.inWholeMilliseconds

        repository.observeItemDetails(42).toList()

        coVerify(exactly = 1) { watchStateRepository.recordFromServer(listOf(newer), now) }
        // Once, from the first read. A second would be the stale emission being recorded as well.
        coVerify(exactly = 1) { watchStateRepository.recordFromServer(listOf(stale), any()) }
    }

    @Test
    fun aWarmWithNothingStoredFailsWhenTheLoadFails() = runTest {
        // What tells the prefetcher this id was not warmed, so a later attempt is still worth making.
        coEvery { api.getItemDetails(42) } throws IllegalStateException("network down")

        assertThrows<IllegalStateException> { repository.warmItemDetails(42) }
    }

    @Test
    fun aWarmThatOnlyFailsToRevalidateSucceedsBecauseTheStoredValueStands() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        repository.markStale(42)
        coEvery { api.getItemDetails(42) } throws IllegalStateException("network down")

        repository.warmItemDetails(42)
    }

    @Test
    fun markStaleKeepsServingTheItemButRevalidates() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.markStale(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(Cached.Value(item(42, "Fresh"), isStale = true, updatedAt = now), emissions[0])
        assertEquals(Cached.Value(item(42, "Newer"), isStale = false, updatedAt = now), emissions[1])
    }

    @Test
    fun invalidateRemovesTheItemSoTheNextOpenWaits() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.invalidate(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Newer"), isStale = false, updatedAt = now)), emissions)
    }

    @Test
    fun everyItemReadSharpensTheWatchStateIndex() = runTest {
        val fetched = item(42, "Fresh")
        givenApiReturns(fetched)

        repository.observeItemDetails(42).toList()

        coVerify(exactly = 1) { watchStateRepository.recordFromServer(listOf(fetched), any()) }
    }

    @Test
    fun getItemDetailsStillReturnsASingleValue() = runTest {
        givenApiReturns(item(42, "Fresh"))

        assertEquals(item(42, "Fresh"), repository.getItemDetails(42))
    }

    @Test
    fun clearDropsBothNamespaces() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()

        repository.clear()

        givenApiReturns(item(42, "Reloaded"))
        assertEquals(item(42, "Reloaded"), repository.getItemDetails(42))
        coVerify(exactly = 2) { api.getItemDetails(42) }
    }

    @Test
    fun getItemDetailsReturnsTheStoredItemWhenRevalidationFails() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        repository.markStale(42)
        coEvery { api.getItemDetails(42) } throws IllegalStateException("network down")

        val result = repository.getItemDetails(42)

        assertEquals(item(42, "Fresh"), result)
    }

    @Test
    fun getItemDetailsThrowsWhenNothingWasEverStoredAndTheLoadFails() = runTest {
        coEvery { api.getItemDetails(42) } throws IllegalStateException("network down")

        assertThrows<IllegalStateException> { repository.getItemDetails(42) }
    }

    @Test
    fun refreshThrowsWhenRevalidationFailsEvenWithAStoredValuePresent() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        coEvery { api.getItemDetails(42) } throws IllegalStateException("network down")

        assertThrows<IllegalStateException> { repository.refresh(42) }
    }

    private fun givenApiReturns(value: Item) {
        coEvery { api.getItemDetails(value.id) } returns Result.success(ApiResponse(item = value))
    }

    private fun item(id: Int, title: String) = Item(id = id, title = title, type = ItemType.MOVIE)

    private class InMemoryPayloadStore : PersistentPayloadStore {
        private val rows = mutableMapOf<String, StoredPayload>()

        override var generation: Long = 0L
            private set

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
            generation += 1
            rows.clear()
        }
    }
}
