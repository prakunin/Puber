package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.StoredPayload
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeInteractorCacheTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>(relaxed = true)
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val store = InMemoryPayloadStore()

    private val interactor = HomeInteractor(
        api = api,
        watchLaterBookmarkInteractor = watchLaterBookmarkInteractor,
        navigationPreferencesRepository = navigationPreferencesRepository,
        store = store,
    )

    @Test
    fun theWatchingRowIsServedFromStorageOnASecondCall() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems().toList()

        assertEquals(listOf(Cached.Value(listOf(item(1)), isStale = false)), emissions)
        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun forcingTheWatchingRowServesStorageThenRevalidates() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )
        interactor.observeWatchingItems().toList()

        val emissions = interactor.observeWatchingItems(force = true).toList()

        assertEquals(Cached.Value(listOf(item(1)), isStale = true), emissions[0])
        assertEquals(Cached.Value(listOf(item(1)), isStale = false), emissions[1])
        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun theWatchingRowAndTheHotRowUseSeparateKeys() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = true) } returns Result.success(
            com.kino.puber.data.api.models.ApiResponseList(items = listOf(item(1)))
        )

        interactor.observeWatchingItems().toList()

        assertEquals(null, store.read("home:hot"))
        assertEquals(true, store.read("home:continue_watching") != null)
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

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
