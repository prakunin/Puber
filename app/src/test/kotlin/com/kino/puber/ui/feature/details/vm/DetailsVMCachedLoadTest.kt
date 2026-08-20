package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.system.ContentSharer
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
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
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import com.kino.puber.data.api.models.TrailerLinksResponse
import com.kino.puber.domain.interactor.trailer.TrailerLinkInteractor

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
    fun aFreshLookupAfterAReturnedChangeAppliesEvenAfterAnEarlierToggle() = runTest {
        // A toggle must not disable future lookups forever: once something forces a reload of this
        // same item (say, the player reporting a content change), the new lookup for the freshly
        // reloaded item is newer than the earlier toggle and must be allowed to land — otherwise the
        // screen is stuck showing the seeded (possibly wrong, for a movie) flag for the rest of its
        // life.
        val bookmarkedAfterToggle = movie.copy(title = "Movie, bookmarked")
        val reloaded = movie.copy(title = "Movie, reloaded")
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        every {
            interactor.observeItemDetails(42, force = true)
        } returns flowOf(Cached.Value(reloaded, isStale = false))
        coEvery { interactor.isInWatchLaterFolder(movie) } returns false
        coEvery { interactor.setMovieBookmarked(42, true) } returns
            com.kino.puber.domain.interactor.details.MovieBookmarkUpdate(
                isBookmarked = true,
                folderTitle = "Later",
            )
        coEvery { interactor.refreshItemDetails(42) } returns bookmarkedAfterToggle
        coEvery { interactor.isInWatchLaterFolder(bookmarkedAfterToggle) } returns true
        coEvery { interactor.isInWatchLaterFolder(reloaded) } returns true
        val vm = createVM().also { it.testOnStart() }
        runCurrent()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)

        vm.onAction(DetailsAction.PlayClicked)
        verify { router.navigateForResult<ContentChangeSet>(any(), RESULT_CONTENT_CHANGED, capture(listener)) }
        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, (vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun returnedChangesForVisibleSimilarItem_forcesRevalidation() = runTest {
        // The old code hit the API unconditionally for this reload. Now that similar items are
        // served from a cache, an unforced call can answer straight from the stored (non-stale)
        // entry and skip revalidation entirely — which would leave a just-changed similar item
        // showing its pre-change flags for up to CacheTtl.SimilarItems.
        val similarItem = VideoItemUIState(
            id = 100,
            title = "Similar",
            imageUrl = "",
            bigImageUrl = "",
            isSeriesLike = false,
        )
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        every { mapper.mapSimilarItems(any()) } returns listOf(similarItem)
        val vm = createVM().also { it.testOnStart() }
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(DetailsAction.PlayClicked)
        verify { router.navigateForResult<ContentChangeSet>(any(), RESULT_CONTENT_CHANGED, capture(listener)) }
        listener.captured(ContentChangeSet.single(100, ContentChangeType.Bookmark))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { interactor.observeSimilarItems(42, force = false) }
        verify(exactly = 1) { interactor.observeSimilarItems(42, force = true) }
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


    /**
     * A resolver that answers only from the item payload. A trailer given as a bare path goes to
     * the API for a signed link, and this stands in for an API that has none to give.
     */
    private fun noTrailerLinks() = TrailerLinkInteractor(
        mockk {
            coEvery { getTrailerLinks(any()) } returns Result.success(TrailerLinksResponse())
        }
    )

    private fun createVM() = DetailsVM(
        router = router,
        params = DetailsScreenParams(itemId = 42),
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        contentUriCodec = ContentUriCodec(),
        contentSharer = mockk(relaxed = true),
        navPrefs = mockk(relaxed = true),
        trailerLinks = noTrailerLinks(),
        errorHandler = errorHandler,
    )
}
