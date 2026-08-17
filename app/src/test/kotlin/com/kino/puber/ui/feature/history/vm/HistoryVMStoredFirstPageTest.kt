package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.util.stubContentPageCache
import com.kino.puber.util.stubNavigationPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.io.IOException

/**
 * The stored first page is a publication, not a load: it is drawn while the depth walk is out and
 * the walk owns every row from the moment it lands.
 */
internal class HistoryVMStoredFirstPageTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
        private const val SETTLE_MILLIS = 50L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var interactor: HistoryInteractor
    private lateinit var errorHandler: ErrorHandler

    @BeforeEach
    fun setUp() {
        api = mockk()
        interactor = spyk(
            HistoryInteractor(
                api = api,
                itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
                navigationPreferencesRepository = stubNavigationPreferences(),
                contentPageCache = stubContentPageCache(),
            ),
        )
        errorHandler = mockk(relaxed = true)
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
    }

    @Test
    fun theStoredFirstPageIsDrawnBeforeTheDepthWalkFinishes() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(listOf(movie(1))), isStale = true),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM().also(HistoryVM::testOnStart)

        assertEquals(listOf(1), awaitContent(vm).itemIds())

        walk.complete(page(listOf(movie(2))))

        assertEquals(listOf(2), awaitContent(vm) { it.itemIds() == listOf(2) }.itemIds())
        vm.testCancelScope()
    }

    @Test
    fun aStoredPageArrivingAfterTheWalkIsNotDrawnOverIt() {
        val walkPublished = CompletableDeferred<Unit>()
        every { interactor.observeFirstPage(any()) } returns flow {
            walkPublished.await()
            emit(Cached.Value(page(listOf(movie(1))), isStale = true))
        }
        coEvery { api.getHistoryData(1) } returns Result.success(page(listOf(movie(2))))
        val vm = createVM().also(HistoryVM::testOnStart)
        awaitContent(vm) { it.itemIds() == listOf(2) }

        walkPublished.complete(Unit)

        Thread.sleep(SETTLE_MILLIS)
        assertEquals(listOf(2), awaitContent(vm).itemIds())
        vm.testCancelScope()
    }

    /**
     * The list keys its rows by media identity, so the two records a re-watch leaves on one server
     * page have to collapse into one row here exactly as they do on the walk.
     */
    @Test
    fun repeatsWithinTheStoredPageCollapseIntoOneRow() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(listOf(movie(1), movie(1), movie(2))), isStale = true),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM().also(HistoryVM::testOnStart)

        assertEquals(listOf(1, 2), awaitContent(vm).itemIds())
        vm.testCancelScope()
    }

    @Test
    fun anEmptyStoredPageLeavesTheScreenLoading() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(emptyList()), isStale = true),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM().also(HistoryVM::testOnStart)

        Thread.sleep(SETTLE_MILLIS)
        assertEquals(HistoryViewState.Loading, vm.testStateValue)
        vm.testCancelScope()
    }

    @Test
    fun aFailedRevalidationKeepsTheStoredRowsAndSaysNothing() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(listOf(movie(1))), isStale = true),
            Cached.RefreshFailed(IOException("refresh failure")),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM().also(HistoryVM::testOnStart)

        val content = awaitContent(vm)

        assertEquals(listOf(1), content.itemIds())
        assertNull(vm.testMessageValue)
        vm.testCancelScope()
    }

    private fun createVM(): HistoryVM {
        return HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = interactor,
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
            router = mockk<AppRouter>(relaxed = true),
            errorHandler = errorHandler,
        )
    }

    private fun awaitContent(
        vm: HistoryVM,
        predicate: (HistoryViewState.Content) -> Boolean = { true },
    ): HistoryViewState.Content {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (state is HistoryViewState.Content && predicate(state)) return state
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History content; last=${vm.testStateValue}")
    }

    private fun HistoryViewState.Content.itemIds(): List<Int> = items.map(HistoryItemUIState::itemId)

    private fun page(items: List<History>): PaginatedResponse<History> {
        return PaginatedResponse(
            items = items,
            pagination = Pagination(
                current = 1,
                perpage = 20,
                total = 1,
                totalItems = items.size,
            ),
        )
    }

    private fun movie(itemId: Int): History {
        return History(
            item = Item(
                id = itemId,
                title = "Synthetic movie $itemId",
                type = ItemType.MOVIE,
            ),
            video = Video(
                id = itemId * 100,
                number = itemId,
            ),
        )
    }
}
