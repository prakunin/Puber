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
import com.kino.puber.util.stubContentCache
import com.kino.puber.util.stubNavigationPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext

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
                contentCache = stubContentCache(),
            ),
        )
        errorHandler = mockk(relaxed = true)
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        // As DefaultErrorHandler does it, so an exception escaping a launched block really reaches
        // HistoryVM.dispatchError here. Relaxed on its own would swallow it and hide the one thing
        // aFailingStoredPageReadLeavesTheWalkToPublish is about.
        every { errorHandler.proceedInvoke(any(), any()) } answers {
            val action = secondArg<((ErrorEntity) -> Unit)?>()
            action?.invoke(errorHandler.map(firstArg()))
        }
    }

    /**
     * With nothing stored, `CachedFeed.load` rethrows the loader's failure instead of reporting it
     * as `RefreshFailed` — the cold-cache case, so every app start. That failure belongs to a read
     * the user is not waiting on and must not reach the screen, least of all reset the runtime
     * under a walk that is about to succeed.
     */
    @Test
    fun aFailingStoredPageReadLeavesTheWalkToPublish() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flow<Cached<PaginatedResponse<History>>> {
            throw IOException("stored page failure")
        }
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        // The read throws inline inside testOnStart, so by the time the walk is released the
        // failure has already had its chance to reset the runtime under it.
        val vm = createVM().also(HistoryVM::testOnStart)

        walk.complete(page(listOf(movie(2))))

        assertEquals(listOf(2), awaitContent(vm).itemIds())
        assertNull(vm.testMessageValue)
        vm.testCancelScope()
    }

    /**
     * The paginator delivers its opening `Loading` from its own actor thread, so it can arrive
     * after the stored page has been drawn. Here that ordering is forced rather than raced: the
     * paginator runs on a scheduler this test steps by hand.
     */
    @Test
    fun theOpeningLoadingStateDoesNotBlankTheStoredRows() {
        val scheduler = TestCoroutineScheduler()
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(listOf(movie(1))), isStale = true),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM(paginatorContext = StandardTestDispatcher(scheduler))

        vm.testOnStart()

        assertEquals(listOf(1), (vm.testStateValue as HistoryViewState.Content).itemIds())
        scheduler.advanceUntilIdle()
        assertEquals(listOf(1), (vm.testStateValue as HistoryViewState.Content).itemIds())
        vm.testCancelScope()
    }

    /**
     * The same opening `Loading`, raced rather than stepped. It is a read-modify-write of the view
     * state on the paginator's actor thread, so without the publication lock it can decide on the
     * spinner while the screen is empty and write it after the stored page has been drawn — which is
     * the flicker the stored page is there to remove. The stored publication is held at the lock
     * here and must be the state that survives.
     */
    @Test
    fun theOpeningLoadingStateCannotBlankRowsPublishedBesideIt() {
        val scheduler = TestCoroutineScheduler()
        val storedPage = CompletableDeferred<Unit>()
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flow {
            storedPage.await()
            emit(Cached.Value(page(listOf(movie(1))), isStale = true))
        }
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM(paginatorContext = StandardTestDispatcher(scheduler))
        val atLoadingPublication = CountDownLatch(1)
        val storedRowsPublished = CountDownLatch(1)
        vm.testBeforeLoadingStatePublication = {
            vm.testBeforeLoadingStatePublication = null
            atLoadingPublication.countDown()
            // Long enough for the stored publication to land if nothing is stopping it, and a wait
            // rather than a join so that a lock which does stop it cannot deadlock the test.
            storedRowsPublished.await(SETTLE_MILLIS, TimeUnit.MILLISECONDS)
        }
        vm.testOnStart()

        // The paginator's own thread, so the stored page can be published from another one.
        val loadingThread = thread { scheduler.advanceUntilIdle() }
        assertTrue(atLoadingPublication.await(2, TimeUnit.SECONDS))
        val storedThread = thread {
            // Resumes the stored-page collection inline, so the publication runs on this thread.
            storedPage.complete(Unit)
            storedRowsPublished.countDown()
        }

        loadingThread.join(2_000)
        storedThread.join(2_000)
        assertEquals(listOf(1), awaitContent(vm).itemIds())
        vm.testCancelScope()
    }

    /**
     * Pins today's behaviour rather than improving it. The stored page is drawn without entering
     * `stableHistory`, so a walk failure still finds no stable rows and shows the full-screen error
     * it always has — rows then error, where it used to be spinner then error. Softening that means
     * teaching the runtime about drawn-but-unstable rows, which is its own change.
     */
    @Test
    fun aWalkFailureBehindTheStoredPageStillShowsTheFullScreenError() {
        val walk = CompletableDeferred<PaginatedResponse<History>>()
        every { interactor.observeFirstPage(any()) } returns flowOf(
            Cached.Value(page(listOf(movie(1))), isStale = true),
        )
        coEvery { api.getHistoryData(1) } coAnswers { Result.success(walk.await()) }
        val vm = createVM().also(HistoryVM::testOnStart)
        assertEquals(listOf(1), awaitContent(vm).itemIds())

        walk.completeExceptionally(IOException("first page failure"))

        val error = awaitState(vm) { it is HistoryViewState.Error } as HistoryViewState.Error
        assertEquals("first page failure", error.message)
        vm.testCancelScope()
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

    private fun createVM(
        paginatorContext: CoroutineContext = Dispatchers.Default.limitedParallelism(1),
    ): HistoryVM {
        return HistoryVM(
            paginator = Paginator.Store(paginatorContext, comparator = HistoryRowComparator),
            interactor = interactor,
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
            watchStateSyncInteractor = mockk(relaxed = true),
            router = mockk<AppRouter>(relaxed = true),
            errorHandler = errorHandler,
        )
    }

    private fun awaitContent(
        vm: HistoryVM,
        predicate: (HistoryViewState.Content) -> Boolean = { true },
    ): HistoryViewState.Content {
        return awaitState(vm) { state ->
            state is HistoryViewState.Content && predicate(state)
        } as HistoryViewState.Content
    }

    private fun awaitState(
        vm: HistoryVM,
        predicate: (HistoryViewState) -> Boolean,
    ): HistoryViewState {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (predicate(state)) return state
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History state; last=${vm.testStateValue}")
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
