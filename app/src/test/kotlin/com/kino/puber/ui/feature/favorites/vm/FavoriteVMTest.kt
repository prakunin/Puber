package com.kino.puber.ui.feature.favorites.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.favorites.FavoritesInteractor
import com.kino.puber.ui.feature.favorites.model.FavoriteItemUIMapper
import com.kino.puber.ui.feature.favorites.model.FavoriteViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class FavoriteVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: FavoritesInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var mapper: FavoriteItemUIMapper

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        coEvery { interactor.observeWatchlist(force = any()) } returns
            flowOf(Cached.Value(listOf(item()), isStale = false))
        coEvery { interactor.sortByRecentlyPlayed(any()) } answers { firstArg() }
        coEvery { interactor.getItemDetails(42) } returns item()
        every { mapper.mapToState(any(), any()) } returns FavoriteViewState.Content(
            gridState = VideoGridUIState(emptyList()),
            selectedItem = VideoDetailsUIState.Loading,
        )
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
    fun returnedChanges_reloadData() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemPlayed(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 1) { interactor.observeWatchlist(force = false) }
        coVerify(exactly = 1) { interactor.observeWatchlist(force = true) }
    }

    /**
     * The grid must not wait for the side panel. `ItemDetailsRepository.getItemDetails` waits for
     * the last emission of its own feed, which is the network whenever that entry is stale or
     * absent — a cold start, exactly the case this cache exists for — so a watching list gated on
     * it comes off disk in milliseconds and then sits behind a request before anything is drawn.
     */
    @Test
    fun watchingRows_areDrawnBeforeTheDetailsCallSettles() {
        val details = CompletableDeferred<Item>()
        val grid = VideoGridUIState(emptyList())
        val loadedPanel = VideoDetailsUIState.Loading.copy(id = 42, isLoading = false)
        coEvery { interactor.getItemDetails(42) } coAnswers { details.await() }
        every { mapper.mapToState(any(), null) } returns FavoriteViewState.Content(
            gridState = grid,
            selectedItem = VideoDetailsUIState.Loading,
        )
        every { mapper.mapSelectedItem(any(), any()) } returns loadedPanel

        val vm = createVM().also { it.testOnStart() }

        assertEquals(
            FavoriteViewState.Content(grid, VideoDetailsUIState.Loading),
            vm.testStateValue,
        )

        details.complete(item())

        assertEquals(loadedPanel, (vm.testStateValue as FavoriteViewState.Content).selectedItem)
    }

    /**
     * Every refresh signal lands in the same place, and two collections alive at once settle in
     * whatever order their emissions arrive in — the forced one revalidating while the earlier one
     * is still serving the stored list, so the older list can land last and win.
     */
    @Test
    fun aSecondLoad_dropsTheCollectionItReplaces() {
        val staleEmission = CompletableDeferred<Unit>()
        val published = mutableListOf<List<Int>>()
        coEvery { interactor.observeWatchlist(force = false) } returns flow {
            staleEmission.await()
            emit(Cached.Value(listOf(item(id = 1)), isStale = false))
        }
        coEvery { interactor.observeWatchlist(force = true) } returns
            flowOf(Cached.Value(listOf(item(id = 2)), isStale = false))
        every { mapper.mapToState(any(), any()) } answers {
            published += firstArg<List<Item>>().map(Item::id)
            FavoriteViewState.Content(VideoGridUIState(emptyList()), VideoDetailsUIState.Loading)
        }
        val vm = createVM().also { it.testOnStart() }

        vm.onAction(CommonAction.RetryClicked)
        staleEmission.complete(Unit)

        assertEquals(listOf(listOf(2)), published)
    }

    private fun createVM() = FavoriteVM(router, interactor, savedItemInteractor, mapper)

    private fun item(id: Int = 42) = Item(id = id, title = "Series", type = ItemType.SERIAL)

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")
}
