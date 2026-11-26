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
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.preferences.CatalogueFilterRepository
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.domain.interactor.country.CountryInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.showall.model.FilterCountries
import com.kino.puber.ui.feature.showall.model.FilterGenres
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.applyTo
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import com.kino.puber.util.FakeSharedPreferences
import com.kino.puber.util.MainDispatcherExtension
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@Suppress("LargeClass")
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
    private lateinit var genreInteractor: GenreInteractor
    private lateinit var countryInteractor: CountryInteractor

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        genreInteractor = mockk()
        coEvery { genreInteractor.getGenres(any()) } returns Result.success(emptyList())
        countryInteractor = mockk()
        coEvery { countryInteractor.getCountries() } returns Result.success(emptyList())
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
     * The grid restores its depth through the same path as a section row, so a retry over pages
     * that overlap may not put a title in the grid twice.
     */
    @Test
    fun retry_ofAGridWithOverlappingPages_publishesEachIdOnce() = runBlocking {
        val page2Calls = AtomicInteger()
        val mapped = CopyOnWriteArrayList<Pair<Int, List<Int>>>()
        val mapper = mockk<VideoItemUIMapper>()
        every { mapper.mapShortItemList(any()) } answers {
            val items = firstArg<List<Item>>()
            mapped += page2Calls.get() to items.map { it.id }
            items.map { videoItem(it.id) }
        }
        coEvery { interactor.loadPage(any(), page = 1) } returns pageOf(1, 2, 3, current = 1, total = 2)
        coEvery { interactor.loadPage(any(), page = 2) } answers {
            page2Calls.incrementAndGet()
            pageOf(4, 2, 5, current = 2, total = 2)
        }
        servesFirstPageFromTheNetwork(interactor)
        val paginator = Paginator.Store<Item> { old, new -> old.id == new.id }
        val vm = createVM(paginator = paginator, mapper = mapper)
        vm.testOnStart()
        withTimeout(2_000) {
            while (vm.testStateValue !is ShowAllViewState.Content) {
                delay(10)
            }
        }
        vm.onAction(CommonAction.LoadMore)
        withTimeout(2_000) {
            while (page2Calls.get() != 1 || shownIds(vm).lastOrNull() != 5) {
                delay(10)
            }
        }

        vm.onAction(CommonAction.RetryClicked)

        withTimeout(2_000) {
            while (mapped.none { it.first == 2 }) {
                delay(10)
            }
        }
        val restored = mapped.first { it.first == 2 }.second
        assertEquals(listOf(1, 2, 3, 4, 5), restored)
        assertEquals(listOf(1, 2, 3, 4, 5), shownIds(vm))
        vm.testCancelScope()
        paginator.close()
    }

    // region filter

    private val allMovie = TabTypeConfig.browseEntryFor(TabType.Movies)!!.config
    private val all4k = TabTypeConfig.browseEntryFor(TabType.For4k)!!.config
    private val allSerial = TabTypeConfig.browseEntryFor(TabType.Series)!!.config
    private val allDocSerial = TabTypeConfig.browseEntryFor(TabType.DocSeries)!!.config
    private val allTvShow = TabTypeConfig.browseEntryFor(TabType.TvShows)!!.config
    private val allDocMovie = TabTypeConfig.browseEntryFor(TabType.DocMovies)!!.config
    private val allConcert = TabTypeConfig.browseEntryFor(TabType.Concerts)!!.config
    private val animeByYear = ContentFilter(
        genre = Genre(ANIME_GENRE_ID, "Аниме"),
        sort = SortField.YEAR,
        sortDirection = SortDirection.ASC,
    )
    private val drama = Genre(9, "Драма")
    private val usa = Country(1, "США")
    private val dramaByYear = ContentFilter(genre = drama, sort = SortField.YEAR, sortDirection = SortDirection.ASC)

    /** One `observeFirstPage` call: the config it was given and whether it may use the store. */
    private data class FirstPageCall(val config: SectionConfig, val cacheable: Boolean)

    private class FilterRig(
        val vm: ShowAllVM,
        val paginator: Paginator.Store<Item>,
        val calls: MutableList<FirstPageCall>,
        val scope: TestScope,
    ) {
        fun run() = scope.advanceUntilIdle()

        fun act(vararg actions: UIAction) {
            actions.forEach { vm.onAction(it) }
            run()
        }

        fun applyFilter(filter: ContentFilter) {
            act(
                ShowAllAction.FilterPanelOpened,
                ShowAllAction.SortFieldChosen(filter.sort),
                ShowAllAction.SortDirectionChosen(filter.sortDirection),
                ShowAllAction.GenreChosen(filter.genre),
                ShowAllAction.CountryChosen(filter.country),
                ShowAllAction.YearChosen(filter.yearRange),
                ShowAllAction.StatusChosen(filter.finished == true),
                ShowAllAction.FilterPanelClosed,
            )
        }

        fun close() {
            vm.testCancelScope()
            paginator.close()
        }
    }

    /**
     * A grid on the test scheduler. [firstPage] answers the first page of a config and [loadPage]
     * any later page; both default to a single page of the ids the config's genre implies.
     */
    private fun TestScope.filterRig(
        config: SectionConfig = allMovie,
        firstPage: suspend (SectionConfig) -> PaginatedResponse<Item> = { pageOf(1, 2, current = 1, total = 1) },
        filterRepository: CatalogueFilterRepository = filterRepositoryOn(FakeSharedPreferences()),
        showAnime: Boolean = true,
    ): FilterRig {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = Paginator.Store<Item>(
            comparator = { old, new -> old.id == new.id },
            coroutineContext = dispatcher,
        )
        val calls = CopyOnWriteArrayList<FirstPageCall>()
        every { interactor.observeFirstPage(any(), any(), any()) } answers {
            val asked = firstArg<SectionConfig>()
            calls += FirstPageCall(asked, thirdArg())
            flow { emit(Cached.Value(firstPage(asked), isStale = false)) }
        }
        coEvery { interactor.loadPage(any(), page = 1) } coAnswers { firstPage(firstArg()) }
        every { interactor.showAnimeEnabled } returns showAnime
        val mapper = mockk<VideoItemUIMapper>()
        every { mapper.mapShortItemList(any()) } answers { firstArg<List<Item>>().map { videoItem(it.id) } }
        val vm = createVM(
            config = config,
            paginator = paginator,
            mapper = mapper,
            pagingCoroutineContext = dispatcher,
            filterRepository = filterRepository,
        )
        vm.testOnStart()
        val rig = FilterRig(vm, paginator, calls, this)
        rig.run()
        return rig
    }

    // region persisted filter

    @Test
    fun restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, dramaByYear)

        val rig = filterRig(filterRepository = repository)

        assertEquals(listOf(FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false)), rig.calls.toList())
        rig.close()
    }

    @Test
    fun restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage() {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, dramaByYear)

        val vm = createVM(config = allMovie, filterRepository = repository)

        val state = vm.filterState.value
        assertTrue(state.isActive)
        assertEquals("Драма", state.genreTitle)
        assertEquals(SortField.YEAR, state.sort)
        assertEquals(SortDirection.ASC, state.direction)
    }

    @Test
    fun filterApplied_reopenedGridStartsWithIt() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        val first = filterRig(filterRepository = repository)
        first.applyFilter(dramaByYear)
        first.close()

        val reopened = filterRig(filterRepository = repository)

        assertEquals(
            listOf(FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false)),
            reopened.calls.toList(),
        )
        reopened.close()
    }

    @Test
    fun filterOnMovies_isNotInheritedBySerialOr4k() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        val movies = filterRig(filterRepository = repository)
        movies.applyFilter(dramaByYear)
        movies.close()

        listOf(allSerial, all4k).forEach { config ->
            val rig = filterRig(config = config, filterRepository = repository)

            assertEquals(listOf(FirstPageCall(config, cacheable = true)), rig.calls.toList())
            assertEquals(false, rig.vm.filterState.value.isActive)
            rig.close()
        }
    }

    @Test
    fun filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached() = runTest {
        val preferences = FakeSharedPreferences()
        val repository = filterRepositoryOn(preferences)
        val first = filterRig(filterRepository = repository)
        first.applyFilter(dramaByYear)
        assertTrue(preferences.values.containsKey("filter_all_movie"))

        first.act(ShowAllAction.FilterReset)
        first.close()

        assertEquals(false, preferences.values.containsKey("filter_all_movie"))
        val reopened = filterRig(filterRepository = repository)
        assertEquals(listOf(FirstPageCall(allMovie, cacheable = true)), reopened.calls.toList())
        reopened.close()
    }

    @Test
    fun restoredFilter_panelDraftEqualsIt() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, dramaByYear)
        val rig = filterRig(filterRepository = repository)

        rig.act(ShowAllAction.FilterPanelOpened)

        assertEquals(dramaByYear, rig.vm.filterState.value.draft)
        rig.close()
    }

    @Test
    fun restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage() = runTest {
        val preferences = FakeSharedPreferences()
        val repository = filterRepositoryOn(preferences)
        repository.write(allMovie.id, dramaByYear)
        val writes = preferences.transactions.size
        val rig = filterRig(filterRepository = repository)

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.FilterPanelClosed)

        assertEquals(1, rig.calls.size)
        assertEquals(writes, preferences.transactions.size)
        rig.close()
    }

    @Test
    fun restoredAnime_withShowAnimeOff_skipsTheGenreKeepsTheSort() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, animeByYear)

        val rig = filterRig(filterRepository = repository, showAnime = false)

        val sortOnly = ContentFilter(sort = SortField.YEAR, sortDirection = SortDirection.ASC)
        assertEquals(listOf(FirstPageCall(sortOnly.applyTo(allMovie), cacheable = false)), rig.calls.toList())
        assertEquals(null, rig.vm.filterState.value.genreTitle)
        rig.close()
    }

    @Test
    fun restoredAnime_withShowAnimeOn_isApplied() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, animeByYear)

        val rig = filterRig(filterRepository = repository, showAnime = true)

        assertEquals(ANIME_GENRE_ID.toString(), rig.calls.single().config.genre)
        rig.close()
    }

    @Test
    fun restoredAnime_onANonFollowPreferenceGrid_isApplied() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(all4k.id, animeByYear)

        val rig = filterRig(config = all4k, filterRepository = repository, showAnime = false)

        assertEquals(ANIME_GENRE_ID.toString(), rig.calls.single().config.genre)
        rig.close()
    }

    @Test
    fun filterReset_ofARestoredFilter_readsTheBaseQueryCachedAndRemovesTheEntry() = runTest {
        val preferences = FakeSharedPreferences()
        val repository = filterRepositoryOn(preferences)
        repository.write(allMovie.id, dramaByYear)
        val rig = filterRig(filterRepository = repository)

        rig.act(ShowAllAction.FilterReset)

        assertEquals(FirstPageCall(allMovie, cacheable = true), rig.calls.last())
        assertEquals(false, preferences.values.containsKey("filter_all_movie"))
        rig.close()
    }

    @Test
    fun restoredAnime_closedUnchanged_keepsTheStoredGenre() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        repository.write(allMovie.id, animeByYear)
        val rig = filterRig(filterRepository = repository, showAnime = false)

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.FilterPanelClosed)

        assertEquals(ANIME_GENRE_ID, repository.read(allMovie.id).genre?.id)
        rig.close()
    }

    @Test
    fun shelfGrid_neverReadsOrWritesTheStore() = runTest {
        val preferences = FakeSharedPreferences()
        preferences.values["filter_popular"] =
            """{"genreId":9,"genreTitle":"Драма","sort":"YEAR","direction":"ASC"}"""
        val popular = SectionConfig(id = "popular", title = "Popular")

        val rig = filterRig(config = popular, filterRepository = filterRepositoryOn(preferences))
        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.FilterPanelClosed, ShowAllAction.FilterReset)

        assertEquals(listOf(FirstPageCall(popular, cacheable = true)), rig.calls.toList())
        assertTrue(preferences.transactions.isEmpty())
        rig.close()
    }

    // endregion

    @Test
    fun filterState_isUnavailableForAShelfConfig() = runTest {
        val rig = filterRig(config = SectionConfig(id = "popular", title = "Popular"))

        assertEquals(ShowAllFilterState.Unavailable, rig.vm.filterState.value)
        rig.close()
    }

    @Test
    fun filterActions_areIgnoredOnAShelfGrid() = runTest {
        val rig = filterRig(config = SectionConfig(id = "popular", title = "Popular"))

        rig.act(
            ShowAllAction.FilterPanelOpened,
            ShowAllAction.SortFieldChosen(SortField.YEAR),
            ShowAllAction.FilterPanelClosed,
            ShowAllAction.FilterReset,
        )

        assertEquals(1, rig.calls.size)
        assertEquals(ShowAllFilterState.Unavailable, rig.vm.filterState.value)
        rig.close()
    }

    @Test
    fun filterPanelClosed_withoutAChange_doesNotRepage() = runTest {
        val rig = filterRig()

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.FilterPanelClosed)
        rig.act(
            ShowAllAction.FilterPanelOpened,
            ShowAllAction.SortFieldChosen(SortField.YEAR),
            ShowAllAction.SortFieldChosen(SortField.UPDATED),
            ShowAllAction.FilterPanelClosed,
        )

        assertEquals(1, rig.calls.size)
        assertEquals(false, rig.vm.filterState.value.panelOpen)
        rig.close()
    }

    @Test
    fun filterPanelClosed_afterSortAndGenreChosen_repagesOnceWithTheFilteredQuery() = runTest {
        val rig = filterRig()

        rig.applyFilter(dramaByYear)

        assertEquals(listOf(allMovie, dramaByYear.applyTo(allMovie)), rig.calls.map { it.config })
        rig.close()
    }

    @Test
    fun filterReset_restoresTheBaseQueryAndRepagesCached() = runTest {
        val rig = filterRig()
        rig.applyFilter(dramaByYear)

        rig.act(ShowAllAction.FilterReset)

        assertEquals(
            listOf(
                FirstPageCall(allMovie, cacheable = true),
                FirstPageCall(dramaByYear.applyTo(allMovie), cacheable = false),
                FirstPageCall(allMovie, cacheable = true),
            ),
            rig.calls.toList(),
        )
        val restored = rig.vm.filterState.value
        assertEquals(
            ShowAllFilterState(
                available = true,
                genres = restored.genres,
                countries = restored.countries,
                yearOptions = restored.yearOptions,
            ),
            restored,
        )
        rig.close()
    }

    @Test
    fun filterReset_withoutAnActiveFilter_doesNothing() = runTest {
        val rig = filterRig()

        rig.act(ShowAllAction.FilterReset)

        assertEquals(1, rig.calls.size)
        rig.close()
    }

    @Test
    fun filterState_summarisesTheAppliedGenreAndSort() = runTest {
        val rig = filterRig()

        rig.act(
            ShowAllAction.FilterPanelOpened,
            ShowAllAction.GenreChosen(drama),
            ShowAllAction.SortFieldChosen(SortField.YEAR),
            ShowAllAction.SortDirectionChosen(SortDirection.ASC),
        )
        val whileEditing = rig.vm.filterState.value
        rig.act(ShowAllAction.FilterPanelClosed)
        val applied = rig.vm.filterState.value

        assertEquals(dramaByYear, whileEditing.draft)
        assertEquals(false, whileEditing.isActive)
        assertEquals(null, whileEditing.genreTitle)
        assertEquals(SortField.UPDATED, whileEditing.sort)
        assertEquals(true, applied.isActive)
        assertEquals("Драма", applied.genreTitle)
        assertEquals(SortField.YEAR, applied.sort)
        assertEquals(SortDirection.ASC, applied.direction)
        rig.close()
    }

    @Test
    fun filterPanelClosed_withAFilter_readsTheFirstPageUncached() = runTest {
        val rig = filterRig()

        rig.applyFilter(dramaByYear)

        assertEquals(listOf(true, false), rig.calls.map { it.cacheable })
        rig.close()
    }

    @Test
    fun filterChange_dropsAnOldFirstPageStillInFlight() = runTest {
        val oldPage = CompletableDeferred<Unit>()
        val filtered = dramaByYear.applyTo(allMovie)
        val rig = filterRig(firstPage = { asked ->
            if (asked == filtered) {
                pageOf(10, 11, current = 1, total = 1)
            } else {
                withContext(NonCancellable) { oldPage.await() }
                pageOf(1, 2, current = 1, total = 1)
            }
        })

        rig.applyFilter(dramaByYear)
        oldPage.complete(Unit)
        rig.run()

        assertEquals(listOf(10, 11), shownIds(rig.vm))
        rig.close()
    }

    @Test
    fun filterChange_dropsAnOldWalkStepStillInFlight() = runTest {
        val oldStep = CompletableDeferred<Unit>()
        val filtered = dramaByYear.applyTo(allMovie)
        val rig = filterRig(firstPage = { asked ->
            if (asked == filtered) pageOf(10, 11, current = 1, total = 1) else pageOf(1, 2, current = 1, total = 3)
        })
        coEvery { interactor.loadPage(allMovie, page = 2) } coAnswers {
            withContext(NonCancellable) { oldStep.await() }
            pageOf(3, 4, current = 2, total = 3)
        }
        rig.act(CommonAction.LoadMore)

        rig.applyFilter(dramaByYear)
        oldStep.complete(Unit)
        rig.run()

        assertEquals(listOf(10, 11), shownIds(rig.vm))
        rig.close()
    }

    @Test
    fun filterChange_dropsAnOldDepthRestoreStillInFlight() = runTest {
        val oldRestore = CompletableDeferred<Unit>()
        val page2Reads = AtomicInteger()
        val filtered = dramaByYear.applyTo(allMovie)
        val rig = filterRig(firstPage = { asked ->
            if (asked == filtered) pageOf(10, 11, current = 1, total = 1) else pageOf(1, 2, current = 1, total = 2)
        })
        coEvery { interactor.loadPage(allMovie, page = 2) } coAnswers {
            if (page2Reads.incrementAndGet() > 1) withContext(NonCancellable) { oldRestore.await() }
            pageOf(3, 4, current = 2, total = 2)
        }
        rig.act(CommonAction.LoadMore)
        assertEquals(listOf(1, 2, 3, 4), shownIds(rig.vm))
        rig.vm.refreshFirstPage()
        rig.run()

        rig.applyFilter(dramaByYear)
        oldRestore.complete(Unit)
        rig.run()

        assertEquals(2, page2Reads.get())
        assertEquals(listOf(10, 11), shownIds(rig.vm))
        rig.close()
    }

    @Test
    fun filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne() = runTest {
        val filteredPage = CompletableDeferred<Unit>()
        val filtered = dramaByYear.applyTo(allMovie)
        val rig = filterRig(firstPage = { asked ->
            if (asked == filtered) {
                filteredPage.await()
                pageOf(10, 11, current = 1, total = 3)
            } else {
                pageOf(1, 2, current = 1, total = 3)
            }
        })
        coEvery { interactor.loadPage(allMovie, page = 2) } returns pageOf(3, 4, current = 2, total = 3)
        coEvery { interactor.loadPage(filtered, page = 2) } returns pageOf(12, 13, current = 2, total = 3)
        rig.act(CommonAction.LoadMore)

        rig.applyFilter(dramaByYear)
        rig.vm.refreshFirstPage()
        rig.run()
        filteredPage.complete(Unit)
        rig.run()

        coVerify(exactly = 0) { interactor.loadPage(filtered, page = 2) }
        assertEquals(listOf(10, 11), shownIds(rig.vm))
        rig.close()
    }

    @Test
    fun filterChange_marksTheNewListWithAnotherQueryGeneration() = runTest {
        val rig = filterRig()
        val before = (rig.vm.testStateValue as ShowAllViewState.Content).queryGeneration

        rig.applyFilter(dramaByYear)
        val applied = (rig.vm.testStateValue as ShowAllViewState.Content).queryGeneration
        rig.act(ShowAllAction.FilterReset)
        val reset = (rig.vm.testStateValue as ShowAllViewState.Content).queryGeneration

        assertEquals(3, setOf(before, applied, reset).size)
        rig.close()
    }

    @Test
    fun genreList_withAnimeHidden_omitsAnime() = runTest {
        val rig = filterRig()
        every { interactor.showAnimeEnabled } returns false
        coEvery { genreInteractor.getGenres("movie") } returns
            Result.success(listOf(drama, Genre(ANIME_GENRE_ID, "Аниме")))

        rig.act(ShowAllAction.FilterPanelOpened)

        assertEquals(FilterGenres.Loaded(listOf(drama)), rig.vm.filterState.value.genres)
        rig.close()
    }

    @Test
    fun genreList_withAnimeShown_keepsAnime() = runTest {
        val rig = filterRig()
        val anime = Genre(ANIME_GENRE_ID, "Аниме")
        coEvery { genreInteractor.getGenres("movie") } returns Result.success(listOf(drama, anime))

        rig.act(ShowAllAction.FilterPanelOpened)

        assertEquals(FilterGenres.Loaded(listOf(drama, anime)), rig.vm.filterState.value.genres)
        rig.close()
    }

    @Test
    fun genreLoadFailure_showsFailedAndKeepsTheGrid() = runTest {
        val rig = filterRig()
        coEvery { genreInteractor.getGenres("movie") } returns Result.failure(IllegalStateException("down"))

        rig.act(ShowAllAction.FilterPanelOpened)

        assertEquals(FilterGenres.Failed, rig.vm.filterState.value.genres)
        assertEquals(listOf(1, 2), shownIds(rig.vm))
        assertEquals(1, rig.calls.size)
        rig.close()
    }

    @Test
    fun genresRetry_loadsAgainAfterAFailure() = runTest {
        val rig = filterRig()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { genreInteractor.getGenres("movie") } coAnswers {
            reads += 1
            if (reads == 1) {
                Result.failure(IllegalStateException("down"))
            } else {
                release.await()
                Result.success(listOf(drama))
            }
        }
        rig.act(ShowAllAction.FilterPanelOpened)
        assertEquals(FilterGenres.Failed, rig.vm.filterState.value.genres)

        rig.act(ShowAllAction.GenresRetry, ShowAllAction.GenresRetry)
        assertEquals(FilterGenres.Loading, rig.vm.filterState.value.genres)
        release.complete(Unit)
        rig.run()

        assertEquals(2, reads)
        assertEquals(FilterGenres.Loaded(listOf(drama)), rig.vm.filterState.value.genres)
        rig.close()
    }

    @Test
    fun genreList_forA4kGrid_requestsThe4kType() = runTest {
        val rig = filterRig(config = all4k)

        rig.act(ShowAllAction.FilterPanelOpened)

        coVerify(exactly = 1) { genreInteractor.getGenres("4k") }
        rig.close()
    }

    // region country, year and status

    private val seriesGrids get() = listOf(allSerial, allDocSerial, allTvShow)

    @Test
    fun countryChosen_panelClosed_readsTheCountryQueryUncachedAndSummarisesIt() = runTest {
        val rig = filterRig()

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.CountryChosen(usa), ShowAllAction.FilterPanelClosed)

        val call = rig.calls.last()
        assertEquals(1, call.config.country)
        assertEquals("movie", call.config.type)
        assertEquals(false, call.cacheable)
        assertEquals("США", rig.vm.filterState.value.countryTitle)
        rig.close()
    }

    @Test
    fun yearChosen_panelClosed_sendsThePresetAndSummarisesIt() = runTest {
        val rig = filterRig()

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.YearChosen(2010..2019), ShowAllAction.FilterPanelClosed)

        assertEquals("2010-2019", rig.calls.last().config.year)
        assertEquals(2010..2019, rig.vm.filterState.value.yearRange)
        rig.close()
    }

    @Test
    fun statusChosen_onSeriesGrids_sendsFinished() = runTest {
        seriesGrids.forEach { config ->
            val rig = filterRig(config = config)

            rig.act(
                ShowAllAction.FilterPanelOpened,
                ShowAllAction.StatusChosen(true),
                ShowAllAction.FilterPanelClosed,
            )

            assertTrue(rig.vm.filterState.value.statusAvailable, config.id)
            assertEquals(true, rig.calls.last().config.finished, config.id)
            rig.close()
        }
    }

    @Test
    fun statusChosen_anyStatusOnASeriesGrid_clearsFinished() = runTest {
        val rig = filterRig(config = allSerial)

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.StatusChosen(true), ShowAllAction.FilterPanelClosed)
        assertEquals(true, rig.calls.last().config.finished)
        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.StatusChosen(false), ShowAllAction.FilterPanelClosed)

        assertEquals(null, rig.calls.last().config.finished)
        assertEquals(null, rig.vm.filterState.value.draft.finished)
        rig.close()
    }

    @Test
    fun statusDoor_isUnavailableOnMovieDocumovieConcertAnd4k() = runTest {
        listOf(allMovie, allDocMovie, allConcert, all4k).forEach { config ->
            val rig = filterRig(config = config)

            assertEquals(false, rig.vm.filterState.value.statusAvailable, config.id)
            rig.close()
        }
    }

    @Test
    fun statusChosen_onAMovieGrid_isIgnored() = runTest {
        val rig = filterRig()

        rig.act(ShowAllAction.FilterPanelOpened, ShowAllAction.StatusChosen(true), ShowAllAction.FilterPanelClosed)

        assertEquals(1, rig.calls.size)
        assertTrue(rig.calls.all { it.config.finished == null })
        rig.close()
    }

    @Test
    fun filterPanelClosed_withEveryFilterSet_readsOneQueryCarryingAllOfThem() = runTest {
        val rig = filterRig(config = allSerial)

        rig.applyFilter(
            ContentFilter(
                genre = drama,
                country = usa,
                sort = SortField.YEAR,
                sortDirection = SortDirection.ASC,
                yearRange = 2010..2019,
                finished = true,
            ),
        )

        assertEquals(2, rig.calls.size)
        val config = rig.calls.last().config
        assertEquals("9", config.genre)
        assertEquals("year", config.sort)
        assertEquals(1, config.country)
        assertEquals("2010-2019", config.year)
        assertEquals(true, config.finished)
        rig.close()
    }

    @Test
    fun restoredCountryYearAndStatus_headerShowsSummaryBeforeTheFirstPage() = runTest {
        val repository = filterRepositoryOn(FakeSharedPreferences())
        val stored = ContentFilter(country = usa, yearRange = 2010..2019, finished = true)
        repository.write(allSerial.id, stored)

        val headerOnly = createVM(config = allSerial, filterRepository = repository).filterState.value
        val rig = filterRig(config = allSerial, filterRepository = repository)

        assertEquals("США", headerOnly.countryTitle)
        assertEquals(2010..2019, headerOnly.yearRange)
        assertTrue(headerOnly.finishedOnly)
        assertEquals(listOf(FirstPageCall(stored.applyTo(allSerial), cacheable = false)), rig.calls.toList())
        coVerify(exactly = 0) { countryInteractor.getCountries() }
        rig.close()
    }

    @Test
    fun filterReset_clearsCountryYearAndStatusRemovesTheEntryAndReadsCached() = runTest {
        val preferences = FakeSharedPreferences()
        val rig = filterRig(config = allSerial, filterRepository = filterRepositoryOn(preferences))
        rig.applyFilter(ContentFilter(country = usa, yearRange = 2010..2019, finished = true))
        assertTrue(preferences.values.containsKey("filter_all_serial"))

        rig.act(ShowAllAction.FilterReset)

        val state = rig.vm.filterState.value
        assertEquals(null, state.countryTitle)
        assertEquals(null, state.yearRange)
        assertEquals(false, state.finishedOnly)
        assertEquals(false, preferences.values.containsKey("filter_all_serial"))
        assertEquals(FirstPageCall(allSerial, cacheable = true), rig.calls.last())
        rig.close()
    }

    @Test
    fun countryLoadFailure_showsFailedAndKeepsGenresUsable() = runTest {
        val rig = filterRig()
        coEvery { countryInteractor.getCountries() } returns Result.failure(IllegalStateException("down"))

        rig.act(ShowAllAction.FilterPanelOpened)

        val state = rig.vm.filterState.value
        assertEquals(FilterCountries.Failed, state.countries)
        assertTrue(state.genres is FilterGenres.Loaded)
        assertEquals(listOf(1, 2), shownIds(rig.vm))
        assertEquals(1, rig.calls.size)
        rig.close()
    }

    @Test
    fun countriesRetry_loadsAgainAfterAFailure() = runTest {
        val rig = filterRig()
        val release = CompletableDeferred<Unit>()
        var reads = 0
        coEvery { countryInteractor.getCountries() } coAnswers {
            reads += 1
            if (reads == 1) {
                Result.failure(IllegalStateException("down"))
            } else {
                release.await()
                Result.success(listOf(usa))
            }
        }
        rig.act(ShowAllAction.FilterPanelOpened)
        assertEquals(FilterCountries.Failed, rig.vm.filterState.value.countries)

        rig.act(ShowAllAction.CountriesRetry, ShowAllAction.CountriesRetry)
        assertEquals(FilterCountries.Loading, rig.vm.filterState.value.countries)
        release.complete(Unit)
        rig.run()

        assertEquals(2, reads)
        assertEquals(FilterCountries.Loaded(listOf(usa)), rig.vm.filterState.value.countries)
        rig.close()
    }

    // endregion

    @Test
    fun filteredEmptyPage_keepsTheFilterActive() = runTest {
        val filtered = dramaByYear.applyTo(allMovie)
        val rig = filterRig(firstPage = { asked ->
            if (asked == filtered) emptyPage(current = 1, total = 1) else pageOf(1, 2, current = 1, total = 1)
        })

        rig.applyFilter(dramaByYear)

        assertEquals(ShowAllViewState.Empty, rig.vm.testStateValue)
        assertTrue(rig.vm.filterState.value.isActive)
        rig.close()
    }

    // endregion

    private fun shownIds(vm: ShowAllVM): List<Int> =
        (vm.testStateValue as? ShowAllViewState.Content)?.items?.map { it.id }.orEmpty()

    private fun pageOf(vararg ids: Int, current: Int, total: Int) = PaginatedResponse(
        items = ids.map(::item),
        pagination = Pagination(current = current, perpage = ids.size, total = total),
    )

    /**
     * Wires the cached-first-page flow onto the page loads these tests stub: one fresh emission per
     * subscription, which is what the interactor's own loader produces when nothing is stored.
     */
    private fun servesFirstPageFromTheNetwork(interactor: ContentListInteractor) {
        every { interactor.observeFirstPage(any(), any(), any()) } answers {
            val config = firstArg<SectionConfig>()
            flow { emit(Cached.Value(interactor.loadPage(config, page = 1), isStale = false)) }
        }
    }

    private fun createVM(
        config: SectionConfig = SectionConfig(id = "popular", title = "Popular"),
        paginator: Paginator.Store<Item> = Paginator.Store { old, new -> old.id == new.id },
        mapper: VideoItemUIMapper = mockk(relaxed = true),
        contentListInteractor: ContentListInteractor = interactor,
        pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
        filterRepository: CatalogueFilterRepository = filterRepositoryOn(FakeSharedPreferences()),
    ) = ShowAllVM(
        paginator = paginator,
        config = config,
        interactor = contentListInteractor,
        genreInteractor = genreInteractor,
        countryInteractor = countryInteractor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        router = router,
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
        filterRepository = filterRepository,
        pagingCoroutineContext = pagingCoroutineContext,
    )

    private fun filterRepositoryOn(preferences: FakeSharedPreferences): CatalogueFilterRepository {
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), Context.MODE_PRIVATE) } returns preferences.sharedPreferences
        return CatalogueFilterRepository(context)
    }

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
