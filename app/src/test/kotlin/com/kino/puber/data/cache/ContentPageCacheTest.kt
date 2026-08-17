package com.kino.puber.data.cache

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.util.FakePayloadStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ContentPageCacheTest {

    private val store = FakePayloadStore()
    private var now = 1_000_000L
    private val subject = ContentPageCache(store = store, clock = { now })

    @Test
    fun aFreshSectionPageIsServedWithoutTouchingTheLoader() = runTest {
        var loads = 0
        subject.sectionPage("s", watchStateVersion = 1L) { loads++; page(1) }.toList()

        now += 1.minutes.inWholeMilliseconds
        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { loads++; page(2) }.toList()

        assertEquals(1, loads)
        assertEquals(listOf(1), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aStaleSectionPageEmitsTheStoredValueThenTheFreshOne() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        now += 31.minutes.inWholeMilliseconds

        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { page(2) }.toList()

        assertEquals(listOf(1, 2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aMovedWatchStateVersionRevalidatesAPageThatIsStillFresh() = runTest {
        // A filtered page is baked against an index version, so a page that is fresh by the clock
        // can still be wrong. The stored value is kept and drawn first — the refresh follows it.
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()

        val emissions = subject.sectionPage("s", watchStateVersion = 2L) { page(2) }.toList()

        assertEquals(listOf(1, 2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aMovedWatchStateVersionForcesOnlyTheFirstReadAfterTheMove() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        subject.sectionPage("s", watchStateVersion = 2L) { page(2) }.toList()

        var loads = 0
        subject.sectionPage("s", watchStateVersion = 2L) { loads++; page(3) }.toList()

        assertEquals(0, loads)
    }

    @Test
    fun aMovedWatchStateVersionRevalidatesEverySectionKeyNotJustTheFirstAsked() = runTest {
        // A catalogue tab renders several sections at once. A move must not be consumed by
        // whichever one happens to ask first, leaving the rest to serve stale-by-index pages.
        subject.sectionPage("a", watchStateVersion = 1L) { page(1) }.toList()
        subject.sectionPage("b", watchStateVersion = 1L) { page(11) }.toList()

        val emissionsA = subject.sectionPage("a", watchStateVersion = 2L) { page(2) }.toList()
        val emissionsB = subject.sectionPage("b", watchStateVersion = 2L) { page(12) }.toList()

        assertEquals(listOf(1, 2), emissionsA.map { (it as Cached.Value).value.items.single().id })
        assertEquals(listOf(11, 12), emissionsB.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun buildingASectionPageFlowWithoutCollectingItDoesNotConsumeTheMove() = runTest {
        // The version check has to run when the flow is collected, not when sectionPage() is called
        // — building a flow and dropping it is not a read, and must not burn the one forced read.
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()

        subject.sectionPage("s", watchStateVersion = 2L) { page(2) } // built, never collected

        val emissions = subject.sectionPage("s", watchStateVersion = 2L) { page(3) }.toList()

        assertEquals(listOf(1, 3), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun aSectionPagePastTheHardCeilingCountsAsAbsent() = runTest {
        subject.sectionPage("s", watchStateVersion = 1L) { page(1) }.toList()
        now += 8.days.inWholeMilliseconds

        val emissions = subject.sectionPage("s", watchStateVersion = 1L) { page(2) }.toList()

        assertEquals(listOf(2), emissions.map { (it as Cached.Value).value.items.single().id })
    }

    @Test
    fun theWatchlistIsStoredAndServedAsAList() = runTest {
        subject.watchlist { listOf(item(7)) }.toList()

        val emissions = subject.watchlist { listOf(item(8)) }.toList()

        assertEquals(listOf(7), (emissions.single() as Cached.Value).value.map(Item::id))
    }

    @Test
    fun theHistoryFirstPageRevalidatesAfterItsShortTtl() = runTest {
        subject.historyFirstPage { historyPage(1) }.toList()
        now += 3.minutes.inWholeMilliseconds

        val emissions = subject.historyFirstPage { historyPage(2) }.toList()

        assertEquals(2, emissions.size)
    }

    private fun item(id: Int) = Item(id = id, title = "Item $id", type = ItemType.MOVIE)

    private fun page(id: Int) = PaginatedResponse(
        items = listOf(item(id)),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )

    private fun historyPage(id: Int) = PaginatedResponse(
        items = listOf(History(item = item(id))),
        pagination = Pagination(current = 1, perpage = 50, total = 1),
    )
}
