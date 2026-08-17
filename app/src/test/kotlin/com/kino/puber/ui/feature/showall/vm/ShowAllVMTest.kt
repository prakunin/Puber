package com.kino.puber.ui.feature.showall.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
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
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ShowAllVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: ContentListInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
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
    fun childChanges_areAccumulatedAndReturnedOnBack() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM()
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onBackPressed()

        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Watched)
                },
            )
        }
        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
    }

    @Test
    fun directSavedChange_isAccumulatedAndInvalidatesFirstPageCacheAfterSuccess() {
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } returns Result.success(false)
        val vm = createVM()

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        vm.onBackPressed()

        verify(exactly = 1) { interactor.invalidateFirstPageCache() }
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Bookmark)
                },
            )
        }
    }

    @Test
    fun backWhileDirectSavePending_waitsAndReturnsChangeExactlyOnce() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery {
            savedItemInteractor.setSaved(itemId = 42, isSeriesLike = false, saved = false)
        } coAnswers {
            releaseSave.await()
            Result.success(false)
        }
        val vm = createVM()

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), false))
        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        verify(exactly = 2) { router.addBackDispatcher(vm) }
        releaseSave.complete(Unit)

        verify(exactly = 1) {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> {
                    it.changes[42] == setOf(ContentChangeType.Bookmark)
                },
            )
        }
    }

    @Test
    fun backWithoutChanges_returnsEmptyChangeSet() {
        val vm = createVM()

        vm.onBackPressed()

        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match<ContentChangeSet> { it.isEmpty },
            )
        }
    }

    @Test
    fun firstPage_publishesInteractorItemsWithoutAdditionalFiltering() = runBlocking {
        val item = Item(
            id = 25,
            title = "Interactor result",
            type = ItemType.MOVIE,
            genres = listOf(Genre(ANIME_GENRE_ID, "Anime")),
        )
        val mappedItem = videoItem(25)
        val mapper = mockk<VideoItemUIMapper>()
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item)
        servesFirstPageFromTheNetwork(interactor)
        every { mapper.mapShortItemList(listOf(item)) } returns listOf(mappedItem)
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(paginator = paginator, mapper = mapper)

        vm.testOnStart()
        withTimeout(2_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }

        assertEquals(ShowAllViewState.Content(listOf(mappedItem)), vm.testStateValue)
        verify(exactly = 1) { mapper.mapShortItemList(listOf(item)) }
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * A retry is one of the signals that knows the server's answer has changed, so it may not settle
     * for whatever the store happens to hold: the stored page is still drawn, and the request behind
     * it is guaranteed. Opening the grid is not such a signal, and asks for nothing when the entry
     * is fresh.
     */
    @Test
    fun retry_forcesTheFirstPageRead_whereOpeningTheGridDoesNot() = runBlocking {
        val config = SectionConfig(id = "popular", title = "Popular")
        coEvery { interactor.loadPage(any(), page = 1) } returns page(item(7))
        servesFirstPageFromTheNetwork(interactor)
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(config = config, paginator = paginator)
        vm.testOnStart()
        withTimeout(2_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }
        verify(exactly = 1) { interactor.observeFirstPage(config, force = false) }

        vm.onAction(CommonAction.RetryClicked)

        verify(timeout = 2_000) { interactor.observeFirstPage(config, force = true) }
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun aWalkThatSpentItsBudgetResumesWhereItStopped() = runBlocking {
        // Retry on the empty state starts over from page one and walks into the same wall, so a run
        // of watched titles longer than one budget would strand the screen there for good.
        val visible = Item(id = 7, title = "Item 7", type = ItemType.MOVIE)
        val firstPageWithSomethingLeft = MAX_EMPTY_PAGE_CHAIN_UNDER_TEST + 2
        coEvery { interactor.loadPage(any(), any()) } answers {
            val requested = secondArg<Int>()
            if (requested < firstPageWithSomethingLeft) {
                emptyPage(current = requested, total = 100)
            } else {
                page(visible, current = requested, total = 100)
            }
        }
        servesFirstPageFromTheNetwork(interactor)
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(paginator = paginator)

        vm.testOnStart()
        withTimeout(5_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }

        // Picked up on the page after the one the budget ran out on, rather than starting over.
        coVerify(exactly = 1) { interactor.loadPage(any(), page = firstPageWithSomethingLeft) }
        coVerify(exactly = 1) { interactor.loadPage(any(), page = 1) }
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * Wires the cached-first-page flow onto the page loads these tests stub: one fresh emission per
     * subscription, which is what the interactor's own loader produces when nothing is stored.
     */
    private fun servesFirstPageFromTheNetwork(interactor: ContentListInteractor) {
        every { interactor.observeFirstPage(any(), any()) } answers {
            val config = firstArg<SectionConfig>()
            flow { emit(Cached.Value(interactor.loadPage(config, page = 1), isStale = false)) }
        }
    }

    private fun createVM(
        config: SectionConfig = SectionConfig(id = "popular", title = "Popular"),
        paginator: Paginator.Store<Item> = Paginator.Store { old, new -> old.id == new.id },
        mapper: VideoItemUIMapper = mockk(relaxed = true),
        contentListInteractor: ContentListInteractor = interactor,
    ) = ShowAllVM(
        paginator = paginator,
        config = config,
        interactor = contentListInteractor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = router,
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun page(
        item: Item,
        current: Int = 1,
        total: Int = 1,
    ) = PaginatedResponse(
        items = listOf(item),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )

    private fun emptyPage(current: Int, total: Int) = PaginatedResponse<Item>(
        items = emptyList(),
        pagination = Pagination(current = current, perpage = 50, total = total),
    )
}

/** Mirrors ContentListPagingVM.MAX_EMPTY_PAGE_CHAIN. */
private const val MAX_EMPTY_PAGE_CHAIN_UNDER_TEST = 3
