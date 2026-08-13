package com.kino.puber.data.repository

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ItemDetailsRepositoryTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchStateRepository = mockk<WatchStateRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()
    private val repository = ItemDetailsRepository(
        api = api,
        watchStateRepository = watchStateRepository,
        store = store,
    )

    @Test
    fun observeItemDetailsEmitsOnceWhenNothingIsStored() = runTest {
        givenApiReturns(item(42, "Fresh"))

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false)), emissions)
    }

    @Test
    fun observeItemDetailsServesTheStoredItemOnASecondOpen() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()

        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Fresh"), isStale = false)), emissions)
        coVerify(exactly = 1) { api.getItemDetails(42) }
    }

    @Test
    fun markStaleKeepsServingTheItemButRevalidates() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.markStale(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(Cached.Value(item(42, "Fresh"), isStale = true), emissions[0])
        assertEquals(Cached.Value(item(42, "Newer"), isStale = false), emissions[1])
    }

    @Test
    fun invalidateRemovesTheItemSoTheNextOpenWaits() = runTest {
        givenApiReturns(item(42, "Fresh"))
        repository.observeItemDetails(42).toList()
        givenApiReturns(item(42, "Newer"))

        repository.invalidate(42)
        val emissions = repository.observeItemDetails(42).toList()

        assertEquals(listOf(Cached.Value(item(42, "Newer"), isStale = false)), emissions)
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

        assertEquals(null, store.read("item:42"))
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

    private fun givenApiReturns(value: Item) {
        coEvery { api.getItemDetails(value.id) } returns Result.success(ApiResponse(item = value))
    }

    private fun item(id: Int, title: String) = Item(id = id, title = title, type = ItemType.MOVIE)

    private class InMemoryPayloadStore : PersistentPayloadStore {
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
