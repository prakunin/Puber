package com.kino.puber.ui.feature.details.vm

import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Trailer
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The trailer that takes over the panel behind the description, and every way it must not start
 * or must stop. The full-screen trailer the Trailer button opens is a different thing entirely;
 * it lives in [DetailsVMTest].
 */
class DetailsVMTrailerPreviewTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        /** Mirrors `DetailsVM.TRAILER_PREVIEW_DELAY_MS`, which is private to the ViewModel. */
        private const val TRAILER_PAUSE_MS = 2000L
        private const val TRAILER_URL = "https://cdn/trailer.mp4"
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var mapper: DetailsScreenUIMapper
    private lateinit var interactor: DetailsInteractor
    private lateinit var savedItemInteractor: SavedItemInteractor
    private lateinit var errorHandler: ErrorHandler
    private lateinit var navPrefs: NavigationPreferencesRepository

    private val item = Item(id = 42, title = "Movie", type = ItemType.MOVIE)

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true) {
            every { screens } returns this@DetailsVMTrailerPreviewTest.screens
        }
        mapper = mockk(relaxed = true)
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        errorHandler = mockk {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } returns Unit
        }
        navPrefs = mockk(relaxed = true) {
            every { getAutoTrailerEnabled() } returns true
        }

        coEvery { interactor.isInWatchLaterFolder(any()) } returns false
        every { interactor.observeSimilarItems(42) } returns flowOf(Cached.Value(emptyList(), isStale = false))
        every { mapper.map(any(), any()) } returns content()
        every { mapper.mapSimilarItems(any()) } returns emptyList()
        givenItem(item.copy(trailer = Trailer(url = TRAILER_URL)))
    }

    @Test
    fun theTrailerStartsOnlyAfterThePause() {
        val vm = startedVM()

        assertNull(previewTrailerUrl(vm))

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertEquals(TRAILER_URL, previewTrailerUrl(vm))
    }

    @Test
    fun theFileUrlIsUsedWhenThereIsNoStreamUrl() {
        givenItem(item.copy(trailer = Trailer(url = "", file = "https://cdn/trailer.file")))
        val vm = startedVM()

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertEquals("https://cdn/trailer.file", previewTrailerUrl(vm))
    }

    @Test
    fun aTrailerGivenAsABarePathIsNotPlayed() {
        givenItem(item.copy(trailer = Trailer(url = "/trailers/d/02/d88196ed.mp4")))
        val vm = startedVM()

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun theTrailerStaysOffWhenTheSettingIsOff() {
        every { navPrefs.getAutoTrailerEnabled() } returns false
        val vm = startedVM()

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun anItemWithoutATrailerNeverStartsOne() {
        givenItem(item)
        val vm = startedVM()

        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun theFullScreenTrailerReplacesTheOneInThePanel() {
        val vm = startedVM()
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        vm.onAction(DetailsAction.TrailerClicked)

        val content = vm.testStateValue as DetailsScreenState.Content
        assertNull(content.previewTrailerUrl)
        assertEquals(TRAILER_URL, content.trailerUrl)
    }

    @Test
    fun leavingForThePlayerWithinThePauseNeverStartsIt() {
        every { screens.player(42, null, null) } returns mockk<PuberScreen>()
        val vm = startedVM()

        vm.onAction(DetailsAction.PlayClicked)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun openingTheSeasonsPanelStopsIt() {
        val vm = startedVM()
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        vm.onAction(DetailsAction.SelectSeasonClicked)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun scrollingPastThePanelStopsIt() {
        val vm = startedVM()
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        vm.onAction(DetailsAction.TrailerPreviewStopped)

        assertNull(previewTrailerUrl(vm))
    }

    @Test
    fun theEndOfTheTrailerBringsBackTheStill() {
        val vm = startedVM()
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(TRAILER_PAUSE_MS + 1)

        vm.onAction(DetailsAction.TrailerPreviewFinished)

        assertNull(previewTrailerUrl(vm))
    }

    private fun givenItem(item: Item) {
        every { interactor.observeItemDetails(42) } returns flowOf(Cached.Value(item, isStale = false))
    }

    private fun previewTrailerUrl(vm: DetailsVM) =
        (vm.testStateValue as DetailsScreenState.Content).previewTrailerUrl

    private fun startedVM(): DetailsVM = DetailsVM(
        router = router,
        params = DetailsScreenParams(itemId = 42),
        mapper = mapper,
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        resources = FakeResourceProvider(),
        contentUriCodec = ContentUriCodec(),
        contentSharer = mockk(relaxed = true),
        navPrefs = navPrefs,
        errorHandler = errorHandler,
    ).also { it.testOnStart() }

    private fun content() = DetailsScreenState.Content(
        details = VideoDetailsUIState.Loading,
        info = DetailsInfoUIState(
            description = "",
            ratings = emptyList(),
            primaryRows = emptyList(),
            secondaryRows = emptyList(),
            castMembers = emptyList(),
        ),
        buttons = emptyList(),
        isInWatchlist = false,
        isWatched = false,
    )
}
