package com.kino.puber.ui.feature.history.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.WatchingInfo
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import com.kino.puber.util.stubContentCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

internal class HistoryVMLifecycleTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val AWAIT_TIMEOUT_MILLIS = 4_000L
        private const val AWAIT_POLL_MILLIS = 10L
    }

    private lateinit var api: KinoPubApiClient
    private lateinit var vm: HistoryVM
    private lateinit var watchStateSync: WatchStateSyncInteractor
    private val displaySettingsChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @BeforeEach
    fun setUp() {
        api = mockk()
        watchStateSync = mockk(relaxed = true)
        val errorHandler = mockk<ErrorHandler>(relaxed = true)
        every { errorHandler.map(any()) } answers {
            val error = firstArg<Throwable>()
            ErrorEntity(message = error.message.orEmpty(), code = "test")
        }
        vm = HistoryVM(
            paginator = Paginator.Store(comparator = HistoryRowComparator),
            interactor = HistoryInteractor(
                api = api,
                itemDetailsRepository = mockk<ItemDetailsRepository>(relaxed = true),
                navigationPreferencesRepository = mockk {
                    every { displaySettingsChanges } returns this@HistoryVMLifecycleTest.displaySettingsChanges
                },
                contentCache = stubContentCache(),
            ),
            mapper = HistoryUIMapper(VideoItemUIMapper(FakeResourceProvider())),
            watchStateSyncInteractor = watchStateSync,
            router = mockk<AppRouter>(relaxed = true),
            errorHandler = errorHandler,
        )
    }

    @Test
    fun openingTheScreenAsksTheIndexToCatchUp() {
        coEvery { api.getHistoryData(1) } returns Result.success(page(listOf(movie(1))))

        vm.testOnStart()
        awaitContent()

        // Not forced, so a walk already running is left alone; no age accepted, so the hour that
        // governs the background refresh does not keep this one from happening.
        verify { watchStateSync.requestSync(force = false, maxIndexAge = Duration.ZERO) }
    }

    @Test
    fun comingBackToTheScreenAsksAgain() {
        coEvery { api.getHistoryData(1) } returns Result.success(page(listOf(movie(1))))
        vm.testOnStart()
        awaitContent()

        vm.onAction(CommonAction.OnResume)
        awaitState { true }

        verify(exactly = 2) {
            watchStateSync.requestSync(force = false, maxIndexAge = Duration.ZERO)
        }
    }

    @Test
    fun watchedIndicatorSettingChange_reloadsWhatIsOnScreen() {
        // Moving between screens does not pause the activity, so coming back from the settings
        // screen brings no resume with it — without this the rows keep the previous choice until
        // something else happens to reload them.
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            pageOneCalls.incrementAndGet()
            Result.success(page(listOf(movie(1))))
        }
        vm.testOnStart()
        awaitContent()
        assertEquals(1, pageOneCalls.get())

        assertTrue(displaySettingsChanges.tryEmit(Unit))

        awaitState { pageOneCalls.get() == 2 }
    }

    @Test
    fun onResumeFromEmpty_reloadsPageOne() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.success(page(emptyList()))
            } else {
                Result.success(page(listOf(movie(1))))
            }
        }
        vm.testOnStart()
        awaitState { pageOneCalls.get() == 1 && it == HistoryViewState.Empty }

        vm.onAction(CommonAction.OnResume)

        assertEquals(listOf(1), awaitContent().items.map(HistoryItemUIState::itemId))
        assertEquals(2, pageOneCalls.get())
        coVerify(exactly = 2) { api.getHistoryData(1) }
    }

    @Test
    fun onResumeFromInitialError_reloadsPageOne() {
        val pageOneCalls = AtomicInteger()
        coEvery { api.getHistoryData(1) } coAnswers {
            if (pageOneCalls.incrementAndGet() == 1) {
                Result.failure(IOException("initial failure"))
            } else {
                Result.success(page(listOf(movie(1))))
            }
        }
        vm.testOnStart()
        awaitState { it is HistoryViewState.Error }

        vm.onAction(CommonAction.OnResume)

        assertEquals(listOf(1), awaitContent().items.map(HistoryItemUIState::itemId))
        assertEquals(2, pageOneCalls.get())
        coVerify(exactly = 2) { api.getHistoryData(1) }
    }

    @Test
    fun onResumeDuringInitialLoading_doesNotDuplicatePageOneRequest() {
        val pageOneCalls = AtomicInteger()
        val requestEntered = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        coEvery { api.getHistoryData(1) } coAnswers {
            pageOneCalls.incrementAndGet()
            requestEntered.countDown()
            check(releaseRequest.await(2, TimeUnit.SECONDS))
            Result.success(page(listOf(movie(1))))
        }
        vm.testOnStart()
        assertTrue(requestEntered.await(2, TimeUnit.SECONDS))
        assertEquals(HistoryViewState.Loading, vm.testStateValue)

        vm.onAction(CommonAction.OnResume)
        Thread.sleep(40)

        assertEquals(1, pageOneCalls.get())
        releaseRequest.countDown()
        assertEquals(listOf(1), awaitContent().items.map(HistoryItemUIState::itemId))
        coVerify(exactly = 1) { api.getHistoryData(1) }
    }

    private fun awaitContent(): HistoryViewState.Content {
        return awaitState { it is HistoryViewState.Content } as HistoryViewState.Content
    }

    private fun awaitState(predicate: (HistoryViewState) -> Boolean): HistoryViewState {
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_MILLIS * 1_000_000
        while (System.nanoTime() < deadline) {
            val state = vm.testStateValue
            if (predicate(state)) return state
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        fail("Timed out waiting for History state; last=${vm.testStateValue}")
    }

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
                watching = WatchingInfo(time = 120, duration = 600),
            ),
        )
    }
}
