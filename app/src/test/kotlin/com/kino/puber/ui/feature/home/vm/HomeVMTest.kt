package com.kino.puber.ui.feature.home.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class HomeVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: HomeInteractor
    private lateinit var mapper: HomeUIMapper
    private lateinit var videoItemMapper: VideoItemUIMapper
    private lateinit var apiDomainInteractor: ApiDomainInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
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
        every { interactor.observeWatchLaterItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeBookmarkItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.observeCollections() } returns flowOf(Cached.Value(emptyList(), false))
        coEvery { interactor.lastWatchedAt() } returns emptyMap()
        every { interactor.prepareHomeItems(any(), any(), any()) } answers { firstArg() }
        every { mapper.mapItemSection(any(), any()) } returns null
        every { mapper.mapCollectionSection(any()) } returns null
        every { videoItemMapper.mapHeroItems(any()) } returns emptyList()
    }

    @Test
    fun itemSelected_navigatesForContentChangeResultToDetails() {
        val screen = mockk<PuberScreen>()
        every { screens.details(42) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemSelected(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun itemPlayed_navigatesForContentChangeResultToPlayer() {
        val screen = mockk<PuberScreen>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()

        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun watchStateSettling_remapsTheCardsWithoutAskingTheServerAgain() = runTest {
        // A watched mark changes how a card is drawn, not what the server would return. Reloading
        // every section for it would cost a round of requests per mark.
        createVM().also { it.testOnStart() }
        // Sections now publish as they arrive, so the initial load alone maps Fresh more than
        // once (each later section's arrival re-publishes everything loaded so far). Clear the
        // recorded calls once that settles so the assertion below is only about the remap that
        // the display-change event itself causes, not an internal implementation detail of how
        // many sections happened to be in flight.
        clearMocks(mapper, answers = false, recordedCalls = true, verificationMarks = true)

        displayChanges.emit(Unit)
        runCurrent()

        // Mapped again from what was already loaded, with nothing asked of the server.
        verify(exactly = 1) { mapper.mapItemSection(any(), HomeSectionType.Fresh) }
        coVerify(exactly = 1) { apiDomainInteractor.autoResolveWorkingDomain() }
        verify(exactly = 1) { interactor.observeWatchingItems(false) }
    }

    @Test
    fun returnedChanges_refreshContentStateSilently() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 2) { apiDomainInteractor.autoResolveWorkingDomain() }
    }

    @Test
    fun returnedBookmarkChange_refreshesOnlyPersonalBookmarkRows() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Bookmark))

        verify(exactly = 1) { interactor.observeWatchLaterItems(true) }
        verify(exactly = 1) { interactor.observeBookmarkItems(true) }
        verify(exactly = 0) { interactor.observeWatchingItems(true) }
    }

    @Test
    fun returnedWatchlistChange_refreshesOnlyContinueWatchingRow() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watchlist))

        verify(exactly = 1) { interactor.observeWatchingItems(true) }
        verify(exactly = 0) { interactor.observeWatchLaterItems(true) }
        verify(exactly = 0) { interactor.observeBookmarkItems(true) }
    }

    @Test
    fun successfulMovieSave_refreshesPersonalBookmarkRows() {
        coEvery { savedItemInteractor.setSaved(42, false, true) } returns Result.success(true)
        val vm = createVM().also { it.testOnStart() }

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))

        verify(exactly = 1) { interactor.observeWatchLaterItems(true) }
        verify(exactly = 1) { interactor.observeBookmarkItems(true) }
        verify(exactly = 0) { interactor.observeWatchingItems(true) }
    }

    @Test
    fun collectionChanges_areReturnedToHome() {
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val vm = createVM().also { it.testOnStart() }

        vm.onCollectionClick(7, "Collection")

        verify {
            router.navigateForResult<ContentChangeSet>(
                match { screen -> screen.key == "CollectionDetailScreen_7" },
                RESULT_CONTENT_CHANGED,
                capture(listener),
            )
        }
        listener.captured(ContentChangeSet.single(42, ContentChangeType.Bookmark))
        verify(exactly = 1) { interactor.observeWatchLaterItems(true) }
        verify(exactly = 1) { interactor.observeBookmarkItems(true) }
    }

    @Test
    fun returnedChangesAndResume_keepOnlyLatestRefreshRunning() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val refreshGate = CompletableDeferred<Unit>()
        var resolveCalls = 0
        every { screens.details(42) } returns screen
        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } coAnswers {
            resolveCalls += 1
            if (resolveCalls > 1) {
                refreshGate.await()
            }
            ApiDomainAutoResolveResult.Success(
                state = ApiDomainState(domain = "api.example", customDomain = null),
                changed = false,
            )
        }
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onAction(CommonAction.OnResume)
        refreshGate.complete(Unit)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 2) { interactor.observeWatchingItems(any()) }
    }

    @Test
    fun filteredHotFeedsHeroAndHotSectionWhilePersonalWatchingItemsRemainUnchanged() {
        val filteredHotItem = item(1)
        val personalWatchingItem = item(2)
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(filteredHotItem), false))
        every { interactor.observeWatchingItems(any()) } returns
            flowOf(Cached.Value(listOf(personalWatchingItem), false))
        val vm = createVM()

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify { videoItemMapper.mapHeroItems(listOf(filteredHotItem)) }
        verify { mapper.mapItemSection(listOf(filteredHotItem), HomeSectionType.Hot) }
        verify { mapper.mapItemSection(listOf(personalWatchingItem), HomeSectionType.ContinueWatching) }
    }

    @Test
    fun homePresentation_sortsOnlyBookmarkRowsByLastWatched() {
        val watchLater = item(1)
        val bookmark = item(2)
        val hot = item(3)
        val lastWatched = mapOf(bookmark.id to 200L, watchLater.id to 100L)
        coEvery { interactor.lastWatchedAt() } returns lastWatched
        every { interactor.observeWatchLaterItems(any()) } returns flowOf(Cached.Value(listOf(watchLater), false))
        every { interactor.observeBookmarkItems(any()) } returns flowOf(Cached.Value(listOf(bookmark), false))
        every { interactor.observeHotItems() } returns flowOf(Cached.Value(listOf(hot), false))

        createVM().testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify { interactor.prepareHomeItems(listOf(watchLater), lastWatched, true) }
        verify { interactor.prepareHomeItems(listOf(bookmark), lastWatched, true) }
        verify { interactor.prepareHomeItems(listOf(hot), lastWatched, false) }
    }

    private val displayChanges = MutableSharedFlow<Unit>()
    private val cardDisplayChanges = mockk<CardDisplayChanges> {
        every { changes } returns this@HomeVMTest.displayChanges
    }

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

    private fun videoItem(id: Int) = VideoItemUIState(
        id = id,
        title = "Item $id",
        imageUrl = "",
        bigImageUrl = "",
    )

    private fun item(id: Int) = Item(
        id = id,
        title = "Item $id",
        type = ItemType.MOVIE,
    )
}
