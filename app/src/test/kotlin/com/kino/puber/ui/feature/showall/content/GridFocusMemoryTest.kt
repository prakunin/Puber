package com.kino.puber.ui.feature.showall.content

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The fake frame clock does not model recomposition, so these tests prove the decision logic of
 * [GridFocusMemory], not when the restore requester attaches to a card. Attach timing is covered
 * by the androidTests and the device checks.
 */
class GridFocusMemoryTest {

    private fun card(n: Int) = VideoItemUIState(id = n, title = "Card $n", imageUrl = "", bigImageUrl = "")

    private fun cards(range: IntRange) = range.map(::card)

    private fun without(id: Int, list: List<VideoItemUIState>) = list.filter { it.id != id }

    @Test
    fun resolveRestoreId_openerPresentReturnsTheOpener() {
        assertEquals(5, resolveRestoreId(5, 4, cards(1..10)))
    }

    @Test
    fun resolveRestoreId_openerGoneMidListReturnsTheCardAtTheSameIndex() {
        assertEquals(6, resolveRestoreId(5, 4, without(5, cards(1..10))))
    }

    @Test
    fun resolveRestoreId_openerGoneAndWasLastReturnsTheNewLastCard() {
        assertEquals(9, resolveRestoreId(10, 9, cards(1..9)))
    }

    @Test
    fun resolveRestoreId_indexBeyondAShorterListReturnsTheLastCard() {
        assertEquals(10, resolveRestoreId(99, 40, cards(1..10)))
    }

    @Test
    fun resolveRestoreId_emptyListReturnsNull() {
        assertEquals(null, resolveRestoreId(5, 4, emptyList()))
    }

    @Test
    fun resolveRestoreId_nullIdReturnsNull() {
        assertEquals(null, resolveRestoreId(null, 0, cards(1..10)))
    }

    @Test
    fun resolveRestoreId_nullIdWithNonZeroIndexReturnsNull() {
        assertEquals(null, resolveRestoreId(null, 4, cards(1..10)))
    }

    // U-AC1a
    @Test
    fun reconcile_reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour() = runTest {
        val h = Harness(5, 4, cards(1..10), onFrame = { if (it == 1) changeItems(without(5, cards(1..10))) })
        run(h)
        assertEquals("restore:6:true", h.restoreEvents().last())
        assertEquals(6, h.memory.restoreId)
        assertTrue("grid" !in h.events)
    }

    // U-AC1b
    @Test
    fun reconcile_reloadOnTheLastAttemptStillGetsAFreshBudget() = runTest {
        val h = Harness(
            5, 4, cards(1..10),
            restoreOverride = { id -> id == 6 },
            onFrame = { if (it == 3) changeItems(without(5, cards(1..10))) },
        )
        run(h)
        assertEquals("restore:6:true", h.restoreEvents().last())
        assertTrue(h.clock.frames in 4..6)
        assertTrue("grid" !in h.events)
    }

    // U-AC1c
    @Test
    fun reconcile_openerDroppedAsLastItemRetargetsToTheNewLastCard() = runTest {
        val h = Harness(10, 9, cards(1..10), onFrame = { if (it == 1) changeItems(cards(1..9)) })
        run(h)
        assertEquals("restore:9:true", h.restoreEvents().last())
    }

    // U-AC2a
    @Test
    fun reconcile_openerAbsentAtConstructionFocusesTheClampedIndexCard() = runTest {
        val h = Harness(5, 4, without(5, cards(1..10)))
        assertEquals(6, h.memory.restoreId)
        run(h)
        assertEquals(listOf("restore:6:true"), h.events)

        val far = Harness(99, 40, cards(1..10))
        assertEquals(10, far.memory.restoreId)
        run(far)
        assertEquals(listOf("restore:10:true"), far.events)
    }

    // U-AC4
    @Test
    fun reconcile_contentFocusFlippingOffBetweenAttemptsStopsFurtherRequests() = runTest {
        val h = Harness(5, 4, cards(1..10), restoreOverride = { false }, onFrame = { if (it == 2) activeVar = false })
        run(h)
        assertEquals(1, h.restoreEvents().size)
        assertTrue("grid" !in h.events)
    }

    @Test
    fun reconcile_contentFocusInactiveFromTheStartMakesNoRequest() = runTest {
        val h = Harness(5, 4, cards(1..10), restoreOverride = { false })
        h.activeVar = false
        run(h)
        assertTrue(h.events.isEmpty())
    }

    // U-AC5a
    @Test
    fun reconcile_everyTargetedAttemptFailingWhileActiveCallsTheGridRequesterOnce() = runTest {
        val h = Harness(5, 4, cards(1..10), restoreOverride = { false })
        run(h)
        assertEquals(listOf("restore:5:false", "restore:5:false", "restore:5:false", "grid"), h.events)
    }

    // U-AC5b
    @Test
    fun reconcile_gridRequesterIsNotCalledWhenContentFocusIsInactive() = runTest {
        val off = Harness(5, 4, cards(1..10), restoreOverride = { false })
        off.activeVar = false
        run(off)
        assertTrue("grid" !in off.events)

        val late = Harness(
            5, 4, cards(1..10),
            restoreOverride = { false },
            onFrame = { if (it == 3) activeVar = false },
        )
        run(late)
        assertTrue("grid" !in late.events)
    }

    // U-AC5c
    @Test
    fun reconcile_gridRequesterIsNotCalledWhileTargetedAttemptsArePendingOrAfterASuccess() = runTest {
        val success = Harness(5, 4, cards(1..10))
        success.restoreSucceeds = { success.clock.frames >= 2 }
        run(success)
        assertEquals(2, success.restoreEvents().size)
        assertTrue("grid" !in success.events)

        val changed = Harness(
            5, 4, cards(1..10),
            restoreOverride = { id -> id == 6 },
            onFrame = { if (it == 2) changeItems(without(5, cards(1..10))) },
        )
        run(changed)
        assertTrue("grid" !in changed.events)
        assertEquals("restore:6:true", changed.restoreEvents().last())

        val empty = Harness(5, 4, emptyList())
        run(empty)
        assertTrue(empty.events.isEmpty())
    }

    // U-AC6a
    @Test
    fun reconcile_appendWhileFocusIsInTheGridMakesNoRequest() = runTest {
        val h = Harness(5, 4, cards(1..10))
        h.memory.onGridFocusChanged(true)
        h.memory.onCardFocused(3, 2)
        h.itemsVar = cards(1..13)
        run(h)
        assertTrue(h.events.isEmpty())
    }

    // U-AC6b
    @Test
    fun reconcile_remapKeepingTheFocusedCardMakesNoRequest() = runTest {
        val h = Harness(5, 4, cards(1..10))
        h.memory.onGridFocusChanged(true)
        h.memory.onCardFocused(3, 2)
        h.itemsVar = cards(1..10).map { it.copy(title = "${it.title} changed") }
        run(h)
        assertTrue(h.events.isEmpty())
    }

    // U-AC6c
    @Test
    fun reconcile_focusLeavingTheGridToTheRailThenReloadMakesNoRequest() = runTest {
        val h = Harness(5, 4, cards(1..10))
        h.memory.onGridFocusChanged(true)
        h.memory.onGridFocusChanged(false)
        h.itemsVar = without(5, cards(1..10))
        run(h)
        assertTrue(h.events.isEmpty())
    }

    // U-AC6d
    @Test
    fun reconcile_firstLaunchWithNoSavedIdMakesNoRequest() = runTest {
        val h = Harness(null, 0, cards(1..10))
        run(h)
        assertTrue(h.events.isEmpty())
    }

    // U-AC6e
    @Test
    fun reconcile_liveGridFocusedCardRemovedRetargetsToTheNeighbour() = runTest {
        val h = Harness(null, 0, cards(1..10))
        h.memory.onGridFocusChanged(true)
        h.memory.onCardFocused(5, 4)
        h.itemsVar = without(5, cards(1..10))
        run(h)
        assertEquals("restore:6:true", h.restoreEvents().last())
    }

    @Test
    fun reconcile_entryWithContentInactiveRestoresWhenReconcileRerunsAfterItTurnsActive() = runTest {
        val h = Harness(5, 4, cards(1..10))
        h.activeVar = false
        run(h)
        assertTrue(h.events.isEmpty())

        h.activeVar = true
        run(h)
        assertEquals(listOf("restore:5:true"), h.events)
    }

    @Test
    fun reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid() = runTest {
        val h = Harness(null, 0, cards(1..10), restoreOverride = { false })
        h.memory.onGridFocusChanged(true)
        h.memory.onCardFocused(5, 4)
        h.itemsVar = without(5, cards(1..10))
        run(h)
        assertEquals(listOf("restore:6:false", "restore:6:false", "restore:6:false"), h.events)
    }

    // A rail opened from card 5 (index 4): the grid held focus, then lost it to the rail.
    private fun railOpenedFromCard5(items: List<VideoItemUIState> = cards(1..10)): Harness {
        val h = Harness(null, 0, items)
        h.memory.onGridFocusChanged(true)
        h.memory.onCardFocused(5, 4)
        h.memory.onGridFocusChanged(false)
        return h
    }

    private fun TestScope.handoff(h: Harness, id: Long) {
        launch(h.clock) { h.memory.onHandoff(id) }
        advanceUntilIdle()
    }

    // T-U1
    @Test
    fun onHandoff_rememberedCardAndGridUnfocusedRequestsThatCard() = runTest {
        val h = railOpenedFromCard5()
        handoff(h, 1)
        assertEquals(listOf("restore:5:true"), h.events)
    }

    // T-U2
    @Test
    fun onHandoff_withoutARememberedCardMakesNoRequest() = runTest {
        val h = Harness(null, 0, cards(1..10))
        handoff(h, 1)
        assertTrue(h.events.isEmpty())
    }

    // T-U3
    @Test
    fun onHandoff_whileTheGridHoldsFocusMakesNoRequest() = runTest {
        val h = railOpenedFromCard5()
        h.memory.onGridFocusChanged(true)
        handoff(h, 1)
        assertTrue(h.events.isEmpty())
    }

    // T-U4
    @Test
    fun onHandoff_theSameIdIsAnsweredOnce() = runTest {
        val h = railOpenedFromCard5()
        handoff(h, 1)
        h.memory.onGridFocusChanged(false)
        handoff(h, 1)
        assertEquals(listOf("restore:5:true"), h.events)
        handoff(h, 2)
        assertEquals(listOf("restore:5:true", "restore:5:true"), h.events)
    }

    // T-U5
    @Test
    fun onHandoff_contentInactiveMakesNoRequestAndFlippingOffStopsIt() = runTest {
        val off = railOpenedFromCard5()
        off.activeVar = false
        handoff(off, 1)
        assertTrue(off.events.isEmpty())

        val late = Harness(
            null, 0, cards(1..10),
            restoreOverride = { false },
            onFrame = { if (it == 2) activeVar = false },
        )
        late.memory.onGridFocusChanged(true)
        late.memory.onCardFocused(5, 4)
        late.memory.onGridFocusChanged(false)
        handoff(late, 1)
        assertEquals(1, late.restoreEvents().size)
        assertTrue("grid" !in late.events)
    }

    // An id seen while the content is inactive stays unanswered until it turns active.
    @Test
    fun onHandoff_idArrivingWhileInactiveIsAnsweredOnceContentTurnsActive() = runTest {
        val h = railOpenedFromCard5()
        h.activeVar = false
        handoff(h, 1)
        h.activeVar = true
        handoff(h, 1)
        assertEquals(listOf("restore:5:true"), h.events)
    }

    // T-U6
    @Test
    fun onHandoff_rememberedCardDroppedWhileTheRailWasOpenRequestsTheNeighbour() = runTest {
        val h = railOpenedFromCard5()
        h.itemsVar = without(5, cards(1..10))
        handoff(h, 1)
        assertEquals(listOf("restore:6:true"), h.events)
    }

    // T-U7
    @Test
    fun onHandoff_leavesPendingEntryUntouched() = runTest {
        val h = Harness(5, 4, cards(1..10))
        handoff(h, 1)
        assertEquals(listOf("restore:5:true"), h.events)
        run(h)
        assertEquals(listOf("restore:5:true", "restore:5:true"), h.events)
        assertTrue("grid" !in h.events)
    }

    private fun TestScope.run(h: Harness) {
        h.start(this)
        advanceUntilIdle()
    }

    private class FakeFrameClock(private val onFrame: (Int) -> Unit) : MonotonicFrameClock {
        var frames = 0
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frames++
            val result = onFrame(frames * FRAME_NANOS)
            this.onFrame(frames)
            currentCoroutineContext().ensureActive()
            return result
        }
    }

    private inner class Harness(
        saved: Int?,
        index: Int,
        initial: List<VideoItemUIState>,
        restoreOverride: ((Int?) -> Boolean)? = null,
        onFrame: Harness.(Int) -> Unit = {},
    ) {
        var itemsVar = initial
        var restoreSucceeds: (Int?) -> Boolean = restoreOverride ?: { id -> id != null && itemsVar.any { it.id == id } }
        var activeVar = true
        val events = mutableListOf<String>()
        val clock = FakeFrameClock { frame -> onFrame(this@Harness, frame) }
        val memory: GridFocusMemory = GridFocusMemory(
            lastFocusedId = mutableStateOf(saved),
            lastFocusedIndex = mutableStateOf(index),
            items = { itemsVar },
            contentFocusActive = { activeVar },
            requestRestore = {
                val id = memory.restoreId
                val ok = restoreSucceeds(id)
                events += "restore:$id:$ok"
                ok
            },
            requestGrid = {
                events += "grid"
                true
            },
        )
        private lateinit var scope: CoroutineScope
        private var job: Job? = null

        fun start(scope: CoroutineScope) {
            this.scope = scope
            job = scope.launch(clock) { memory.reconcile() }
        }

        fun changeItems(new: List<VideoItemUIState>) {
            itemsVar = new
            job?.cancel()
            job = scope.launch(clock) { memory.reconcile() }
        }

        fun restoreEvents() = events.filter { it.startsWith("restore:") }
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
