package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorEntity
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
import com.kino.puber.data.cache.Cached
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext

/**
 * Shared setup for the section view model's tests. They live in more than one class because the view
 * model answers two separate questions — how a section pages and walks past pages emptied by
 * filtering, and how it draws a first page that arrives from the store before the server — and one
 * class holding both had outgrown what anyone can read at once.
 */
internal open class SectionVMTestFixture {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    /** The store publishes through [Paginator.Store.render], which replays the current state. */
    protected fun paginatorState(paginator: Paginator.Store<Item>): Paginator.State {
        lateinit var current: Paginator.State
        paginator.render = { current = it }
        return current
    }

    /**
     * Wires the cached-first-page flow onto the page loads these tests stub: one fresh emission per
     * subscription, which is what the interactor's own loader produces when nothing is stored.
     */
    protected fun servesFirstPageFromTheNetwork(interactor: ContentListInteractor) {
        every { interactor.observeFirstPage(any(), any()) } answers {
            val config = firstArg<SectionConfig>()
            flow { emit(Cached.Value(interactor.loadPage(config, page = 1), isStale = false)) }
        }
    }

    /**
     * An error handler that maps and forwards instead of swallowing, which is what the paginator's
     * error state needs to be reachable at all.
     */
    protected fun mappingErrorHandler(): ErrorHandler = mockk<ErrorHandler>(relaxed = true).also { handler ->
        every { handler.map(any()) } answers {
            ErrorEntity(message = firstArg<Throwable>().message.orEmpty(), code = "test")
        }
        every { handler.proceed(any()) } answers {
            val action = firstArg<((ErrorEntity) -> Unit)?>()
            val consumer: (Throwable) -> Unit = { error -> action?.invoke(handler.map(error)) }
            consumer
        }
    }

    protected fun createVM(
        paginator: Paginator.Store<Item>,
        config: SectionConfig,
        interactor: ContentListInteractor,
        coordinator: ContentListRefreshCoordinator,
        pagingCoroutineContext: CoroutineContext,
        savedItemInteractor: SavedItemInteractor = mockk(relaxed = true),
        mapper: VideoItemUIMapper = mockk(relaxed = true),
        errorHandler: ErrorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
    ) = SectionVM(
        paginator = paginator,
        config = config,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = mockk<AppRouter>(relaxed = true),
        errorHandler = errorHandler,
        contentListRefreshCoordinator = coordinator,
        pagingCoroutineContext = pagingCoroutineContext,
    )

    protected fun paginator(coroutineContext: CoroutineContext) = Paginator.Store<Item>(
        comparator = { old, new -> old.id == new.id },
        coroutineContext = coroutineContext,
    )

    protected fun config(id: String) = SectionConfig(
        id = id,
        title = id,
    )

    protected fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    /** A mapper that preserves ids, so a section can tell whether it is holding a given item. */
    protected fun mapperFor(vararg ids: Int) = mockk<VideoItemUIMapper>(relaxed = true).also { mapper ->
        every { mapper.mapShortItemList(any()) } answers {
            firstArg<List<Item>>().filter { it.id in ids }.map { videoItem(it.id) }
        }
    }

    protected fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    /** Mirrors ContentListPagingVM.MAX_EMPTY_PAGE_CHAIN. */
    protected val maxEmptyPageChainUnderTest = 3

    /** Mirrors ContentListPagingVM.MAX_RESUME_ROUNDS. */
    protected val maxResumeRoundsUnderTest = 3

    /** Mirrors ContentListPagingVM.WALK_RESUME_PAUSE, in milliseconds. */
    protected val walkResumePauseUnderTest = 500L

    protected fun emptyPage(
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse<Item>(
        items = emptyList(),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )

    protected fun page(
        item: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )
}

internal class SectionVMTest : SectionVMTestFixture() {

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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
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
        servesFirstPageFromTheNetwork(interactor)
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        watchStateChanges.emit(1L)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.loadPage(any(), page = 1) }
        // Re-paging is the section's whole part in this. The stored page was filtered against the
        // index this signal has just moved, and the cache answers that move by revalidating — a
        // section dropping anything here would land on the reloads every other open section started
        // off this same signal.
        verify(exactly = 0) { interactor.invalidateFirstPageCache() }
        vm.testCancelScope()
        paginator.close()
    }
}

/** The stale-while-revalidate half: a stored first page, the fresh one behind it, and the demand that
 * guarantees the second. */
internal class SectionVMFirstPageTest : SectionVMTestFixture() {

    /**
     * A refresh is one of the signals that knows the server's answer has changed, so it may not
     * settle for whatever the store happens to hold: the stored page is still drawn, and the request
     * behind it is guaranteed. Opening the section is not such a signal, and asks for nothing when
     * the entry is fresh.
     */
    @Test
    fun refresh_forcesTheFirstPageRead_whereOpeningTheSectionDoesNot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("popular")
        val forced = mutableListOf<Boolean>()
        every { interactor.observeFirstPage(any(), any()) } answers {
            forced += secondArg<Boolean>()
            flow { emit(Cached.Value(page(item(7)), isStale = false)) }
        }
        val vm = createVM(paginator, sectionConfig, interactor, coordinator, dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        vm.onAction(CommonAction.RetryClicked)
        testScheduler.advanceUntilIdle()
        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        // The whole sequence, not just the forced reads: the load that opens the section must not
        // force, and one signal must not leave a demand behind that forces a load after it.
        assertEquals(listOf(false, true, true), forced)
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * `resetPaging` restarts through the paginator, so the load a refresh belongs to only starts after
     * a round trip through the store's dispatcher. A second signal arriving inside that window
     * restarts again and cancels the first load — and the guarantee must travel with the load that
     * survives rather than with the one that was cancelled, or the row serves stored pages for the
     * rest of the TTL after an event that was supposed to reach the server.
     */
    @Test
    fun refresh_keepsForcingWhenItsOwnLoadIsCancelledByTheNextRefresh() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val sectionConfig = config("popular")
        val forced = mutableListOf<Boolean>()
        val release = CompletableDeferred<Unit>()
        every { interactor.observeFirstPage(any(), any()) } answers {
            forced += secondArg<Boolean>()
            flow {
                release.await()
                emit(Cached.Value(page(item(7)), isStale = false))
            }
        }
        val vm = createVM(paginator, sectionConfig, interactor, ContentListRefreshCoordinator(), dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        vm.refreshFirstPage()
        vm.refreshFirstPage()
        testScheduler.advanceUntilIdle()
        release.complete(Unit)
        testScheduler.advanceUntilIdle()

        // How many loads the two restarts produce is the paginator's business; what matters is that
        // the opening one is the only unforced load, so whichever survives carries the guarantee.
        assertTrue(forced.size > 1, "the refreshes produced no load at all")
        assertEquals(false, forced.first())
        assertEquals(List(forced.size - 1) { true }, forced.drop(1))
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_drawsTheCachedPageThenTheFreshOneWithNoLoadingInBetween() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1, 2),
        )
        val states = mutableListOf<SectionState>()
        // Unconfined so the recorder runs the moment a state is published. Queued behind the
        // scheduler it would sample the state flow instead, and a conflated sequence cannot answer
        // what appeared between two publications — which is the whole claim under test.
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.testStateFlow.collect(states::add)
        }
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(page(item(1)), isStale = true))
        testScheduler.advanceUntilIdle()
        emissions.emit(Cached.Value(page(item(2)), isStale = false))
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(1, 2),
            states.filterIsInstance<SectionState.Content>().map { it.items.single().id },
        )
        val firstContentAt = states.indexOfFirst { it is SectionState.Content }
        assertEquals(
            emptyList<SectionState>(),
            states.drop(firstContentAt).filterIsInstance<SectionState.Loading>(),
        )
        collector.cancel()
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_aFailedBackgroundRefreshLeavesTheCachedContentStanding() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(page(item(1)), isStale = true))
        testScheduler.advanceUntilIdle()
        emissions.emit(Cached.RefreshFailed(IllegalStateException("network")))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), (vm.testStateValue as SectionState.Content).items.map { it.id })
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * The two publications of one load share a collection, so nothing restarts the paginator between
     * them and nothing cancels the walk the cached page started. Its outstanding page therefore lands
     * on a list that has already been replaced, where it must be dropped rather than appended —
     * otherwise the row shows a page the fresh list never asked for and pages on from the wrong
     * cursor.
     */
    @Test
    fun firstPage_aWalkStartedByTheCachedPageCannotPublishOntoTheFreshOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        val releaseSecondPage = CompletableDeferred<PaginatedResponse<Item>>()
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        coEvery { interactor.loadPage(any(), page = 2) } coAnswers { releaseSecondPage.await() }
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1, 2),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        // The stored page came back emptied by filtering, so the section walks on to page two.
        emissions.emit(Cached.Value(emptyPage(current = 1, total = 9), isStale = true))
        testScheduler.advanceUntilIdle()
        // The fresh page arrives while that request is still outstanding, and only then does it answer.
        emissions.emit(Cached.Value(page(item(1), current = 1, total = 9), isStale = false))
        testScheduler.advanceUntilIdle()
        releaseSecondPage.complete(page(item(2), current = 2, total = 9))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), (vm.testStateValue as SectionState.Content).items.map { it.id })
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()
        // The fresh list pages on from its own page one rather than from where the old walk had got to.
        coVerify(exactly = 2) { interactor.loadPage(any(), page = 2) }
        coVerify(exactly = 0) { interactor.loadPage(any(), page = 3) }
        vm.testCancelScope()
        paginator.close()
    }

    /** The same for the round a spent walk left waiting: the pause is not a way back onto a new list. */
    @Test
    fun firstPage_aWalkPausedByTheCachedPageDoesNotResumeUnderTheFreshOne() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        val pagesRead = mutableListOf<Int>()
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            pagesRead += requested
            emptyPage(current = requested, total = 100)
        }
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            mapper = mapperFor(1),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(emptyPage(current = 1, total = 100), isStale = true))
        // Runs everything that is due now, which stops short of the paused continuation.
        testScheduler.runCurrent()
        val walkedForTheCachedPage = pagesRead.toList()
        emissions.emit(Cached.Value(page(item(1), current = 1, total = 100), isStale = false))
        testScheduler.advanceTimeBy(walkResumePauseUnderTest * 2)
        testScheduler.advanceUntilIdle()

        assertTrue(walkedForTheCachedPage.isNotEmpty(), "the cached page never started a walk")
        assertEquals(walkedForTheCachedPage, pagesRead)
        assertEquals(listOf(1), (vm.testStateValue as SectionState.Content).items.map { it.id })
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * Nothing stored and the load fails is the one case that still belongs on the error state, and it
     * takes the route it took before the cache: `errorHandlerGeneral` into the paginator.
     */
    @Test
    fun firstPage_withNothingStoredAndAFailingLoad_stillShowsTheError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        every { interactor.observeFirstPage(any(), any()) } returns flow {
            throw IllegalStateException("no network")
        }
        val vm = createVM(
            paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher,
            errorHandler = mappingErrorHandler(),
        )

        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        assertEquals(SectionState.Error("no network"), vm.testStateValue)
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun firstPage_walkCountersResetBetweenTheCachedAndTheFreshEmission() = runTest {
        // Both emissions are first pages, and each starts a walk of its own. Counted as one, the
        // fresh page inherits a budget the cached page has already spent and the section gives up
        // without looking past page one.
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val emissions = MutableSharedFlow<Cached<PaginatedResponse<Item>>>(extraBufferCapacity = 2)
        val pagesRead = mutableListOf<Int>()
        every { interactor.observeFirstPage(any(), any()) } returns emissions
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            pagesRead += requested
            emptyPage(current = requested, total = 100)
        }
        val vm = createVM(paginator, config("popular"), interactor, ContentListRefreshCoordinator(), dispatcher)
        vm.testOnStart()
        testScheduler.advanceUntilIdle()

        emissions.emit(Cached.Value(emptyPage(current = 1, total = 100), isStale = true))
        testScheduler.advanceUntilIdle()
        val walkedForTheCachedPage = pagesRead.toList()
        emissions.emit(Cached.Value(emptyPage(current = 1, total = 100), isStale = false))
        testScheduler.advanceUntilIdle()

        assertEquals(walkedForTheCachedPage, pagesRead.drop(walkedForTheCachedPage.size))
        vm.testCancelScope()
        paginator.close()
    }
}
