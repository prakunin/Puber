package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
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
    fun resumingNeverPublishesFewerSectionsThanWereAlreadyOnScreen() = runTest {
        // The regression this guards: clearing the accumulated rows at the start of a resume,
        // before any of that resume's own flows have answered, drops the screen down to whichever
        // row happens to answer first and re-grows it from there — moving focus off whatever row
        // the user was already on. Rows must only ever be replaced in place, never zeroed out.
        fun stateFor(type: HomeSectionType) = HomeSectionState(title = type.name, items = emptyList(), type = type)
        every { mapper.mapItemSection(any(), HomeSectionType.ContinueWatching) } returns
            stateFor(HomeSectionType.ContinueWatching)
        every { mapper.mapItemSection(any(), HomeSectionType.Hot) } returns stateFor(HomeSectionType.Hot)
        every { mapper.mapItemSection(any(), HomeSectionType.Fresh) } returns stateFor(HomeSectionType.Fresh)
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(1)), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(item(2)), false))
        every { interactor.observeFreshItems() } returns flowOf(Cached.Value(listOf(item(3)), false))

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        val initialSize = (vm.testStateValue as HomeViewState.Content).sections.size
        assertTrue(initialSize >= 3)

        // Record the state as it stood just before every subsequent publish, and gate the rest of
        // the resume's sections on a Deferred that never completes in this test, so the recorded
        // trace covers exactly the window a resume opens before every row has answered again.
        val recordedSizes = mutableListOf<Int>()
        every { videoItemMapper.mapHeroItems(any()) } answers {
            recordedSizes += (vm.testStateValue as? HomeViewState.Content)?.sections?.size ?: 0
            emptyList()
        }
        val neverCompletes = CompletableDeferred<Unit>()
        val stillGated = flow<Cached<List<Item>>> { neverCompletes.await() }
        every { interactor.observePopularMovies() } returns stillGated
        every { interactor.observePopularSeries() } returns stillGated
        every { interactor.observeWatchLaterItems() } returns stillGated
        every { interactor.observeBookmarkItems() } returns stillGated
        every { interactor.observeCollections() } returns
            flow { neverCompletes.await() }

        vm.onAction(CommonAction.OnResume)
        runCurrent()

        assertTrue(
            recordedSizes.all { it >= initialSize },
        ) {
            "sections.size dipped to ${recordedSizes.minOrNull()} during resume, below the " +
                "$initialSize rows already on screen: $recordedSizes"
        }
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
        flow<Cached<List<KCollection>>> {
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
