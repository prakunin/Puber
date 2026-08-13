package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DetailsVMCachedLoadTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var interactor: DetailsInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var errorHandler: ErrorHandler

    private val movie = Item(id = 42, title = "Movie", type = ItemType.MOVIE)

    @BeforeEach
    fun setup() {
        router = mockk(relaxed = true)
        every { router.screens } returns mockk<Screens>(relaxed = true)
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }
        mapper = mockk(relaxed = true)
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns emptyList()
        every { interactor.observeSimilarItems(any()) } returns flowOf(Cached.Value(emptyList(), false))
        every { interactor.seededWatchlistFlag(any()) } returns false
        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
    }

    @Test
    fun contentIsRenderedBeforeTheBookmarkFolderLookupAnswers() = runTest {
        // For a movie with no inline bookmarks the folder lookup is always a network call. Waiting
        // for it is what made a cached title still show a spinner.
        val lookupGate = CompletableDeferred<Boolean>()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } coAnswers { lookupGate.await() }

        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
    }

    @Test
    fun theResolvedWatchlistFlagPatchesTheContent() = runTest {
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } returns true

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun aLateWatchlistPatchIsDroppedAfterTheUserToggled() = runTest {
        // The lookup started before the user pressed the button; letting it land would silently undo
        // what they just did. The mutation itself refreshes the item and re-derives the flag through
        // its own (unrelated, pre-existing) refreshAfterMutation call, so that lookup is stubbed to
        // resolve immediately — only the seed lookup from the initial render is left gated, so the
        // assertion is about that one late patch and nothing else.
        val lookupGate = CompletableDeferred<Boolean>()
        val refreshedMovie = movie.copy(title = "Movie, bookmarked")
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } coAnswers { lookupGate.await() }
        coEvery { interactor.setMovieBookmarked(42, true) } coAnswers {
            com.kino.puber.domain.interactor.details.MovieBookmarkUpdate(
                isBookmarked = true,
                folderTitle = "Later",
            )
        }
        coEvery { interactor.refreshItemDetails(42) } returns refreshedMovie
        coEvery { interactor.isInWatchLaterFolder(refreshedMovie) } returns true
        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        lookupGate.complete(false)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun aStaleCachedItemIsShownAndThenReplacedByTheFreshOne() = runTest {
        val newer = movie.copy(title = "Movie, updated")
        every { interactor.observeItemDetails(42) } returns flowOf(
            Cached.Value(movie, isStale = true),
            Cached.Value(newer, isStale = false),
        )
        val seen = mutableListOf<Item>()
        every { mapper.map(any(), any()) } answers {
            seen += firstArg<Item>()
            content()
        }

        createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(movie, newer), seen.distinct())
    }

    @Test
    fun aFailedRefreshLeavesTheCachedContentOnScreen() = runTest {
        every { interactor.observeItemDetails(42) } returns flowOf(
            Cached.Value(movie, isStale = true),
            Cached.RefreshFailed(IllegalStateException("offline")),
        )

        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.testStateValue is DetailsScreenState.Content)
    }

    private fun content() = DetailsScreenState.Content(
        details = mockk(relaxed = true),
        info = mockk(relaxed = true),
        buttons = emptyList(),
        isInWatchlist = false,
        isWatched = false,
    )

    private fun createVM() = DetailsVM(
        router = router,
        params = DetailsScreenParams(itemId = 42),
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        errorHandler = errorHandler,
    )
}
