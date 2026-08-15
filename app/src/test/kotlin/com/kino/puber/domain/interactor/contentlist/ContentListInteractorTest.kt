package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.models.ApiResponse
import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.api.models.isFullyWatched
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import com.kino.puber.ui.feature.main.model.TabType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Shared setup for the interactor's tests. They live in more than one class because the interactor
 * answers two quite separate questions — how a filtered page is assembled and cached, and how a
 * fresh section pages across several shortcut types — and one class holding both had outgrown what
 * anyone can read at once.
 */
internal open class ContentListInteractorTestFixture {

    protected val api = mockk<KinoPubApiClient>()
    protected val contentPreferences = MutableStateFlow(defaultContentPreferences())
    protected val displaySettingsChanges = MutableSharedFlow<Unit>()
    protected val navigationPreferencesRepository = mockk<NavigationPreferencesRepository> {
        every { contentPreferences } returns this@ContentListInteractorTestFixture.contentPreferences
        every { displaySettingsChanges } returns this@ContentListInteractorTestFixture.displaySettingsChanges
    }
    /** Item ids the local watch-state index reports as finished, on top of the items' own fields. */
    protected val indexedAsWatched = mutableSetOf<Int>()
    protected val settledWatchStateChanges = MutableSharedFlow<Long>()
    protected val watchStateRepository = mockk<WatchStateRepository> {
        every { isFullyWatched(any()) } answers {
            val item = firstArg<Item>()
            item.isFullyWatched() || item.id in indexedAsWatched
        }
        every { version } returns MutableStateFlow(0L)
        every { settledChanges } returns this@ContentListInteractorTestFixture.settledWatchStateChanges
    }
    protected val interactor =
        ContentListInteractor(api, navigationPreferencesRepository, watchStateRepository)

    @BeforeEach
    fun setup() {
        mockkObject(KinoPubConfig)
        every { KinoPubConfig.CURRENT_API_DOMAIN } returns "unit.test"
        contentPreferences.value = defaultContentPreferences()
        indexedAsWatched.clear()
        interactor.invalidateFirstPageCache()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(KinoPubConfig)
    }

    protected fun config(filterMode: AnimeFilterMode) = SectionConfig(
        id = "section_${filterMode.name}",
        title = filterMode.name,
        type = "movie",
        sort = "updated",
        animeFilterMode = filterMode,
    )

    protected fun page(
        vararg items: Item,
        current: Int = 1,
        total: Int = 1,
        perpage: Int = items.size,
    ) = PaginatedResponse(
        items = items.toList(),
        pagination = Pagination(current = current, perpage = perpage, total = total),
    )

    protected fun item(
        id: Int,
        title: String,
        vararg genreIds: Int,
        type: ItemType = ItemType.MOVIE,
        watched: Int? = null,
        new: Int? = null,
        total: Int? = null,
    ) = Item(
        id = id,
        title = title,
        type = type,
        genres = genreIds
            .map { genreId -> Genre(id = genreId, title = "Genre $genreId") }
            .takeIf(List<Genre>::isNotEmpty),
        watched = watched,
        new = new,
        total = total,
    )

    /** Mirrors `ContentListInteractor.MAX_PAGES_PER_STEP`, which is private to the interactor. */
    protected val maxPagesPerStepUnderTest = 5

    protected fun defaultContentPreferences() = ContentPreferences(
        showCartoonsTab = false,
        showAnimeTab = false,
        showAnime = true,
        hideWatched = false,
        showWatchedIndicators = true,
    )
}

internal class ContentListInteractorTest : ContentListInteractorTestFixture() {

    @Test
    fun invalidateFirstPageCache_clearsCachedFirstPages() = runTest {
        val config = SectionConfig(id = "fresh", title = "Fresh", type = "movie", sort = "updated")
        val firstPage = page(item(id = 1, title = "Before"))
        val refreshedPage = page(item(id = 2, title = "After"))
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(firstPage) andThen
            Result.success(refreshedPage)

        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        assertEquals(firstPage, interactor.loadPage(config, page = 1))
        interactor.invalidateFirstPageCache()
        assertEquals(refreshedPage, interactor.loadPage(config, page = 1))

        coVerify(exactly = 2) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun noneMode_returnsUnfilteredPageWithOneServerCall() = runTest {
        val config = config(AnimeFilterMode.None)
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            item(id = 2, title = "Unknown"),
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        assertEquals(response, interactor.loadPage(config, page = 1))

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun followPreference_whenAnimeIsShown_returnsUnfilteredPageWithOneServerCall() = runTest {
        val config = config(AnimeFilterMode.FollowPreference)
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            item(id = 2, title = "Movie"),
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        assertEquals(response, interactor.loadPage(config, page = 1))

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun followPreference_whenAnimeIsHidden_excludesAnimeAndRetainsMissingGenres() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val config = config(AnimeFilterMode.FollowPreference)
        val visible = item(id = 2, title = "Movie")
        val response = page(
            item(id = 1, title = "Anime", ANIME_GENRE_ID),
            visible,
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(response.pagination, result.pagination)
    }

    @Test
    fun excludeMode_removesMixedCartoonAnimeItems() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val cartoon = item(id = 2, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(
                    id = 1,
                    title = "Anime cartoon",
                    CARTOON_GENRE_ID,
                    ANIME_GENRE_ID,
                ),
                cartoon,
            )
        )

        assertEquals(listOf(cartoon), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun onlyMode_retainsAnimeAndDropsUnclassifiedItems() = runTest {
        val config = config(AnimeFilterMode.Only)
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                anime,
                item(id = 2, title = "Movie"),
            )
        )

        assertEquals(listOf(anime), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun heroConfig_routesShortcutRequestWithGenreAndRetainsOnlyAnime() = runTest {
        val config = TabTypeConfig.heroConfigsFor(TabType.Anime)
            .single { it.type == "serial" }
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("hot", "serial", 1, ANIME_GENRE_ID.toString())
        } returns Result.success(
            page(
                anime,
                item(id = 2, title = "Movie"),
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(anime), result.items)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "serial", 1, ANIME_GENRE_ID.toString())
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun cartoonHeroConfig_refillsAcrossShortcutPagesAndSuppressesDuplicateIds() = runTest {
        val config = TabTypeConfig.heroConfigsFor(TabType.Cartoons)
            .single { it.type == "movie" }
        val firstCartoon = item(id = 2, title = "Cartoon 1", CARTOON_GENRE_ID)
        val secondCartoon = item(id = 3, title = "Cartoon 2", CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("hot", "movie", 1, CARTOON_GENRE_ID.toString())
        } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                firstCartoon,
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("hot", "movie", 2, CARTOON_GENRE_ID.toString())
        } returns Result.success(
            page(
                firstCartoon.copy(title = "Duplicate"),
                secondCartoon,
                current = 2,
                total = 2,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstCartoon, secondCartoon), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "movie", 1, CARTOON_GENRE_ID.toString())
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("hot", "movie", 2, CARTOON_GENRE_ID.toString())
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun filteredPage_refillsBatchAcrossServerPagesAndReturnsLastConsumedPagination() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        val config = config(AnimeFilterMode.FollowPreference)
        val firstVisible = item(id = 2, title = "Movie 1")
        val secondVisible = item(id = 3, title = "Movie 2")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                firstVisible,
                current = 1,
                total = 3,
                perpage = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 4, title = "Anime 2", ANIME_GENRE_ID),
                secondVisible,
                current = 2,
                total = 3,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstVisible, secondVisible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 3, null, null) }
    }

    @Test
    fun filteredPage_failsBoundedly_whenCurrentDoesNotAdvanceToRequestedPage() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                current = 0,
                total = 3,
                perpage = 1,
            )
        )

        val error = try {
            interactor.loadPage(config, page = 1)
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(
            "Content pagination current 0 did not match requested page 1",
            error?.message,
        )
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 2, null, null) }
    }

    @Test
    fun filteredPage_failsBoundedly_whenFollowUpCurrentDoesNotAdvanceToRequestedPage() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime", ANIME_GENRE_ID),
                current = 1,
                total = 3,
                perpage = 1,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 2, title = "Anime 2", ANIME_GENRE_ID),
                current = 1,
                total = 3,
                perpage = 1,
            )
        )

        val error = try {
            interactor.loadPage(config, page = 1)
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(
            "Content pagination current 1 did not match requested page 2",
            error?.message,
        )
        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", 3, null, null) }
    }

    @Test
    fun filteredPage_preservesFinalConsumedPageOverflow() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val firstVisible = item(id = 1, title = "Cartoon 1", CARTOON_GENRE_ID)
        val secondVisible = item(id = 2, title = "Cartoon 2", CARTOON_GENRE_ID)
        val overflowVisible = item(id = 3, title = "Cartoon 3", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                firstVisible,
                item(id = 4, title = "Anime", ANIME_GENRE_ID),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                secondVisible,
                overflowVisible,
                current = 2,
                total = 2,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(firstVisible, secondVisible, overflowVisible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 1) { api.getItems("movie", "updated", 2, null, null) }
    }

    @Test
    fun filteredPages_continueBeyondFivePagesUntilVisibleBatchIsRefilled() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        (1..6).forEach { pageNumber ->
            coEvery {
                api.getItems("movie", "updated", pageNumber, null, null)
            } returns Result.success(
                page(
                    item(id = pageNumber, title = "Anime $pageNumber", ANIME_GENRE_ID),
                    current = pageNumber,
                    total = 7,
                    perpage = 1,
                )
            )
        }
        val visible = item(id = 7, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 7, null, null) } returns Result.success(
            page(visible, current = 7, total = 7, perpage = 1)
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(7, result.pagination.current)
        (1..7).forEach { pageNumber ->
            coVerify(exactly = 1) {
                api.getItems("movie", "updated", pageNumber, null, null)
            }
        }
    }

    @Test
    fun filteredPages_stopAtServerEndAndSuppressDuplicateIds() = runTest {
        val config = config(AnimeFilterMode.Exclude)
        val visible = item(id = 10, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Anime 1", ANIME_GENRE_ID),
                visible,
                current = 1,
                total = 2,
                perpage = 3,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                visible.copy(title = "Duplicate"),
                item(id = 2, title = "Anime 2", ANIME_GENRE_ID),
                current = 2,
                total = 2,
                perpage = 3,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(visible), result.items)
        assertEquals(2, result.pagination.current)
        coVerify(exactly = 2) { api.getItems("movie", "updated", any(), null, null) }
    }

    @Test
    fun firstPageCache_isSeparatedByFilterModeAndAnimePreference() = runTest {
        val followPreference = config(AnimeFilterMode.FollowPreference)
        val exclude = followPreference.copy(animeFilterMode = AnimeFilterMode.Exclude)
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        val movie = item(id = 2, title = "Movie")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns
            Result.success(page(anime)) andThen
            Result.success(page(movie)) andThen
            Result.success(page(movie))

        assertEquals(listOf(anime), interactor.loadPage(followPreference, page = 1).items)
        contentPreferences.value = defaultContentPreferences().copy(showAnime = false)
        assertEquals(listOf(movie), interactor.loadPage(followPreference, page = 1).items)
        assertEquals(listOf(movie), interactor.loadPage(exclude, page = 1).items)

        coVerify(exactly = 3) { api.getItems("movie", "updated", 1, null, null) }
    }

    // region hide watched

    @Test
    fun hideWatchedDisabled_keepsWatchedItemsWithOneServerCall() = runTest {
        val config = config(AnimeFilterMode.None)
        val response = page(
            item(id = 1, title = "Watched", watched = 1),
            item(id = 2, title = "Fresh"),
        )
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(response)

        assertEquals(response, interactor.loadPage(config, page = 1))

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null) }
    }

    @Test
    fun hideWatchedEnabled_dropsFullyWatchedItems() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        val config = config(AnimeFilterMode.None)
        val fresh = item(id = 2, title = "Fresh")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Watched movie", watched = 1),
                fresh,
                item(id = 3, title = "Finished series", type = ItemType.SERIAL, watched = 10, new = 0),
            )
        )

        assertEquals(listOf(fresh), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun hideWatchedEnabled_keepsPartiallyWatchedSeries() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        val config = config(AnimeFilterMode.None)
        val partial = item(id = 1, title = "Half seen", type = ItemType.SERIAL, watched = 4, new = 6, total = 10)
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(page(partial))

        assertEquals(listOf(partial), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun hideWatchedEnabled_fetchesFurtherPagesToFillThePage() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        val config = config(AnimeFilterMode.None)
        val firstVisible = item(id = 2, title = "Fresh 1")
        val secondVisible = item(id = 4, title = "Fresh 2")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Watched", watched = 1),
                firstVisible,
                current = 1,
                total = 3,
                perpage = 2,
            )
        )
        coEvery { api.getItems("movie", "updated", 2, null, null) } returns Result.success(
            page(
                item(id = 3, title = "Watched", watched = 1),
                secondVisible,
                current = 2,
                total = 3,
                perpage = 2,
            )
        )

        assertEquals(
            listOf(firstVisible, secondVisible),
            interactor.loadPage(config, page = 1).items,
        )
    }

    @Test
    fun hideWatchedEnabled_stopsAfterPageBudgetAndReturnsShortPage() = runTest {
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        val config = config(AnimeFilterMode.None)
        val onlyVisible = item(id = 1, title = "Fresh")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                onlyVisible,
                item(id = 2, title = "Watched", watched = 1),
                current = 1,
                total = maxPagesPerStepUnderTest + 2,
                perpage = 2,
            )
        )
        (2..maxPagesPerStepUnderTest + 2).forEach { pageNumber ->
            coEvery { api.getItems("movie", "updated", pageNumber, null, null) } returns Result.success(
                page(
                    item(id = pageNumber * 10, title = "Watched $pageNumber", watched = 1),
                    item(id = pageNumber * 10 + 1, title = "Watched $pageNumber b", watched = 1),
                    current = pageNumber,
                    total = maxPagesPerStepUnderTest + 2,
                    perpage = 2,
                )
            )
        }

        assertEquals(listOf(onlyVisible), interactor.loadPage(config, page = 1).items)

        coVerify(exactly = 1) { api.getItems("movie", "updated", maxPagesPerStepUnderTest, null, null) }
        coVerify(exactly = 0) { api.getItems("movie", "updated", maxPagesPerStepUnderTest + 1, null, null) }
    }

    @Test
    fun hideWatchedEnabled_stopsAtTheBudgetAndLeavesTheRestToTheCaller() = runTest {
        // Walking past a page that hid everything is the paginator's job now. One load stops at its
        // budget and hands back an empty page that still reports pages behind it — proof enough
        // that the list is not over, without spending a catalogue to find something visible.
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        val config = config(AnimeFilterMode.None)
        val lastPage = maxPagesPerStepUnderTest + 2
        val visible = item(id = 999, title = "Fresh at the end")
        (1 until lastPage).forEach { pageNumber ->
            coEvery { api.getItems("movie", "updated", pageNumber, null, null) } returns Result.success(
                page(
                    item(id = pageNumber * 10, title = "Watched $pageNumber", watched = 1),
                    current = pageNumber,
                    total = lastPage,
                    perpage = 1,
                )
            )
        }
        coEvery { api.getItems("movie", "updated", lastPage, null, null) } returns Result.success(
            page(visible, current = lastPage, total = lastPage, perpage = 1)
        )

        val response = interactor.loadPage(config, page = 1)

        assertTrue(response.items.isEmpty())
        assertTrue(response.pagination.current < response.pagination.total)
        coVerify(exactly = 0) { api.getItems("movie", "updated", lastPage, null, null) }
    }

    @Test
    fun hideWatchedEnabled_dropsItemsKnownOnlyToTheLocalIndex() = runTest {
        // The catalogue endpoints return no watch fields at all, so this is the real-world case.
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        indexedAsWatched += 1
        val config = config(AnimeFilterMode.None)
        val fresh = item(id = 2, title = "Fresh")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(
            page(
                item(id = 1, title = "Watched only per local index", type = ItemType.SERIAL),
                fresh,
            )
        )

        assertEquals(listOf(fresh), interactor.loadPage(config, page = 1).items)
    }

    @Test
    fun displaySettingsChanges_areForwardedFromThePreferences() = runTest {
        val emissions = mutableListOf<Unit>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            interactor.displaySettingsChanges.collect(emissions::add)
        }

        displaySettingsChanges.emit(Unit)
        runCurrent()

        assertEquals(1, emissions.size)
        collector.cancel()
    }

    @Test
    fun hideWatchedToggle_doesNotReuseTheCachedFirstPage() = runTest {
        val config = config(AnimeFilterMode.None)
        val watched = item(id = 1, title = "Watched", watched = 1)
        val fresh = item(id = 2, title = "Fresh")
        coEvery { api.getItems("movie", "updated", 1, null, null) } returns Result.success(page(watched, fresh))

        assertEquals(listOf(watched, fresh), interactor.loadPage(config, page = 1).items)
        contentPreferences.value = defaultContentPreferences().copy(hideWatched = true)
        assertEquals(listOf(fresh), interactor.loadPage(config, page = 1).items)
    }

    // endregion

    @Test
    fun invalidateItemDetails_clearsCachedItemDetails() = runTest {
        val firstItem = item(id = 42, title = "Before")
        val refreshedItem = item(id = 42, title = "After")
        coEvery { api.getItemDetails(42) } returns Result.success(ApiResponse(item = firstItem)) andThen
            Result.success(ApiResponse(item = refreshedItem))

        assertEquals(firstItem, interactor.getItemDetails(42))
        assertEquals(firstItem, interactor.getItemDetails(42))
        interactor.invalidateItemDetails(42)
        assertEquals(refreshedItem, interactor.getItemDetails(42))

        coVerify(exactly = 2) { api.getItemDetails(42) }
    }
}

internal class ContentListInteractorFreshSectionTest : ContentListInteractorTestFixture() {

    @Test
    fun freshCartoonConfig_routesTypedFreshRequestsWithoutServerGenre() = runTest {
        val config = TabTypeConfig.sectionsFor(TabType.Cartoons)
            .single { it.id == "fresh_cartoon" }
        val cartoon = item(id = 1, title = "Cartoon", CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(
            page(
                cartoon,
                current = 1,
                total = 1,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(
            page(
                item(id = 2, title = "Anime", CARTOON_GENRE_ID, ANIME_GENRE_ID),
                current = 1,
                total = 1,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(cartoon), result.items)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun freshAnimeConfig_routesTypedFreshRequestsWithoutServerGenre() = runTest {
        val config = TabTypeConfig.sectionsFor(TabType.Anime)
            .single { it.id == "fresh_anime" }
        val anime = item(id = 1, title = "Anime", ANIME_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(
            page(
                anime,
                current = 1,
                total = 1,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(
            page(
                item(id = 2, title = "Cartoon", CARTOON_GENRE_ID),
                current = 1,
                total = 1,
                perpage = 2,
            )
        )

        val result = interactor.loadPage(config, page = 1)

        assertEquals(listOf(anime), result.items)
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 1) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun freshFirstPage_bypassesSharedCacheAndResetsSourcePaging() = runTest {
        val config = TabTypeConfig.sectionsFor(TabType.Cartoons)
            .single { it.id == "fresh_cartoon" }
        val firstMovie = item(id = 1, title = "First movie", CARTOON_GENRE_ID)
        val secondMovie = item(id = 2, title = "Second movie", CARTOON_GENRE_ID)
        val firstSerial = item(id = 3, title = "First serial", CARTOON_GENRE_ID)
        val secondSerial = item(id = 4, title = "Second serial", CARTOON_GENRE_ID)
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(page(firstMovie, perpage = 2)) andThen
            Result.success(page(secondMovie, perpage = 2))
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(page(firstSerial, perpage = 2)) andThen
            Result.success(page(secondSerial, perpage = 2))

        assertEquals(
            listOf(firstMovie, firstSerial),
            interactor.loadPage(config, page = 1).items,
        )
        assertEquals(
            listOf(secondMovie, secondSerial),
            interactor.loadPage(config, page = 1).items,
        )

        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        }
        coVerify(exactly = 2) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        }
        coVerify(exactly = 0) { api.getItems(any(), any(), any(), any(), any()) }
    }

    @Test
    fun invalidateFirstPageCache_discardsFreshSourcePagingState() = runTest {
        val config = TabTypeConfig.sectionsFor(TabType.Cartoons)
            .single { it.id == "fresh_cartoon" }
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 1, null)
        } returns Result.success(
            page(
                item(id = 1, title = "Movie", CARTOON_GENRE_ID),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )
        coEvery {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 1, null)
        } returns Result.success(
            page(
                item(id = 2, title = "Serial", CARTOON_GENRE_ID),
                current = 1,
                total = 2,
                perpage = 2,
            )
        )

        interactor.loadPage(config, page = 1)
        interactor.invalidateFirstPageCache()
        val error = runCatching { interactor.loadPage(config, page = 2) }.exceptionOrNull()

        assertEquals("Fresh logical page 2 did not match expected page 1", error?.message)
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", ItemType.MOVIE.value, 2, null)
        }
        coVerify(exactly = 0) {
            api.getItemsByShortcut("fresh", ItemType.SERIAL.value, 2, null)
        }
    }
}
