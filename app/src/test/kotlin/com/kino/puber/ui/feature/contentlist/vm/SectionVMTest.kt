package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

class SectionVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @Test
    fun coordinatorRefresh_restartsPagingWithoutClearingSharedCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sideEffects = mutableListOf<Paginator.SideEffect>()
        val sideEffectCollector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            paginator.sideEffects.collect(sideEffects::add)
        }
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        val vm = createVM(paginator, config("popular"), interactor, coordinator, dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        sideEffects.clear()

        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        assertEquals(listOf(Paginator.SideEffect.LoadFirstPage), sideEffects)
        sideEffectCollector.cancel()
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun coordinatorSubscriber_createdBeforeRefreshReceivesItWhenCollectionStartsAfter() = runTest {
        val coordinator = ContentListRefreshCoordinator()
        val refreshRequests = coordinator.refreshRequests()

        coordinator.requestRefresh()

        assertEquals(SectionRefresh.All, withTimeout(1_000) { refreshRequests.first() })
    }

    @Test
    fun directSavedChange_restartsOnlyTheSectionsShowingThatItem() = runTest {
        // The saved flag is baked into the item payload, so a row showing the same title has to be
        // re-read to draw the new badge. A row that never held it has nothing to redraw, and every
        // section on the tab used to re-page for one button press.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val holdingPaginator = paginator(dispatcher)
        val unrelatedPaginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val holdingConfig = config("popular")
        val unrelatedConfig = config("fresh")
        coEvery { interactor.loadPage(holdingConfig, page = 1) } returns page(item(42))
        coEvery { interactor.loadPage(unrelatedConfig, page = 1) } returns page(item(7))
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val holding = createVM(
            paginator = holdingPaginator,
            config = holdingConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
            mapper = mapperFor(42, 7),
        )
        val unrelated = createVM(
            paginator = unrelatedPaginator,
            config = unrelatedConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
            mapper = mapperFor(42, 7),
        )
        holding.testOnStart()
        unrelated.testOnStart()
        testScheduler.advanceUntilIdle()

        holding.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        coVerify(exactly = 2) { interactor.loadPage(holdingConfig, page = 1) }
        coVerify(exactly = 1) { interactor.loadPage(unrelatedConfig, page = 1) }
        holding.testCancelScope()
        unrelated.testCancelScope()
        holdingPaginator.close()
        unrelatedPaginator.close()
    }

    /**
     * Returning from a details or player screen can carry changes of any kind, including ones that
     * decide membership, so that route still reloads every row.
     */
    @Test
    fun coordinatorRefresh_stillRestartsEverySection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("popular")
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item(7))
        val vm = createVM(paginator, sectionConfig, interactor, coordinator, dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.loadPage(sectionConfig, page = 1) }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_publishesInteractorItemsWithoutAdditionalFiltering() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>()
        val mapper = mockk<VideoItemUIMapper>()
        val coordinator = ContentListRefreshCoordinator()
        val item = Item(
            id = 25,
            title = "Interactor result",
            type = ItemType.MOVIE,
            genres = listOf(Genre(ANIME_GENRE_ID, "Anime")),
        )
        val mappedItem = videoItem(25)
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item)
        every { interactor.displaySettingsChanges } returns emptyFlow()
        every { interactor.watchStateChanges } returns emptyFlow()
        every { mapper.mapShortItemList(listOf(item)) } returns listOf(mappedItem)
        val vm = createVM(
            paginator = paginator,
            config = config("anime"),
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapper,
        )

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(SectionState.Content(listOf(mappedItem)), vm.testStateValue)
        verify(exactly = 1) { mapper.mapShortItemList(listOf(item)) }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aPageEmptiedByFilteringIsNotTheEndOfTheList() = runTest {
        // Hiding watched titles can blank a whole server page. Reading that as end-of-list left the
        // section showing "empty" with nothing that would ever ask for the pages behind it.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val visible = item(id = 7)
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage(current = 1, total = 3)
        coEvery { interactor.loadPage(any(), page = 2) } returns page(visible, current = 2, total = 3)
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.loadPage(any(), page = 2) }
        assertEquals(listOf(visible), (paginatorState(paginator) as Paginator.State.Data<*>).data)
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun blankPagesAreNotWalkedInOneBurst() = runTest {
        // Every step already costs the interactor several server pages, so one burst has a ceiling
        // even while the server keeps reporting more.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        coEvery { interactor.loadPage(any(), any()) } answers {
            emptyPage(current = secondArg<Int>(), total = 100)
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)

        vm.testOnStart()
        // Runs everything that is due now, which stops short of the paused continuation.
        testScheduler.runCurrent()

        coVerify(atMost = maxEmptyPageChainUnderTest + 1) { interactor.loadPage(any(), any()) }
        assertEquals(Paginator.State.Empty, paginatorState(paginator))
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aWalkThatSpentItsBudgetResumesWhereItStopped() = runTest {
        // An empty row is hidden, so there is no retry to press and no way into "show all": a run
        // of watched titles longer than one budget would leave the section blank for good, even
        // with unwatched titles on the pages behind it.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val visible = item(id = 7)
        val firstPageWithSomethingLeft = maxEmptyPageChainUnderTest + 2
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            if (requested < firstPageWithSomethingLeft) {
                emptyPage(current = requested, total = 100)
            } else {
                page(visible, current = requested, total = 100)
            }
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)

        vm.testOnStart()
        testScheduler.runCurrent()
        assertEquals(Paginator.State.Empty, paginatorState(paginator))

        testScheduler.advanceUntilIdle()

        // Picked up on the page after the one the budget ran out on, rather than starting over.
        coVerify(exactly = 1) { interactor.loadPage(any(), page = firstPageWithSomethingLeft) }
        coVerify(exactly = 1) { interactor.loadPage(any(), page = 1) }
        assertEquals(listOf(visible), (paginatorState(paginator) as Paginator.State.Data<*>).data)
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aWalkThatKeepsFindingNothingStopsInsteadOfReadingTheCatalogue() = runTest {
        // Every section on the screen walks at once, so the rounds need a ceiling of their own:
        // without one, a heavily watched account has several lists reading their way to the end of
        // the catalogue in parallel, against a request budget the user needs for opening a title.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        var pagesRead = 0
        coEvery { interactor.loadPage(any(), any()) } answers {
            pagesRead++
            emptyPage(current = secondArg<Int>(), total = 10_000)
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        val pagesPerRound = maxEmptyPageChainUnderTest + 1
        assertEquals(pagesPerRound * (maxResumeRoundsUnderTest + 1), pagesRead)
        // And it stays stopped rather than waking up again on the next pause.
        testScheduler.advanceTimeBy(walkResumePauseUnderTest * 10)
        testScheduler.advanceUntilIdle()
        assertEquals(pagesPerRound * (maxResumeRoundsUnderTest + 1), pagesRead)
        assertEquals(Paginator.State.Empty, paginatorState(paginator))
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aPageWithSomethingOnItGivesTheWalkItsRoundsBack() = runTest {
        // The ceiling bounds one fruitless stretch, not the section: a stretch that ended with
        // items must not leave a later one with a spent budget.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val visible = item(id = 7)
        // Far enough in that the walk can only reach it with a full budget it has already spent
        // once: one round short of the ceiling, then a page with an item, then the same again.
        val pagesPerRound = maxEmptyPageChainUnderTest + 1
        val pageWithItem = pagesPerRound * maxResumeRoundsUnderTest
        val lastPageRead = pageWithItem + pagesPerRound * maxResumeRoundsUnderTest
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            if (requested == pageWithItem) {
                page(visible, current = requested, total = 10_000)
            } else {
                emptyPage(current = requested, total = 10_000)
            }
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)

        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()

        // The stretch after the item got a full set of rounds rather than none.
        coVerify(exactly = 1) { interactor.loadPage(any(), page = lastPageRead) }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aPausedWalkIsDroppedWhenPagingRestarts() = runTest {
        // The pause is where a restart has to be able to take the walk out. Two walks waking up
        // side by side would read the same pages twice and interleave what they publish.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val pagesRead = mutableListOf<Int>()
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            pagesRead += requested
            emptyPage(current = requested, total = 100)
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)
        vm.testOnStart()
        testScheduler.runCurrent()

        vm.refreshFirstPage()
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(walkResumePauseUnderTest + 1)
        testScheduler.runCurrent()

        // The page the first walk was paused on is read once — by the restarted walk alone.
        val firstResumedPage = maxEmptyPageChainUnderTest + 2
        assertEquals(1, pagesRead.count { it == firstResumedPage })
        vm.testCancelScope()
        paginator.close()
    }

    /** The store publishes through [Paginator.Store.render], which replays the current state. */
    private fun paginatorState(paginator: Paginator.Store<Item>): Paginator.State {
        lateinit var current: Paginator.State
        paginator.render = { current = it }
        return current
    }

    /**
     * With watched titles shown, the index decides how a card is *drawn*, not which cards the list
     * contains — so the rows already fetched are still the right rows. Re-paging them costs a
     * request per open section for a change a re-map covers, and every section on the screen reacts
     * to the same signal at once.
     */
    @Test
    fun watchStateChange_redrawsTheLoadedRowsWithoutRefetchingThem() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val watchStateChanges = MutableSharedFlow<Long>()
        every { interactor.watchStateChanges } returns watchStateChanges
        every { interactor.hideWatchedEnabled } returns false
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item(1))
        val mapper = mockk<VideoItemUIMapper>(relaxed = true)
        every { mapper.mapShortItemList(any()) } returns listOf(videoItem(1))
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapper,
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        every { mapper.mapShortItemList(any()) } returns listOf(videoItem(1).copy(title = "Watched"))

        watchStateChanges.emit(1L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { interactor.loadPage(any(), page = 1) }
        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        assertEquals(
            listOf("Watched"),
            (vm.testStateValue as SectionState.Content).items.map { it.title },
        )
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * With watched titles hidden the index decides membership, so a re-map cannot answer it: rows
     * that have just become watched have to leave the list, which only a re-page can do.
     */
    @Test
    fun watchStateChange_stillRepagesWhenWatchedTitlesAreHidden() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val watchStateChanges = MutableSharedFlow<Long>()
        every { interactor.watchStateChanges } returns watchStateChanges
        every { interactor.hideWatchedEnabled } returns true
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item(1))
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        watchStateChanges.emit(1L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.loadPage(any(), page = 1) }
        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        vm.testCancelScope()
        paginator.close()
    }

    private fun createVM(
        paginator: Paginator.Store<Item>,
        config: SectionConfig,
        interactor: ContentListInteractor,
        coordinator: ContentListRefreshCoordinator,
        pagingCoroutineContext: CoroutineContext,
        savedItemInteractor: SavedItemInteractor = mockk(relaxed = true),
        mapper: VideoItemUIMapper = mockk(relaxed = true),
    ) = SectionVM(
        paginator = paginator,
        config = config,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = mockk<AppRouter>(relaxed = true),
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
        contentListRefreshCoordinator = coordinator,
        pagingCoroutineContext = pagingCoroutineContext,
    )

    private fun paginator(coroutineContext: CoroutineContext) = Paginator.Store<Item>(
        comparator = { old, new -> old.id == new.id },
        coroutineContext = coroutineContext,
    )

    private fun config(id: String) = SectionConfig(
        id = id,
        title = id,
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    /** A mapper that preserves ids, so a section can tell whether it is holding a given item. */
    private fun mapperFor(vararg ids: Int) = mockk<VideoItemUIMapper>(relaxed = true).also { mapper ->
        every { mapper.mapShortItemList(any()) } answers {
            firstArg<List<Item>>().filter { it.id in ids }.map { videoItem(it.id) }
        }
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    /** Mirrors ContentListPagingVM.MAX_EMPTY_PAGE_CHAIN. */
    private val maxEmptyPageChainUnderTest = 3

    /** Mirrors ContentListPagingVM.MAX_RESUME_ROUNDS. */
    private val maxResumeRoundsUnderTest = 3

    /** Mirrors ContentListPagingVM.WALK_RESUME_PAUSE, in milliseconds. */
    private val walkResumePauseUnderTest = 500L

    private fun emptyPage(
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse<Item>(
        items = emptyList(),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )

    private fun page(
        item: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )
}
