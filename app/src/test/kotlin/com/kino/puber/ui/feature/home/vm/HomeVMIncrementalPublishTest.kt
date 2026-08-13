package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeVMIncrementalPublishTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var interactor: HomeInteractor
    private lateinit var mapper: HomeUIMapper
    private lateinit var videoItemMapper: VideoItemUIMapper
    private lateinit var apiDomainInteractor: ApiDomainInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    private val displayChanges = MutableSharedFlow<Unit>()
    private val cardDisplayChanges = mockk<CardDisplayChanges> {
        every { changes } returns this@HomeVMIncrementalPublishTest.displayChanges
    }

    @BeforeEach
    fun setup() {
        router = mockk(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        videoItemMapper = mockk(relaxed = true)
        apiDomainInteractor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }

        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "api.example", customDomain = null),
            changed = false,
        )
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeFreshItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observePopularMovies() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observePopularSeries() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeWatchLaterItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeBookmarkItems() } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeCollections() } returns flowOf(Cached.Value(emptyList(), false))
        every { mapper.mapItemSection(any(), any()) } returns null
        every { mapper.mapCollectionSection(any()) } returns null
        every { videoItemMapper.mapHeroItems(any()) } returns emptyList()
    }

    @Test
    fun theFirstSectionToArrivePublishesContentWithoutWaitingForTheRest() = runTest {
        // The whole point: a stored watching row must not be held behind nine other requests.
        val slowGate = CompletableDeferred<Unit>()
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = false)
        )
        every { interactor.observeHotItems() } returns flow {
            slowGate.await()
            emit(Cached.Value(emptyList(), isStale = false))
        }

        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    @Test
    fun aSectionThatFailsLeavesTheOthersOnScreen() = runTest {
        every { interactor.observeHotItems() } returns flow { throw IllegalStateException("offline") }
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = false)
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    @Test
    fun everySectionFailingWithNothingStoredShowsTheErrorScreen() = runTest {
        listOf<() -> Unit>(
            { every { interactor.observeWatchingItems(any()) } returns failing() },
            { every { interactor.observeHotItems() } returns failing() },
            { every { interactor.observeFreshItems() } returns failing() },
            { every { interactor.observePopularMovies() } returns failing() },
            { every { interactor.observePopularSeries() } returns failing() },
            { every { interactor.observeWatchLaterItems() } returns failing() },
            { every { interactor.observeBookmarkItems() } returns failing() },
            { every { interactor.observeCollections() } returns failingCollections() },
        ).forEach { it() }

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Error)
    }

    @Test
    fun resumeForcesOnlyTheWatchingRow() = runTest {
        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(CommonAction.OnResume)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.observeWatchingItems(true) }
        verify(exactly = 2) { interactor.observeHotItems() }
    }

    @Test
    fun aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished() = runTest {
        every { interactor.observeWatchingItems(any()) } returns flowOf(
            Cached.Value(listOf(item(1)), isStale = true),
            Cached.RefreshFailed(IllegalStateException("offline")),
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Content)
        verify { mapper.mapItemSection(listOf(item(1)), HomeSectionType.ContinueWatching) }
    }

    private fun failing() = flow<Cached<List<Item>>> { throw IllegalStateException("offline") }

    private fun failingCollections() =
        flow<Cached<List<com.kino.puber.data.api.models.KCollection>>> {
            throw IllegalStateException("offline")
        }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun createVM() = HomeVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        videoItemMapper = videoItemMapper,
        apiDomainInteractor = apiDomainInteractor,
        savedItemInteractor = savedItemInteractor,
        cardDisplayChanges = cardDisplayChanges,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )
}
