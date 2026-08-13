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

        assertEquals(Unit, withTimeout(1_000) { refreshRequests.first() })
    }

    @Test
    fun directSavedChange_invalidatesCacheOnceAndRestartsSiblingSections() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstPaginator = paginator(dispatcher)
        val siblingPaginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val savedItemInteractor = mockk<SavedItemInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val firstConfig = config("popular")
        val siblingConfig = config("fresh")
        coEvery { interactor.loadPage(any(), page = 1) } returns emptyPage()
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val first = createVM(
            paginator = firstPaginator,
            config = firstConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
        )
        val sibling = createVM(
            paginator = siblingPaginator,
            config = siblingConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            savedItemInteractor = savedItemInteractor,
        )
        first.testOnStart()
        sibling.testOnStart()
        testScheduler.advanceUntilIdle()

        first.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        testScheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        coVerify(exactly = 2) { interactor.loadPage(firstConfig, page = 1) }
        coVerify(exactly = 2) { interactor.loadPage(siblingConfig, page = 1) }
        first.testCancelScope()
        sibling.testCancelScope()
        firstPaginator.close()
        siblingPaginator.close()
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

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    /** Mirrors SectionVM.MAX_EMPTY_PAGE_CHAIN. */
    private val maxEmptyPageChainUnderTest = 3

    /** Mirrors SectionVM.WALK_RESUME_PAUSE, in milliseconds. */
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
