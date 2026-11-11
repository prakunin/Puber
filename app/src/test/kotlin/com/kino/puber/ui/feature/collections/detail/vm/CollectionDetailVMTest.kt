package com.kino.puber.ui.feature.collections.detail.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.ui.feature.collections.detail.model.CollectionDetailViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class CollectionDetailVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var screens: Screens
    private lateinit var interactor: CollectionInteractor
    private lateinit var mapper: VideoItemUIMapper
    private lateinit var savedItemInteractor: SavedItemInteractor
    private val fetches = ArrayDeque<CompletableDeferred<List<Item>>>()

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        fetches.clear()
        coEvery { interactor.getCollectionItems(7) } coAnswers {
            fetches.removeFirstOrNull()?.await() ?: listOf(item(42))
        }
        every { mapper.mapShortItemList(any()) } answers {
            firstArg<List<Item>>().map { videoItem(it.id, isSaved = it.inWatchlist == true) }
        }
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
    fun returnedChanges_publishRefetchedContentWithoutLoading() = runTest {
        val states = recordStates(startedVM())
        val listener = returnListener(vm)
        val firstContent = vm.testStateValue
        val from = states.size
        val fetch = queueFetch()

        listener(changed)

        assertSame(firstContent, vm.testStateValue)
        fetch.complete(listOf(item(42), item(43)))
        settle()
        assertTrue(states.drop(from).none { it is CollectionDetailViewState.Loading })
        assertEquals(listOf(42, 43), (states.last() as CollectionDetailViewState.Content).items.map { it.id })
    }

    @Test
    fun playerReturn_publishRefetchedContentWithoutLoading() = runTest {
        val states = recordStates(startedVM())
        val listener = returnListener(vm, viaPlayer = true)
        val from = states.size
        val fetch = queueFetch()

        listener(changed)
        fetch.complete(listOf(item(42), item(43)))
        settle()

        assertTrue(states.drop(from).none { it is CollectionDetailViewState.Loading })
        assertEquals(listOf(42, 43), (states.last() as CollectionDetailViewState.Content).items.map { it.id })
    }

    @Test
    fun firstLoad_publishesLoadingBeforeContent() = runTest {
        val fetch = queueFetch()
        vm = createVM()
        val states = recordStates(vm)

        vm.testOnStart()
        fetch.complete(listOf(item(42)))
        settle()

        assertEquals(2, states.size)
        assertTrue(states[0] is CollectionDetailViewState.Loading)
        assertTrue(states[1] is CollectionDetailViewState.Content)
    }

    @Test
    fun retry_publishesLoadingAfterError() = runTest {
        val failing = queueFetch()
        vm = createVM()
        vm.testOnStart()
        failing.completeExceptionally(IllegalStateException("refresh failed"))
        settle()
        assertEquals(CollectionDetailViewState.Error("refresh failed"), vm.testStateValue)
        queueFetch()

        vm.onAction(CommonAction.RetryClicked)

        assertEquals(CollectionDetailViewState.Loading, vm.testStateValue)
    }

    @Test
    fun failedRefresh_keepsContentAndShowsMessage() = runTest {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)
        val firstContent = vm.testStateValue
        val fetch = queueFetch()

        listener(changed)
        fetch.completeExceptionally(IllegalStateException("refresh failed"))
        settle()

        assertEquals(firstContent, vm.testStateValue)
        assertEquals("refresh failed", vm.testMessageValue?.message)
    }

    @Test
    fun overlappingRefreshes_publishOnlyTheLatestFetch() = runTest {
        vm = createVM().also { it.testOnStart() }
        val states = recordStates(vm)
        val listener = returnListener(vm)
        val first = queueFetch()
        val second = queueFetch()
        listener(changed)
        listener(changed)

        second.complete(listOf(item(2)))
        settle()
        val afterSecond = states.size
        first.complete(listOf(item(1)))
        settle()

        assertEquals(listOf(2), (states.last() as CollectionDetailViewState.Content).items.map { it.id })
        val laterIds = states.drop(afterSecond - 1).filterIsInstance<CollectionDetailViewState.Content>()
            .flatMap { content -> content.items.map { it.id } }
        assertTrue(1 !in laterIds)
        assertNull(vm.testMessageValue)
    }

    @Test
    fun toggleDuringRefresh_keepsSavedValueWhenSaveSettledFirst() = runTest {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)
        val fetch = queueFetch()
        val save = controlledSave(42)
        listener(changed)

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        save.complete(Result.success(true))
        settle()
        fetch.complete(listOf(item(42, saved = false)))
        settle()

        assertTrue(cardSaved(42))
    }

    @Test
    fun toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch() = runTest {
        vm = createVM().also { it.testOnStart() }
        val states = recordStates(vm)
        val listener = returnListener(vm)
        val fetch = queueFetch()
        val save = controlledSave(42)
        listener(changed)

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        val afterToggle = states.size
        fetch.complete(listOf(item(42, saved = false), item(43)))
        settle()
        assertEquals(listOf(42), (vm.testStateValue as CollectionDetailViewState.Content).items.map { it.id })
        save.complete(Result.success(true))
        settle()

        val published = states.drop(afterToggle).filterIsInstance<CollectionDetailViewState.Content>()
        assertTrue(published.none { c -> c.items.single { it.id == 42 }.isSaved.not() })
        assertEquals(listOf(42, 43), (vm.testStateValue as CollectionDetailViewState.Content).items.map { it.id })
        assertTrue(cardSaved(42))
    }

    @Test
    fun settledToggleWhileAnotherSaveIsPending_showsServerSavedValue() = runTest {
        val initial = queueFetch()
        vm = createVM().also { it.testOnStart() }
        initial.complete(listOf(item(42), item(43)))
        settle()
        val listener = returnListener(vm)
        val save42 = controlledSave(42)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        save42.complete(Result.success(true))
        settle()
        val save43 = controlledSave(43)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(43), true))
        val refresh = queueFetch()

        listener(changed)
        refresh.complete(listOf(item(42), item(43)))
        settle()
        save43.complete(Result.success(true))
        settle()

        assertEquals(false, cardSaved(42))
        assertEquals(true, cardSaved(43))
    }

    @Test
    fun failedToggleDuringRefresh_showsRevertedValue() = runTest {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)
        val fetch = queueFetch()
        val save = controlledSave(42)
        listener(changed)

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        save.complete(Result.failure(IllegalStateException("save failed")))
        settle()
        fetch.complete(listOf(item(42, saved = false)))
        settle()

        assertEquals(false, cardSaved(42))
    }

    @Test
    fun refreshAfterPublishedToggle_showsServerSavedValue() = runTest {
        val initial = queueFetch()
        vm = createVM().also { it.testOnStart() }
        initial.complete(listOf(item(42), item(43)))
        settle()
        val listener = returnListener(vm)
        val firstRefresh = queueFetch()
        val save42 = controlledSave(42)
        listener(changed)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        save42.complete(Result.success(true))
        settle()
        firstRefresh.complete(listOf(item(42), item(43)))
        settle()
        val save43 = controlledSave(43)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(43), true))
        val secondRefresh = queueFetch()

        listener(changed)
        secondRefresh.complete(listOf(item(42), item(43)))
        settle()
        save43.complete(Result.success(true))
        settle()

        assertEquals(false, cardSaved(42))
    }

    @Test
    fun gridToggleBeforeReturn_showsServerSavedValue() = runTest {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)
        val save = controlledSave(42)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        save.complete(Result.success(true))
        settle()
        val fetch = queueFetch()

        listener(changed)
        fetch.complete(listOf(item(42, saved = false)))
        settle()

        assertEquals(false, cardSaved(42))
    }

    @Test
    fun toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh() = runTest {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)
        val firstRefresh = queueFetch()
        val save = controlledSave(42)
        listener(changed)
        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        firstRefresh.completeExceptionally(IllegalStateException("refresh failed"))
        settle()
        val secondRefresh = queueFetch()

        listener(changed)
        save.complete(Result.success(true))
        settle()
        secondRefresh.complete(listOf(item(42, saved = false)))
        settle()

        assertTrue(cardSaved(42))
    }

    @Test
    fun emptyReturnedChanges_doNotRefetch() {
        vm = createVM().also { it.testOnStart() }
        val listener = returnListener(vm)

        listener(null)
        listener(ContentChangeSet.empty())

        coVerify(exactly = 1) { interactor.getCollectionItems(7) }
    }

    @Test
    fun returnedChanges_areForwardedToParentOnBack() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }
        val changes = ContentChangeSet.single(42, ContentChangeType.Watched)

        listener.captured(changes)
        vm.onBackPressed()

        verify { router.back(RESULT_CONTENT_CHANGED, changes) }
    }

    @Test
    fun back_waitsForSaveAndForwardsBookmarkChange() = runTest {
        val saveResult = CompletableDeferred<Result<Boolean>>()
        coEvery { savedItemInteractor.setSaved(42, false, true) } coAnswers { saveResult.await() }
        val vm = createVM().also { it.testOnStart() }

        vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))
        vm.onBackPressed()
        verify(exactly = 0) { router.back(any(), any()) }

        saveResult.complete(Result.success(true))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val result = slot<ContentChangeSet>()
        verify { router.back(RESULT_CONTENT_CHANGED, capture(result)) }
        assertEquals(setOf(ContentChangeType.Bookmark), result.captured.changes[42])
    }

    private lateinit var vm: CollectionDetailVM

    private fun startedVM() = createVM().also {
        vm = it
        it.testOnStart()
    }

    private fun createVM() = CollectionDetailVM(
        router = router,
        collectionId = 7,
        collectionTitle = "Collection",
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        errorHandler = mockk<ErrorHandler> {
            every { proceed(any()) } returns { }
            every { proceedInvoke(any(), any()) } answers {
                secondArg<((ErrorEntity) -> Unit)?>()?.invoke(ErrorEntity(message = "refresh failed", code = "test"))
            }
        },
    )

    private val changed = ContentChangeSet.single(42, ContentChangeType.Watched)

    private fun queueFetch() = CompletableDeferred<List<Item>>().also { fetches.add(it) }

    private fun controlledSave(id: Int) = CompletableDeferred<Result<Boolean>>().also { save ->
        coEvery { savedItemInteractor.setSaved(id, false, true) } coAnswers { save.await() }
    }

    private fun settle() = mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

    private fun TestScope.recordStates(vm: CollectionDetailVM): MutableList<CollectionDetailViewState> {
        val states = mutableListOf<CollectionDetailViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.testStateFlow.collect(states::add)
        }
        return states
    }

    private fun returnListener(vm: CollectionDetailVM, viaPlayer: Boolean = false): (ContentChangeSet?) -> Unit {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        every { screens.player(42) } returns screen
        val card = videoItem(42)
        vm.onAction(if (viaPlayer) CommonAction.ItemPlayed(card) else CommonAction.ItemSelected(card))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }
        return listener.captured
    }

    private fun cardSaved(id: Int) =
        (vm.testStateValue as CollectionDetailViewState.Content).items.single { it.id == id }.isSaved

    private fun item(id: Int, saved: Boolean = false) =
        Item(id = id, title = "Movie $id", type = ItemType.MOVIE, inWatchlist = saved)

    private fun videoItem(id: Int, isSaved: Boolean = false) =
        VideoItemUIState(id, "Item $id", "", "", isSaved = isSaved)
}
