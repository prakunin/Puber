package com.kino.puber.core.startup

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.BookmarkFoldersInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import com.kino.puber.util.FakePayloadStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
internal class AppStartupTest {

    private val api = mockk<KinoPubApiClient>(relaxed = true)
    private val navigationPreferences = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val watchState = mockk<WatchStateRepository>(relaxed = true)
    private val contentCache = ContentCacheRepository(FakePayloadStore())
    private val preferences = mockk<ICryptoPreferenceRepository>(relaxed = true)
    private val domain = mockk<ApiDomainInteractor>(relaxed = true)

    private fun homeInteractor() = HomeInteractor(
        api = api,
        watchLaterBookmarkInteractor = mockk<WatchLaterBookmarkInteractor>(relaxed = true),
        bookmarkFolders = BookmarkFoldersInteractor(api, contentCache),
        navigationPreferencesRepository = navigationPreferences,
        watchStateRepository = watchState,
        contentCache = contentCache,
        recentlyPlayedOrder = RecentlyPlayedOrder(api = api, watchState = watchState),
    )

    private fun startup(scope: CoroutineScope, ioDispatcher: CoroutineDispatcher) = AppStartup(
        domainInteractor = { domain },
        preferences = preferences,
        homeInteractor = ::homeInteractor,
        scope = scope,
        ioDispatcher = ioDispatcher,
    )

    private fun TestScope.startup() = startup(this, StandardTestDispatcher(testScheduler))

    private fun signedIn() {
        every { preferences.getAccessToken() } returns "token"
        coEvery { domain.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "api.example", customDomain = null),
            changed = false,
        )
        every { navigationPreferences.contentPreferences } returns
            MutableStateFlow(ContentPreferences(showAnime = true, hideWatched = false, showWatchedIndicators = true))
    }

    private fun stubFeeds(
        hotGate: CompletableDeferred<Unit>? = null,
        watching: suspend () -> List<Item> = { emptyList() },
    ) {
        coEvery { api.getItemsByShortcut("hot", type = "movie") } coAnswers {
            hotGate?.await()
            Result.success(page(item(2)))
        }
        coEvery { api.getItemsByShortcut("hot", type = "serial") } coAnswers {
            hotGate?.await()
            Result.success(page(item(3)))
        }
        coEvery { api.getWatchingList(onlySubscribed = true) } coAnswers {
            Result.success(ApiResponseList(items = watching()))
        }
    }

    @Test
    fun start_appliesTheSavedDomainOffTheMainThread() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "startup-io") }
        try {
            val applied = CompletableDeferred<String>()
            every { domain.initialize() } answers { applied.complete(Thread.currentThread().name); Unit }
            val startup = startup(CoroutineScope(Dispatchers.Default), executor.asCoroutineDispatcher())

            startup.start()

            val thread = withContext(Dispatchers.Default) { withTimeout(5_000) { applied.await() } }
            assertTrue(thread.startsWith("startup-io"), thread)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun warmUpStartsHotAndWatchingWithoutAnyScreen() = runTest {
        signedIn()
        stubFeeds()

        startup().start()
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getItemsByShortcut("hot", type = "movie") }
        coVerify(exactly = 1) { api.getItemsByShortcut("hot", type = "serial") }
        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = true) }
    }

    @Test
    fun warmUpAndHomeShareOneRequestPerEndpoint() = runTest {
        signedIn()
        val gate = CompletableDeferred<Unit>()
        stubFeeds(hotGate = gate, watching = { gate.await(); listOf(item(1)) })

        startup().start()
        runCurrent()
        val home = homeInteractor()
        val hot = mutableListOf<Cached<List<Item>>>()
        val watching = mutableListOf<Cached<List<Item>>>()
        launch { home.observeHotItems().collect { hot += it } }
        launch { home.observeWatchingItems().collect { watching += it } }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.getItemsByShortcut("hot", type = "movie") }
        coVerify(exactly = 1) { api.getItemsByShortcut("hot", type = "serial") }
        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = true) }
        assertEquals(listOf(2, 3), values(hot).map(Item::id).sorted())
        assertEquals(listOf(1), values(watching).map(Item::id))
    }

    @Test
    fun aWarmUpLandingAfterAStoreWipeIsNotServedToHome() = runTest {
        signedIn()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        stubFeeds(watching = {
            calls += 1
            if (calls == 1) {
                gate.await()
                listOf(item(1))
            } else {
                listOf(item(2))
            }
        })

        startup().start()
        runCurrent()
        contentCache.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        val seen = mutableListOf<Cached<List<Item>>>()
        launch { homeInteractor().observeWatchingItems().collect { seen += it } }
        advanceUntilIdle()

        assertEquals(listOf(2), values(seen).map(Item::id))
    }

    @Test
    fun awaitDomainInitialized_completesWhenInitializeThrows() = runTest {
        every { domain.initialize() } throws IllegalStateException("prefs unreadable")
        val startup = startup()

        startup.start()
        runCurrent()
        startup.awaitDomainInitialized()
    }

    @Test
    fun awaitDomainInitialized_waitsUntilTheDomainIsApplied() = runTest {
        val startup = startup()
        var done = false
        launch {
            startup.awaitDomainInitialized()
            done = true
        }
        runCurrent()
        assertTrue(!done)

        startup.start()
        runCurrent()

        assertTrue(done)
    }

    @Test
    fun warmUpRequestsNothingWithoutAnAccessToken() = runTest {
        every { preferences.getAccessToken() } returns null
        stubFeeds()

        startup().start()
        advanceUntilIdle()

        coVerify(exactly = 0) { domain.autoResolveWorkingDomain() }
        coVerify(exactly = 0) { api.getItemsByShortcut(any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.getWatchingList(any()) }
    }

    private fun values(emissions: List<Cached<List<Item>>>): List<Item> =
        emissions.filterIsInstance<Cached.Value<List<Item>>>().last().value

    private fun page(vararg items: Item) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = 1, perpage = items.size, total = 1),
    )

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)
}
