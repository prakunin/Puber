package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.system.ContentSharer
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.domain.interactor.details.MovieBookmarkUpdate
import com.kino.puber.domain.interactor.details.MovieWatchedUpdate
import com.kino.puber.domain.interactor.details.WatchedUpdate
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import com.kino.puber.data.api.models.TrailerLinksResponse
import com.kino.puber.domain.interactor.trailer.TrailerLinkInteractor

class DetailsVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var interactor: DetailsInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler
    private lateinit var contentSharer: ContentSharer
    private lateinit var navPrefs: NavigationPreferencesRepository

    private val params = DetailsScreenParams(itemId = 42)

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true) {
            every { screens } returns this@DetailsVMTest.screens
        }
        mapper = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        contentSharer = mockk {
            every { share(any(), any()) } returns true
        }
        errorHandler = mockk {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } returns Unit
        }
        navPrefs = mockk(relaxed = true) {
            every { getAutoTrailerEnabled() } returns true
        }

        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(testItem, isStale = false))
        coEvery { interactor.refreshItemDetails(42) } returns refreshedItem
        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
        every { interactor.observeSimilarItems(42) } returns flowOf(Cached.Value(listOf(similarItem), isStale = false))
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns listOf(videoItem(id = 100))
    }

    @Test
    fun playClicked_navigatesForContentChangeResultToPlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.PlayClicked)

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun shareClickedUsesStablePublicItemUrl() {
        val vm = startedVM()

        vm.onAction(DetailsAction.ShareClicked)

        verify { contentSharer.share("https://kino.pub/item/view/42", any()) }
    }

    @Test
    fun shareClickedFromEpisodeDetailsIncludesEpisodeCoordinates() {
        val vm = startedVM(
            DetailsScreenParams(
                itemId = 42,
                initialEpisode = DetailsEpisodeTarget(seasonNumber = 2, episodeNumber = 5),
            ),
        )

        vm.onAction(DetailsAction.ShareClicked)

        verify { contentSharer.share("https://kino.pub/item/view/42/s2e5", any()) }
    }

    @Test
    fun episodeSelected_navigatesForContentChangeResultToEpisodePlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(42, 1, 2) } returns playerScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.EpisodeSelected(videoItem(id = 101)))

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun commonItemPlayed_navigatesForContentChangeResultToItemPlayer() {
        val playerScreen = mockk<PuberScreen>()
        every { screens.player(100, null, null) } returns playerScreen
        val vm = startedVM()

        vm.onAction(CommonAction.ItemPlayed(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun similarSelected_navigatesForContentChangeResultToDetails() {
        val detailsScreen = mockk<PuberScreen>()
        every { screens.details(100) } returns detailsScreen
        val vm = startedVM()

        vm.onAction(DetailsAction.SimilarSelected(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(detailsScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun commonItemSelected_navigatesForContentChangeResultToDetails() {
        val detailsScreen = mockk<PuberScreen>()
        every { screens.details(100) } returns detailsScreen
        val vm = startedVM()

        vm.onAction(CommonAction.ItemSelected(videoItem(id = 100)))

        verify {
            router.navigateForResult<ContentChangeSet>(detailsScreen, RESULT_CONTENT_CHANGED, any())
        }
    }

    @Test
    fun historyEpisodeParamsOpenPanelWithExactEpisodeFocusTarget() {
        val target = DetailsEpisodeTarget(seasonNumber = 1, episodeNumber = 2)
        val mapped = content().copy(
            currentEpisode = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
            initialEpisodeFocusId = 101,
        )
        every {
            mapper.map(
                item = testItem,
                isInWatchlist = false,
                initialEpisode = target,
            )
        } returns mapped
        val vm = startedVM(
            DetailsScreenParams(
                itemId = 42,
                initialEpisode = target,
            ),
        )

        val content = vm.testStateValue as DetailsScreenState.Content
        assertTrue(content.seasonsPanelVisible)
        assertEquals(101, content.currentEpisode?.id)
        assertEquals(101, content.initialEpisodeFocusId)
    }

    @Test
    fun returnedChangesForCurrentItem_forceRefreshDetails() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        verify(exactly = 1) { interactor.observeItemDetails(42, force = true) }
    }

    @Test
    fun returnedChangesForCurrentItem_areReturnedWhenDetailsCloses() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun returnedChangesForVisibleSimilarItem_reloadSimilarItemsOnly() {
        val playerScreen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.player(42, null, null) } returns playerScreen
        val vm = startedVM()
        vm.onAction(DetailsAction.PlayClicked)
        verify {
            router.navigateForResult<ContentChangeSet>(playerScreen, RESULT_CONTENT_CHANGED, capture(listener))
        }

        listener.captured(ContentChangeSet.single(100, ContentChangeType.Bookmark))

        coVerify(exactly = 0) { interactor.refreshItemDetails(any()) }
        // The initial load must not force; the reload triggered by a returned change for a visible
        // similar item must — otherwise a non-stale cache entry answers from the stored value and
        // skips revalidation, leaving the just-changed item showing its pre-change flags.
        verify(exactly = 1) { interactor.observeSimilarItems(42, force = false) }
        verify(exactly = 1) { interactor.observeSimilarItems(42, force = true) }
    }

    @Test
    fun backPressed_withoutContentChanges_consumesResultListenerWithEmptyChanges() {
        val vm = startedVM()

        vm.onBackPressed()

        verifyEmptyContentChangeBack()
    }

    @Test
    fun watchlistToggle_success_returnsWatchlistChange() {
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watchlist)
    }

    @Test
    fun movieBookmarkToggle_success_returnsBookmarkChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieWatchedToggle_success_returnsWatchedChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } returns MovieWatchedUpdate(
            isWatched = true,
        )
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun episodeWatchedChanged_success_returnsWatchedChange() {
        coEvery { interactor.setEpisodeWatched(42, 1, 2, true) } returns WatchedUpdate(isWatched = true)
        val vm = startedVM()

        vm.onAction(
            DetailsAction.EpisodeWatchedChanged(
                item = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
                watched = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun seasonWatchedChanged_success_returnsWatchedChange() {
        coEvery { interactor.setSeasonWatched(42, 1, true) } returns WatchedUpdate(isWatched = true)
        val vm = startedVM()

        vm.onAction(
            DetailsAction.SeasonWatchedChanged(
                item = videoItem(id = 1, seasonNumber = 1),
                watched = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun similarItemSavedChanged_success_returnsBookmarkChangeForSimilarItem() {
        coEvery { savedItemInteractor.setSaved(100, isSeriesLike = false, saved = true) } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(
            CommonAction.ItemSavedChanged(
                item = videoItem(id = 100),
                isSaved = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 100, ContentChangeType.Bookmark)
    }

    @Test
    fun similarSeriesSavedChanged_returnsWatchlistChangeForSimilarItem() {
        coEvery { savedItemInteractor.setSaved(100, isSeriesLike = true, saved = true) } returns Result.success(true)
        val vm = startedVM()

        vm.onAction(
            CommonAction.ItemSavedChanged(
                item = videoItem(id = 100, isSeriesLike = true),
                isSaved = true,
            )
        )
        vm.onBackPressed()

        verifyContentChangeBack(itemId = 100, ContentChangeType.Watchlist)
    }

    @Test
    fun backWaitsForPendingMutationAndReturnsItsChange() {
        val releaseMutation = CompletableDeferred<Unit>()
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } coAnswers {
            releaseMutation.await()
            MovieWatchedUpdate(isWatched = true)
        }
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        releaseMutation.complete(Unit)
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun failedMutationThenBackReturnsEmptyChanges() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } throws IllegalStateException("failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()

        verifyEmptyContentChangeBack()
    }


    @Test
    fun watchlistWriteSuccess_refreshFailure_keepsConfirmedStateAndReturnsChange() {
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } returns Result.success(true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watchlist)
    }

    @Test
    fun rapidSeriesWatchlistChanges_applyLatestDesiredStateAfterFirstFailure() {
        val releaseFirst = CompletableDeferred<Unit>()
        val failure = IllegalStateException("failed")
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = true)
        } coAnswers {
            releaseFirst.await()
            Result.failure(failure)
        }
        coEvery {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        } returns Result.success(false)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)
        vm.onAction(DetailsAction.WatchlistToggleClicked)

        coVerify(exactly = 0) {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        }
        releaseFirst.complete(Unit)

        coVerify(exactly = 1) {
            savedItemInteractor.setSaved(42, isSeriesLike = true, saved = false)
        }
        assertFalse((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
    }

    @Test
    fun movieBookmarkAddWriteSuccess_refreshFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkRemoveWriteSuccess_refreshFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        every { mapper.map(movie, any()) } returns content(isInWatchlist = true)
        coEvery { interactor.setMovieBookmarked(42, bookmarked = false) } returns MovieBookmarkUpdate(
            isBookmarked = false,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertFalse((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkWriteSuccess_membershipReadFailure_keepsRequestedStateAndReturnsChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieBookmarked(42, bookmarked = true) } returns MovieBookmarkUpdate(
            isBookmarked = true,
            folderTitle = "Watch later",
        )
        val vm = startedVM()
        coEvery { interactor.refreshItemDetails(42) } returns movie
        coEvery { interactor.isInWatchLaterFolder(movie) } throws IllegalStateException("read failed")

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Bookmark)
    }

    @Test
    fun movieBookmarkRemove_successfulRefreshAppliesRemainingFolderState() {
        val movie = movieItem()
        val refreshed = movie.copy(title = "Refreshed")
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        every { mapper.map(movie, any()) } returns content(isInWatchlist = true)
        coEvery { interactor.setMovieBookmarked(42, bookmarked = false) } returns MovieBookmarkUpdate(
            isBookmarked = false,
            folderTitle = "Watch later",
        )
        coEvery { interactor.refreshItemDetails(42) } returns refreshed
        coEvery { interactor.isInWatchLaterFolder(refreshed) } returns true
        every { mapper.map(refreshed, true) } returns content(isInWatchlist = true)
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchlistToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isInWatchlist)
        verify { mapper.map(refreshed, true) }
    }

    @Test
    fun movieWatchedWriteSuccess_refreshFailure_keepsConfirmedStateAndReturnsChange() {
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } returns MovieWatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)

        assertTrue((vm.testStateValue as DetailsScreenState.Content).isWatched)
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun episodeWatchedWriteSuccess_refreshFailure_appliesRequestedStateAndReturnsChange() {
        coEvery { interactor.setEpisodeWatched(42, 1, 2, true) } returns WatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(
            DetailsAction.EpisodeWatchedChanged(
                item = videoItem(id = 101, seasonNumber = 1, episodeNumber = 2),
                watched = true,
            )
        )

        verify {
            mapper.map(
                match { item -> item.seasons?.first()?.episodes?.first()?.watched == 1 },
                any(),
            )
        }
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun seasonWatchedWriteSuccess_refreshFailure_appliesRequestedStateAndReturnsChange() {
        coEvery { interactor.setSeasonWatched(42, 1, true) } returns WatchedUpdate(isWatched = true)
        coEvery { interactor.refreshItemDetails(42) } throws IllegalStateException("refresh failed")
        val vm = startedVM()

        vm.onAction(
            DetailsAction.SeasonWatchedChanged(
                item = videoItem(id = 1, seasonNumber = 1),
                watched = true,
            )
        )

        verify {
            mapper.map(
                match { item -> item.seasons?.first()?.episodes.orEmpty().all { it.watched == 1 } },
                any(),
            )
        }
        vm.onBackPressed()
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }

    @Test
    fun rapidMovieWatchedMutations_areSerializedAndAppliedInActionOrder() {
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } coAnswers {
            order += "start-true"
            releaseFirst.await()
            order += "finish-true"
            MovieWatchedUpdate(isWatched = true)
        }
        coEvery { interactor.setMovieWatched(42, watched = false) } coAnswers {
            order += "false"
            MovieWatchedUpdate(isWatched = false)
        }
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onAction(DetailsAction.WatchedToggleClicked)

        assertEquals(listOf("start-true"), order)
        coVerify(exactly = 0) { interactor.setMovieWatched(42, watched = false) }

        releaseFirst.complete(Unit)

        assertEquals(listOf("start-true", "finish-true", "false"), order)
        assertFalse((vm.testStateValue as DetailsScreenState.Content).isWatched)
    }

    @Test
    fun repeatedBackWhileMutationPending_isConsumedAndSendsExactlyOneResult() {
        val releaseMutation = CompletableDeferred<Unit>()
        val movie = movieItem()
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(movie, isStale = false))
        coEvery { interactor.setMovieWatched(42, watched = true) } coAnswers {
            releaseMutation.await()
            MovieWatchedUpdate(isWatched = true)
        }
        val vm = startedVM()

        vm.onAction(DetailsAction.WatchedToggleClicked)
        vm.onBackPressed()
        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 0) { router.back(any(), any()) }
        verify(exactly = 3) { router.addBackDispatcher(vm) }

        releaseMutation.complete(Unit)

        verify(exactly = 1) { router.back(RESULT_CONTENT_CHANGED, any()) }
        verifyContentChangeBack(itemId = 42, ContentChangeType.Watched)
    }


    /**
     * A resolver that answers only from the item payload. A trailer given as a bare path goes to
     * the API for a signed link, and this stands in for an API that has none to give.
     */
    private fun noTrailerLinks() = TrailerLinkInteractor(
        mockk {
            coEvery { getTrailerLinks(any()) } returns Result.success(TrailerLinksResponse())
        }
    )

    private fun startedVM(
        params: DetailsScreenParams = this.params,
    ): DetailsVM = createVM(params).also { it.testOnStart() }

    private fun createVM(
        params: DetailsScreenParams = this.params,
    ) = DetailsVM(
        router = router,
        params = params,
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        contentUriCodec = ContentUriCodec(),
        contentSharer = contentSharer,
        navPrefs = navPrefs,
        trailerLinks = noTrailerLinks(),
        errorHandler = errorHandler,
    )

    private fun content(
        similarItems: List<VideoItemUIState> = emptyList(),
        isInWatchlist: Boolean = false,
        isWatched: Boolean = false,
    ): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = VideoDetailsUIState.Loading,
            info = DetailsInfoUIState(
                ratings = emptyList(),
                factsLine = "",
                creditsLine = "",
            ),
            buttons = emptyList(),
            isInWatchlist = isInWatchlist,
            isWatched = isWatched,
            similarItems = similarItems,
        )
    }

    private fun videoItem(
        id: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        isSeriesLike: Boolean = false,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = "Item $id",
            imageUrl = "",
            bigImageUrl = "",
            isSeriesLike = isSeriesLike,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    private fun verifyContentChangeBack(itemId: Int, vararg expectedTypes: ContentChangeType) {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    val changes = result as? ContentChangeSet ?: return@match false
                    changes.changes[itemId] == expectedTypes.toSet()
                },
            )
        }
    }

    private fun verifyEmptyContentChangeBack() {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    (result as? ContentChangeSet)?.isEmpty == true
                },
            )
        }
    }

    private fun movieItem(): Item {
        return Item(
            id = 42,
            title = "Movie",
            type = ItemType.MOVIE,
        )
    }

    private val testItem = Item(
        id = 42,
        title = "Series",
        type = ItemType.SERIAL,
        seasons = listOf(
            Season(
                id = 1,
                number = 1,
                episodes = listOf(Episode(id = 101, number = 2, title = "Episode 2")),
            )
        ),
    )

    private val refreshedItem = testItem.copy(title = "Series refreshed")


    private val similarItem = Item(
        id = 100,
        title = "Similar",
        type = ItemType.MOVIE,
    )
}
