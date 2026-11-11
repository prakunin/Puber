package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    @Test
    fun refresh_afterAPageThatReadAhead_doesNotReRequestAServerPageItAlreadyHolds() = runTest {
        val row = readAheadRow()
        row.walk(loadMores = 1)
        assertEquals(listOf(1, 2, 5, 6, 7, 8), row.ids(), "the row before the refresh")

        row.refresh()

        coVerify(exactly = 0) { row.interactor.loadPage(row.sectionConfig, page = 4) }
        assertEquals(listOf(1, 2, 5, 6, 7, 8), row.ids())
        row.close()
    }

    @Test
    fun refresh_afterAPageThatReadAhead_walksOnFromTheLastServerPageRead() = runTest {
        val row = readAheadRow()
        row.walk(loadMores = 1)
        row.refresh()

        row.vm.onAction(CommonAction.LoadMore)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { row.interactor.loadPage(row.sectionConfig, page = 5) }
        coVerify(exactly = 0) { row.interactor.loadPage(row.sectionConfig, page = 6) }
        row.close()
    }

    @Test
    fun refresh_withOverlappingPages_publishesEachIdOnceInFirstOccurrenceOrder() = runTest {
        val row = rowOf(1, 2, 3, 4, 5) { page, _ ->
            when (page) {
                1 -> pageOf(1, 2, 3, current = 1, total = 2)
                else -> pageOf(4, 2, 5, current = 2, total = 2)
            }
        }
        row.walk(loadMores = 1)

        row.refresh()

        assertEquals(listOf(1, 2, 3, 4, 5), row.ids())
        row.close()
    }

    @Test
    fun refresh_ofARowDeeperThanTheRestoreCeiling_makesAtMostNineRestoreRequests() = runTest {
        val row = rowOf(*(1..12).toList().toIntArray()) { page, _ -> pageOf(page, current = page, total = 20) }
        row.walk(loadMores = 11)

        row.refresh()

        assertEquals(1 + (maxRestoredPagesUnderTest - 1), row.callsAfterWalk, "requests of the refresh")
        assertEquals(
            (1..maxRestoredPagesUnderTest).associateWith { 1 },
            row.pagesAfterWalk.groupingBy { it }.eachCount(),
            "pages requested after the walk",
        )
        row.close()
    }

    @Test
    fun refresh_ofADeepRowWhosePagesReadAhead_stopsAtTheRestoreCeiling() = runTest {
        val row = rowOf(*(1..40).toList().toIntArray()) { page, _ ->
            pageOf(page, current = if (page == 1) 1 else page + 1, total = 50)
        }
        row.walk(loadMores = 11)

        row.refresh(failAfterCalls = 20)

        assertEquals(
            listOf(2, 4, 6, 8, 10),
            row.pagesAfterWalk.filter { it >= 2 },
            "restore pages requested after the walk: each once, page 12 never",
        )
        row.close()
    }

    @Test
    fun refresh_whosePagesReportALowerPageThanRequested_makesAtMostNineRestoreRequests() = runTest {
        val row = rowOf(*(1..10).toList().toIntArray()) { page, afterWalk ->
            when {
                !afterWalk || page == 1 -> pageOf(page, current = page, total = 50)
                else -> pageOf(page, current = 1, total = 50)
            }
        }
        row.walk(loadMores = 9)

        row.refresh(failAfterCalls = 20)

        assertTrue(row.callsAfterWalk <= 1 + (maxRestoredPagesUnderTest - 1), "requests ${row.callsAfterWalk}")
        assertEquals(
            (2..maxRestoredPagesUnderTest).associateWith { 1 },
            row.pagesAfterWalk.filter { it >= 2 }.groupingBy { it }.eachCount(),
            "restore pages requested after the walk",
        )
        row.close()
    }

    @Test
    fun refresh_whoseFirstPageReportsPageZero_makesAtMostNineRestoreRequests() = runTest {
        val row = rowOf(*(1..12).toList().toIntArray()) { page, afterWalk ->
            if (afterWalk && page == 1) {
                pageOf(1, current = 0, total = 50)
            } else {
                pageOf(page, current = page, total = 50)
            }
        }
        row.walk(loadMores = 11)

        row.refresh()

        assertTrue(row.callsAfterWalk <= 1 + (maxRestoredPagesUnderTest - 1), "requests ${row.callsAfterWalk}")
        row.close()
    }

    @Test
    fun refresh_throughEmptyPagesThatReadAhead_stopsAfterTheEmptyPageBudget() = runTest {
        val row = rowOf(*(1..10).toList().toIntArray()) { page, afterWalk ->
            when {
                !afterWalk || page == 1 -> pageOf(page, current = page, total = 50)
                else -> pageOf(current = page + 1, total = 50)
            }
        }
        row.walk(loadMores = 9)

        row.refresh()

        assertEquals(maxEmptyPageChainUnderTest + 1, row.callsAfterWalk - 1, "restore requests")
        assertEquals(listOf(1), row.ids())
        row.close()
    }

    /** Mirrors ContentListPagingVM.MAX_RESTORED_PAGES. */
    private val maxRestoredPagesUnderTest = 10

    /** A row whose second page read ahead: the first page holds server pages 1-2, the next 3-4. */
    private fun TestScope.readAheadRow() = rowOf(1, 2, 5, 6, 7, 8, 9, 10, 11, 12) { page, _ ->
        when (page) {
            1 -> pageOf(1, 2, current = 2, total = 6)
            3 -> pageOf(5, 6, 7, 8, current = 4, total = 6)
            4 -> pageOf(7, 8, 9, 10, current = 5, total = 6)
            else -> pageOf(11, 12, current = 6, total = 6)
        }
    }

    /** [answer] gets the requested page and whether the walk is over (the refresh is running). */
    private fun TestScope.rowOf(
        vararg mappedIds: Int,
        answer: (page: Int, afterWalk: Boolean) -> PaginatedResponse<Item>,
    ): RefreshRow {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val paginator = paginator(dispatcher)
        val interactor = mockk<ContentListInteractor>(relaxed = true)
        val sectionConfig = config("popular")
        val row = RefreshRow(this, paginator, interactor, sectionConfig, ContentListRefreshCoordinator())
        coEvery { interactor.loadPage(sectionConfig, any()) } answers {
            val page = secondArg<Int>()
            if (row.afterWalk) {
                row.callsAfterWalk += 1
                row.pagesAfterWalk += page
                check(row.failAfterCalls == null || row.callsAfterWalk <= row.failAfterCalls!!) { "too many calls" }
            }
            answer(page, row.afterWalk)
        }
        servesFirstPageFromTheNetwork(interactor)
        row.vm = createVM(
            paginator = paginator,
            config = sectionConfig,
            interactor = interactor,
            coordinator = row.coordinator,
            pagingCoroutineContext = dispatcher,
            mapper = mapperFor(*mappedIds),
            errorHandler = mappingErrorHandler(),
        )
        return row
    }

    private inner class RefreshRow(
        private val scope: TestScope,
        private val paginator: Paginator.Store<Item>,
        val interactor: ContentListInteractor,
        val sectionConfig: SectionConfig,
        val coordinator: ContentListRefreshCoordinator,
    ) {
        lateinit var vm: SectionVM
        var afterWalk = false
        var callsAfterWalk = 0
        val pagesAfterWalk = mutableListOf<Int>()
        var failAfterCalls: Int? = null

        fun walk(loadMores: Int) {
            vm.testOnStart()
            scope.testScheduler.advanceUntilIdle()
            repeat(loadMores) {
                vm.onAction(CommonAction.LoadMore)
                scope.testScheduler.advanceUntilIdle()
            }
        }

        fun refresh(failAfterCalls: Int? = null) {
            this.failAfterCalls = failAfterCalls
            afterWalk = true
            coordinator.requestRefresh()
            scope.testScheduler.advanceUntilIdle()
        }

        fun ids(): List<Int> = (vm.testStateValue as SectionState.Content).items.map { it.id }

        fun close() {
            vm.testCancelScope()
            paginator.close()
        }
    }

    private fun contentItemCount(vm: SectionVM): Int =
        (vm.testStateValue as? SectionState.Content)?.items?.size ?: 0

    private fun pageOf(vararg ids: Int, current: Int, total: Int): PaginatedResponse<Item> =
        PaginatedResponse(
            items = ids.map(::item),
            pagination = Pagination(current = current, perpage = ids.size, total = total),
        )
}
