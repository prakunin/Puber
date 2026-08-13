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
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.ui.feature.home.model.HomeAction
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
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun aDomainChangeDropsTheRowsThatBelongedToThePreviousCatalogue() = runTest {
        // The other direction of the rule above. Preserving rows across a load is right for a
        // resume, where the catalogue is the same one; it is wrong for a domain switch, where the
        // rows describe a catalogue the app is no longer talking to. A section whose new request
        // has not answered — or never will — must not keep drawing the old domain's content.
        every { mapper.mapItemSection(any(), HomeSectionType.ContinueWatching) } returns
            stateFor(HomeSectionType.ContinueWatching)
        every { mapper.mapItemSection(any(), HomeSectionType.Hot) } returns stateFor(HomeSectionType.Hot)
        every { mapper.mapItemSection(any(), HomeSectionType.Fresh) } returns stateFor(HomeSectionType.Fresh)
        every { mapper.mapCollectionSection(any()) } returns stateFor(HomeSectionType.Collections)
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(1)), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(item(2)), false))
        every { interactor.observeFreshItems() } returns flowOf(Cached.Value(listOf(item(3)), false))

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, (vm.testStateValue as HomeViewState.Content).sections.size)

        // The next load resolves onto a different domain, and only the watching row answers it.
        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "other.example", customDomain = null),
            changed = true,
        )
        val neverCompletes = CompletableDeferred<Unit>()
        val stillGated = flow<Cached<List<Item>>> { neverCompletes.await() }
        every { interactor.observeHotItems() } returns stillGated
        every { interactor.observeFreshItems() } returns stillGated
        every { interactor.observePopularMovies() } returns stillGated
        every { interactor.observePopularSeries() } returns stillGated
        every { interactor.observeWatchLaterItems() } returns stillGated
        every { interactor.observeBookmarkItems() } returns stillGated
        every { interactor.observeCollections() } returns flow { neverCompletes.await() }
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(4)), false))

        vm.onAction(CommonAction.OnResume)
        runCurrent()

        val types = (vm.testStateValue as HomeViewState.Content).sections.map { it.type }
        assertEquals(listOf(HomeSectionType.ContinueWatching), types)
    }

    @Test
    fun aCacheWipeFromAnotherScreenDropsTheRowsFromThePreviousCatalogue() = runTest {
        // The device settings screen switches the domain and wipes the same caches, with no re-root
        // and no way to tell this screen. Coming back to home then resumes, and the auto-resolve
        // reports changed = false because settings already applied the domain — so the `changed`
        // signal cannot cover this route by construction. The wipe itself has to be what home sees.
        every { interactor.cacheGeneration } returns 0L
        every { mapper.mapItemSection(any(), HomeSectionType.ContinueWatching) } returns
            stateFor(HomeSectionType.ContinueWatching)
        every { mapper.mapItemSection(any(), HomeSectionType.Hot) } returns stateFor(HomeSectionType.Hot)
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(1)), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(item(2)), false))

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, (vm.testStateValue as HomeViewState.Content).sections.size)

        every { interactor.cacheGeneration } returns 1L
        val neverCompletes = CompletableDeferred<Unit>()
        every { interactor.observeHotItems() } returns flow { neverCompletes.await() }
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(3)), false))

        vm.onAction(CommonAction.OnResume)
        runCurrent()

        val types = (vm.testStateValue as HomeViewState.Content).sections.map { it.type }
        assertEquals(listOf(HomeSectionType.ContinueWatching), types)
    }

    @Test
    fun aDomainChangeWhoseSectionsAllFailStopsDrawingThePreviousCatalogue() = runTest {
        // Emptying the row map is not enough on its own: nothing republishes until a section
        // answers, so a switch where none of them ever does would leave the last frame — drawn
        // entirely from the old domain — on screen for as long as the screen lives.
        every { mapper.mapItemSection(any(), HomeSectionType.ContinueWatching) } returns
            stateFor(HomeSectionType.ContinueWatching)
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(1)), false))

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.testStateValue is HomeViewState.Content)

        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.Success(
            state = ApiDomainState(domain = "other.example", customDomain = null),
            changed = true,
        )
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

        vm.onAction(CommonAction.OnResume)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is HomeViewState.Error) {
            "still on ${vm.testStateValue}, which is drawn from the domain the app just left"
        }
    }

    @Test
    fun savingADomainByHandAlsoDropsTheRowsFromThePreviousCatalogue() = runTest {
        // The explicit switches take a different route to the same place: they apply the domain
        // themselves, so the auto-resolve inside the reload that follows reports changed = false and
        // cannot be what clears the rows.
        every { mapper.mapItemSection(any(), HomeSectionType.ContinueWatching) } returns
            stateFor(HomeSectionType.ContinueWatching)
        every { mapper.mapItemSection(any(), HomeSectionType.Hot) } returns stateFor(HomeSectionType.Hot)
        every { interactor.observeWatchingItems(any()) } returns flowOf(Cached.Value(listOf(item(1)), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(item(2)), false))

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, (vm.testStateValue as HomeViewState.Content).sections.size)

        coEvery { apiDomainInteractor.saveCustomDomain("other.example") } returns ApiDomainUpdateResult.Success(
            ApiDomainState(domain = "other.example", customDomain = "other.example")
        )
        val neverCompletes = CompletableDeferred<Unit>()
        every { interactor.observeHotItems() } returns flow { neverCompletes.await() }

        vm.onAction(HomeAction.SaveApiDomain("other.example"))
        runCurrent()

        val types = (vm.testStateValue as HomeViewState.Content).sections.map { it.type }
        assertEquals(listOf(HomeSectionType.ContinueWatching), types)
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

    private fun stateFor(type: HomeSectionType) =
        HomeSectionState(title = type.name, items = emptyList(), type = type)

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
