package com.kino.puber.ui.feature.collections.detail.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
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
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
        interactor = mockk(relaxed = true)
        mapper = mockk(relaxed = true)
        savedItemInteractor = mockk(relaxed = true)
        coEvery { interactor.getCollectionItems(7) } returns listOf(
            Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        )
        every { mapper.mapShortItemList(any()) } returns listOf(videoItem(42))
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
    fun returnedChanges_reloadItems() {
        val screen = mockk<PuberScreen>()
        val listener = slot<(ContentChangeSet?) -> Unit>()
        every { screens.details(42) } returns screen
        val vm = createVM().also { it.testOnStart() }
        vm.onAction(CommonAction.ItemSelected(videoItem(42)))
        verify { router.navigateForResult<ContentChangeSet>(screen, RESULT_CONTENT_CHANGED, capture(listener)) }

        listener.captured(ContentChangeSet.single(42, ContentChangeType.Watched))

        coVerify(exactly = 2) { interactor.getCollectionItems(7) }
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

    private fun createVM() = CollectionDetailVM(
        router = router,
        collectionId = 7,
        collectionTitle = "Collection",
        interactor = interactor,
        savedItemInteractor = savedItemInteractor,
        mapper = mapper,
        errorHandler = mockk<ErrorHandler> { every { proceed(any()) } returns { } },
    )

    private fun videoItem(id: Int) = VideoItemUIState(id, "Item $id", "", "")
}
