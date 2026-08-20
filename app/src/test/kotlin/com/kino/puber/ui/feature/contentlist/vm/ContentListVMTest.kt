package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.api.models.TrailerLinksResponse
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.interactor.trailer.TrailerLinkInteractor
import com.kino.puber.ui.feature.contentlist.model.ContentListAction
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.showall.ShowAllScreen
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ContentListVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var refreshCoordinator: ContentListRefreshCoordinator
    private lateinit var interactor: ContentListInteractor
    private lateinit var mapper: VideoItemUIMapper

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        refreshCoordinator = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        coEvery { interactor.getItemDetails(42) } returns Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        every { mapper.mapDetailedItem(any()) } returns VideoDetailsUIState.Loading.copy(id = 42)
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
    fun heroSelected_navigatesForContentChangeResultToDetails() {
        val screen = mockk<PuberScreen>()
        every { screens.details(42) } returns screen
        val vm = createVM()

        vm.onAction(ContentListAction.HeroSelected(42))

        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, any()) }
    }

    @Test
    fun animeHero_loadsAndCapsItemsAtTen() {
        val configs = TabTypeConfig.heroConfigsFor(TabType.Anime)
        val movieItems = (1..6).map { item(it, ratingPercentage = it) }
        val serialItems = (7..12).map { item(it, ratingPercentage = it) }
        val expectedItems = (movieItems + serialItems)
            .sortedByDescending { it.ratingPercentage }
            .take(10)
        val mappedItems = expectedItems.map { heroItem(it.id) }
        coEvery { interactor.loadPage(configs[0], page = 1) } returns page(movieItems)
        coEvery { interactor.loadPage(configs[1], page = 1) } returns page(serialItems)
        every { mapper.mapHeroItems(expectedItems) } returns mappedItems
        val vm = createVM(heroConfigs = configs)

        assertEquals(true, vm.testStateValue.isHeroLoading)
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mappedItems, vm.testStateValue.heroItems)
        assertEquals(false, vm.testStateValue.isHeroLoading)
        verify { mapper.mapHeroItems(expectedItems) }
    }

    @Test
    fun cartoonHero_loadsUsingCartoonConfiguration() {
        val config = TabTypeConfig.heroConfigsFor(TabType.Cartoons)
            .single { it.type == "movie" }
        val items = listOf(item(42))
        val mappedItems = listOf(heroItem(42))
        coEvery { interactor.loadPage(config, page = 1) } returns page(items)
        every { mapper.mapHeroItems(items) } returns mappedItems
        val vm = createVM(heroConfigs = listOf(config))

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mappedItems, vm.testStateValue.heroItems)
        coVerify(exactly = 1) { interactor.loadPage(config, page = 1) }
    }

    @Test
    fun unrelatedTab_doesNotRequestHero() {
        val vm = createVM()

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { interactor.loadPage(any(), any()) }
        assertEquals(emptyList<HeroItemState>(), vm.testStateValue.heroItems)
    }

    @Test
    fun heroFailure_keepsHeroEmptyWithoutReplacingContentState() {
        val config = TabTypeConfig.heroConfigsFor(TabType.Anime).first()
        coEvery { interactor.loadPage(config, page = 1) } throws IllegalStateException("network")
        val vm = createVM(heroConfigs = listOf(config))

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<HeroItemState>(), vm.testStateValue.heroItems)
        assertEquals(false, vm.testStateValue.isHeroLoading)
    }

    @Test
    fun returnedContentChanges_reserveHeroSlotWhileReloadingAfterInitialEmptyResult() {
        val config = TabTypeConfig.heroConfigsFor(TabType.Anime).first()
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val refreshedItem = item(43)
        val refreshedPage = CompletableDeferred<PaginatedResponse<Item>>()
        every { screens.details(42) } returns screen
        var requestCount = 0
        coEvery { interactor.loadPage(config, page = 1) } coAnswers {
            requestCount += 1
            if (requestCount == 1) page(emptyList()) else refreshedPage.await()
        }
        every { mapper.mapHeroItems(any()) } answers {
            firstArg<List<Item>>().map { heroItem(it.id) }
        }
        val vm = createVM(heroConfigs = listOf(config))

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.testStateValue.isHeroLoading)
        assertEquals(emptyList<HeroItemState>(), vm.testStateValue.heroItems)

        vm.onAction(ContentListAction.HeroSelected(42))
        verify {
            router.navigateForResult<ContentChangeSet>(
                screen,
                RESULT_CONTENT_CHANGED,
                capture(listener),
            )
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        assertEquals(true, vm.testStateValue.isHeroLoading)
        assertEquals(emptyList<HeroItemState>(), vm.testStateValue.heroItems)

        refreshedPage.complete(page(listOf(refreshedItem)))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.loadPage(config, page = 1) }
        verifyOrder {
            interactor.invalidateFirstPageCache()
            refreshCoordinator.requestRefresh()
        }
        assertEquals(false, vm.testStateValue.isHeroLoading)
        assertEquals(listOf(heroItem(43)), vm.testStateValue.heroItems)
    }

    @Test
    fun showAll_navigatesForContentChangeResult() {
        val config = SectionConfig(id = "popular", title = "Popular")
        val vm = createVM()

        vm.onAction(ContentListAction.ShowAll(config))

        verify {
            router.navigateForResult<ContentChangeSet>(
                match { it is ShowAllScreen },
                RESULT_CONTENT_CHANGED,
                any(),
            )
        }
    }

    @Test
    fun emptyShowAllResult_consumesListenerWithoutRefreshingSections() {
        val config = SectionConfig(id = "popular", title = "Popular")
        val listener = slot<(ContentChangeSet?) -> Unit>()
        val vm = createVM()
        vm.onAction(ContentListAction.ShowAll(config))
        verify {
            router.navigateForResult<ContentChangeSet>(
                match { it is ShowAllScreen },
                RESULT_CONTENT_CHANGED,
                capture(listener),
            )
        }

        listener.captured(ContentChangeSet.empty())

        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        verify(exactly = 0) { refreshCoordinator.requestRefresh() }
    }

    @Test
    fun returnedChanges_invalidateFirstPageCacheAndRequestSectionRefresh() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        verifyOrder {
            interactor.invalidateFirstPageCache()
            refreshCoordinator.requestRefresh()
        }
    }

    @Test
    fun returnedChangesForFocusedItem_reloadSelectedDetailsFromTheSharedRepository() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(151)
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        mainDispatcher.dispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) { interactor.getItemDetails(42) }
    }

    @Test
    fun returnedMultipleChanges_refreshSectionsOnlyOnce() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(
            ContentChangeSet
                .single(42, ContentChangeType.Watched)
                .merge(ContentChangeSet.single(100, ContentChangeType.Bookmark))
        )

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        verify(exactly = 1) { refreshCoordinator.requestRefresh() }
    }

    @Test
    fun focusHeldForTwoSeconds_publishesTheTrailerUrl() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun focusMovedBeforeTwoSeconds_neverStartsATrailer() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        coEvery { interactor.getItemDetails(43) } returns item(43)
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1500)
        vm.onAction(CommonAction.ItemFocused(videoItem(43)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun autoTrailerDisabled_leavesTheStillInPlace() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = false)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun itemWithoutATrailer_leavesTheStillInPlace() {
        coEvery { interactor.getItemDetails(42) } returns item(42)
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerWithoutAUrl_fallsBackToTheFile() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = null, file = "https://cdn/trailer.file"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/trailer.file", vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerGivenAsABarePath_isExchangedForASignedLink() {
        val api = mockk<KinoPubApiClient> {
            coEvery { getTrailerLinks(42) } returns Result.success(
                TrailerLinksResponse(trailer = listOf(Trailer(id = 42, url = "https://cdn/signed.m3u8")))
            )
        }
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(file = "/trailers/d/02/x.mp4"))
        val vm = createVM(autoTrailerEnabled = true, trailerLinks = TrailerLinkInteractor(api))

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/signed.m3u8", vm.testStateValue.previewTrailerUrl)
        coVerify(exactly = 1) { api.getTrailerLinks(42) }
    }

    @Test
    fun trailerGivenAsABarePath_isNotPlayed() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "/trailers/d/02/d88196ed.mp4"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerPreviewFinished_clearsTheUrlAndDoesNotReplay() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        vm.onAction(ContentListAction.TrailerPreviewFinished)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun openingAnItem_stopsTheTrailerPreview() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        every { screens.details(42) } returns mockk<PuberScreen>()
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        vm.onAction(CommonAction.ItemSelected(videoItem(42)))

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun withoutTheDetailPanel_noTrailerIsEverPublished() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = ContentListVM(
            router = router,
            interactor = interactor,
            mapper = mapper,
            genreInteractor = mockk(relaxed = true),
            trailerLinks = noTrailerLinks(),
            navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
                every { getNavigationMode() } returns NavigationMode.TopTabs
                every { getAutoTrailerEnabled() } returns true
            },
            contentListRefreshCoordinator = refreshCoordinator,
        )
        vm.testOnStart()

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun playingAnItemWhileDetailsAreInFlight_stillPublishesDetailsButNotThePreview() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val detailsResult = CompletableDeferred<Item>()
        coEvery { interactor.getItemDetails(42) } coAnswers { detailsResult.await() }
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(151)

        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }
        assertNull(vm.testStateValue.previewTrailerUrl)

        detailsResult.complete(item(42, trailer = Trailer(url = "https://cdn/trailer.mp4")))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(42, vm.testStateValue.selectedItem.id)
        assertNull(vm.testStateValue.previewTrailerUrl)

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        mainDispatcher.dispatcher.scheduler.runCurrent()

        coVerify(exactly = 2) { interactor.getItemDetails(42) }
    }

    @Test
    fun heroSelected_stopsAPublishedTrailerPreview() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        every { screens.details(42) } returns mockk<PuberScreen>()
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)
        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)

        vm.onAction(ContentListAction.HeroSelected(42))

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun blankTrailerUrl_fallsBackToTheFile() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "", file = "https://cdn/trailer.file"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertEquals("https://cdn/trailer.file", vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun blankTrailerUrlAndFile_leaveTheStillInPlace() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "", file = "   "))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun openingShowAll_stopsAPublishedTrailerPreview() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)
        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)

        vm.onAction(ContentListAction.ShowAll(SectionConfig(id = "popular", title = "Popular")))

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun openingShowAllDuringTheCountdown_neverPublishesTheTrailer() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1000)

        vm.onAction(ContentListAction.ShowAll(SectionConfig(id = "popular", title = "Popular")))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun focusLeavingTheRows_stopsAPublishedTrailerPreview() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)
        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)

        vm.onAction(ContentListAction.TrailerPreviewStopped)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun focusLeavingTheRowsDuringTheCountdown_neverPublishesTheTrailer() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1000)

        vm.onAction(ContentListAction.TrailerPreviewStopped)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun focusLeavingBeforeThePreviewJobStarts_neverPublishesTheTrailer() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        // Do not run the test dispatcher between these actions: this is the window where the
        // DEFAULT-start coroutine has not assigned trailerGateJob yet.
        vm.onAction(ContentListAction.TrailerPreviewStopped)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(42, vm.testStateValue.selectedItem.id)
        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun trailerPreviewFinishedDuringTheCountdown_cancelsTheGate() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1000)

        vm.onAction(ContentListAction.TrailerPreviewFinished)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(5000)

        assertNull(vm.testStateValue.previewTrailerUrl)
    }

    @Test
    fun refocusingTheSameCardAfterAStop_startsAFreshCountdown() {
        coEvery { interactor.getItemDetails(42) } returns
            item(42, trailer = Trailer(url = "https://cdn/trailer.mp4"))
        val vm = createVM(autoTrailerEnabled = true)
        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2001)
        vm.onAction(ContentListAction.TrailerPreviewStopped)

        vm.onAction(CommonAction.ItemFocused(videoItem(42)))
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1999)
        assertNull(vm.testStateValue.previewTrailerUrl)

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(2)
        assertEquals("https://cdn/trailer.mp4", vm.testStateValue.previewTrailerUrl)
    }

    private fun createVM() = ContentListVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        genreInteractor = mockk(relaxed = true),
        trailerLinks = noTrailerLinks(),
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
            every { getNavigationMode() } returns NavigationMode.SideDrawer
        },
        contentListRefreshCoordinator = refreshCoordinator,
    )

    private fun createVM(heroConfigs: List<SectionConfig>) = ContentListVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        genreInteractor = mockk(relaxed = true),
        trailerLinks = noTrailerLinks(),
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
            every { getNavigationMode() } returns NavigationMode.SideDrawer
        },
        contentListRefreshCoordinator = refreshCoordinator,
        heroConfigs = heroConfigs,
    )

    private fun createVM(
        autoTrailerEnabled: Boolean,
        trailerLinks: TrailerLinkInteractor = noTrailerLinks(),
    ) = ContentListVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
        genreInteractor = mockk(relaxed = true),
        trailerLinks = trailerLinks,
        navPrefs = mockk<NavigationPreferencesRepository>(relaxed = true) {
            every { getNavigationMode() } returns NavigationMode.SideDrawer
            every { getAutoTrailerEnabled() } returns autoTrailerEnabled
        },
        contentListRefreshCoordinator = refreshCoordinator,
    )

    /**
     * A resolver that answers only from the item payload. A trailer given as a bare path goes to
     * the API for a signed link, and this stands in for an API that has none to give.
     */
    private fun noTrailerLinks() = TrailerLinkInteractor(
        mockk {
            coEvery { getTrailerLinks(any()) } returns Result.success(TrailerLinksResponse())
        }
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    private fun item(
        id: Int,
        ratingPercentage: Int? = null,
        trailer: Trailer? = null,
    ) = Item(
        id = id,
        title = "Item $id",
        type = ItemType.MOVIE,
        ratingPercentage = ratingPercentage,
        trailer = trailer,
    )

    private fun page(items: List<Item>) = PaginatedResponse(
        items = items,
        pagination = Pagination(current = 1, perpage = items.size, total = 1),
    )

    private fun heroItem(id: Int) = HeroItemState(
        id = id,
        title = "Hero $id",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "",
        genres = "",
    )
}
