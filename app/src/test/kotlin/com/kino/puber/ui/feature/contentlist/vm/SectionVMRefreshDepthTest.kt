package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What a refresh owes a row the user has already paged through.
 *
 * A refresh used to restart paging: the row went back to page one and lost every page the user had
 * scrolled into, which on TV is not a cosmetic matter — the card they were on is what focus is
 * restored to, and a card that is no longer in the list cannot receive it. So a refresh has to come
 * back with at least the pages the row had, and must not put the row through a shorter list on the
 * way there.
 */
internal class SectionVMRefreshDepthTest : SectionVMTestFixture() {

    @Test
    fun refresh_reloadsEveryPageTheRowHadAlreadyLoaded() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("fresh")
        coEvery { interactor.loadPage(sectionConfig, page = 1) } returns pageOf(1, 2, current = 1, total = 3)
        coEvery { interactor.loadPage(sectionConfig, page = 2) } returns pageOf(3, 4, current = 2, total = 3)
        servesFirstPageFromTheNetwork(interactor)
        val vm = createVM(
            paginator = paginator,
            config = sectionConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapperFor(1, 2, 3, 4),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()
        assertEquals(4, contentItemCount(vm), "the row starts with two pages loaded")

        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { interactor.loadPage(sectionConfig, page = 1) }
        coVerify(exactly = 2) { interactor.loadPage(sectionConfig, page = 2) }
        assertEquals(4, contentItemCount(vm), "the refreshed row still holds both pages")
        vm.testCancelScope()
        paginator.close()
    }

    @Test
    fun refresh_neverPublishesLessThanTheRowAlreadyShowed() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("fresh")
        coEvery { interactor.loadPage(sectionConfig, page = 1) } returns pageOf(1, 2, current = 1, total = 3)
        coEvery { interactor.loadPage(sectionConfig, page = 2) } returns pageOf(3, 4, current = 2, total = 3)
        servesFirstPageFromTheNetwork(interactor)
        val vm = createVM(
            paginator = paginator,
            config = sectionConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapperFor(1, 2, 3, 4),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()

        val published = mutableListOf<SectionState>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            vm.testStateFlow.collect(published::add)
        }
        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()
        collector.cancel()

        val shortened = published.filterIsInstance<SectionState.Content>().filter { it.items.size < 4 }
        assertTrue(shortened.isEmpty(), "row was cut back to ${shortened.map { it.items.size }} while refreshing")
        assertTrue(
            published.none { it is SectionState.Loading },
            "row was blanked to its shimmer while refreshing",
        )
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * Two reloads asked for at once still leave the row at full depth, whichever of their loads
     * survives.
     *
     * A guard on the outcome rather than a reproduction of a race: the depth is read from the row
     * itself at publication time and belongs to no particular load, so there is no obligation for a
     * cancelled load to drop. Held instead in a slot that each load consumed, the load that outlived
     * the other could find the slot already emptied and publish page one alone.
     */
    @Test
    fun refresh_interruptedByAnotherRefresh_stillComesBackAtFullDepth() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("fresh")
        coEvery { interactor.loadPage(sectionConfig, page = 1) } returns pageOf(1, 2, current = 1, total = 3)
        coEvery { interactor.loadPage(sectionConfig, page = 2) } returns pageOf(3, 4, current = 2, total = 3)
        servesFirstPageFromTheNetwork(interactor)
        val vm = createVM(
            paginator = paginator,
            config = sectionConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapperFor(1, 2, 3, 4),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()

        // Two triggers landing in the same breath — a return from a title and a watch-state change
        // — before either load has run far enough to publish anything.
        vm.refreshFirstPage()
        vm.refreshFirstPage()
        testScheduler.advanceUntilIdle()

        assertEquals(4, contentItemCount(vm), "the row that outlived two overlapping reloads")
        vm.testCancelScope()
        paginator.close()
    }

    /**
     * A page that fails to read is not the server saying the row is shorter, and answering with the
     * pages that did arrive would cut it back by exactly what failed.
     */
    @Test
    fun refresh_whoseLaterPageFails_leavesTheRowAsItWas() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val coordinator = ContentListRefreshCoordinator()
        val sectionConfig = config("fresh")
        coEvery { interactor.loadPage(sectionConfig, page = 1) } returns pageOf(1, 2, current = 1, total = 3)
        coEvery { interactor.loadPage(sectionConfig, page = 2) } returns pageOf(3, 4, current = 2, total = 3)
        servesFirstPageFromTheNetwork(interactor)
        val vm = createVM(
            paginator = paginator,
            config = sectionConfig,
            interactor = interactor,
            coordinator = coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapperFor(1, 2, 3, 4),
            errorHandler = mappingErrorHandler(),
        )
        vm.testOnStart()
        testScheduler.advanceUntilIdle()
        vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()
        coEvery { interactor.loadPage(sectionConfig, page = 2) } throws IllegalStateException("page two is down")

        coordinator.requestRefresh()
        testScheduler.advanceUntilIdle()

        assertEquals(4, contentItemCount(vm), "the row the user is looking at")
        vm.testCancelScope()
        paginator.close()
    }

    private fun contentItemCount(vm: SectionVM): Int =
        (vm.testStateValue as? SectionState.Content)?.items?.size ?: 0

    private fun pageOf(vararg ids: Int, current: Int, total: Int): PaginatedResponse<Item> =
        PaginatedResponse(
            items = ids.map(::item),
            pagination = Pagination(current = current, perpage = ids.size, total = total),
        )
}
