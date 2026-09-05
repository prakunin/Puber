package com.kino.puber.core.paginator

import com.kino.puber.core.collections.EquallyFunction
import com.kino.puber.core.error.ErrorEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The reducer had no tests of its own: it was only ever exercised through the view models that sit
 * on it, which is why states holding a list behind an error went unnoticed for so long. Each case
 * here names the state the list is in and the action arriving on it.
 */
class PaginatorReducerTest {

    private val error = ErrorEntity(message = "no network", code = "Network")
    private val otherError = ErrorEntity(message = "page failed", code = "Http")

    private class Harness(scope: TestScope) {
        val states = mutableListOf<Paginator.State>()
        val effects = mutableListOf<Paginator.SideEffect>()
        val store = Paginator.Store<String>(
            coroutineContext = StandardTestDispatcher(scope.testScheduler),
            comparator = EquallyFunction { old, new -> old == new },
        )

        val state: Paginator.State get() = states.last()
    }

    private fun TestScope.harness(): Harness {
        val harness = Harness(this)
        // Subscribed before anything is dispatched: the side-effect flow has no replay, so an
        // effect emitted with nobody listening would simply never arrive.
        backgroundScope.launch { harness.store.sideEffects.collect { harness.effects += it } }
        runCurrent()
        harness.store.render = { harness.states += it }
        return harness
    }

    private fun Harness.settleWith(vararg items: String) {
        store.replace(items.toList())
    }

    @Test
    fun loadNext_overAGeneralError_asksForTheNextPage() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.error(error)
        runCurrent()
        assertEquals(Paginator.State.Error(listOf("a", "b"), error), h.state)
        h.effects.clear()

        h.store.loadNext()
        runCurrent()

        // The screens draw State.Error as content, so reaching the end of that list is a load-more
        // like any other. It used to answer with State.Loading and no request behind it.
        assertEquals(listOf(Paginator.SideEffect.LoadNextPage("b")), h.effects)
        assertEquals(Paginator.State.LoadingNext(listOf("a", "b")), h.state)
    }

    @Test
    fun loadNext_overAPreviousPageError_asksForTheNextPage() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.loadPrev()
        runCurrent()
        h.store.pageError(otherError)
        runCurrent()
        assertEquals(Paginator.State.PageErrorPrev(listOf("a", "b"), otherError), h.state)
        h.effects.clear()

        h.store.loadNext()
        runCurrent()

        assertEquals(listOf(Paginator.SideEffect.LoadNextPage("b")), h.effects)
        assertEquals(Paginator.State.LoadingNext(listOf("a", "b")), h.state)
    }

    @Test
    fun loadPrev_overAGeneralError_asksForThePreviousPage() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.error(error)
        runCurrent()
        h.effects.clear()

        h.store.loadPrev()
        runCurrent()

        assertEquals(listOf(Paginator.SideEffect.LoadPrevPage("a")), h.effects)
        assertEquals(Paginator.State.LoadingPrev(listOf("a", "b")), h.state)
    }

    @Test
    fun loadNext_withNothingLoaded_leavesTheStateAloneRatherThanSpinning() = runTest {
        val h = harness()
        h.store.replace(emptyList())
        runCurrent()
        assertEquals(Paginator.State.Empty, h.state)
        h.effects.clear()

        h.store.loadNext()
        runCurrent()

        // A full-screen spinner with no request behind it never comes down again. There is no page
        // to follow here; the way out of Empty is a refresh.
        assertTrue(h.effects.isEmpty(), "expected no request, got ${h.effects}")
        assertEquals(Paginator.State.Empty, h.state)
    }

    @Test
    fun loadNext_whileTheFirstPageIsInFlight_doesNotDuplicateTheRequest() = runTest {
        val h = harness()
        h.store.restart()
        runCurrent()
        assertEquals(Paginator.State.Loading, h.state)
        h.effects.clear()

        h.store.loadNext()
        runCurrent()

        assertTrue(h.effects.isEmpty(), "expected no request, got ${h.effects}")
        assertEquals(Paginator.State.Loading, h.state)
    }

    @Test
    fun itemUpdated_appliesWhileTheListCarriesAGeneralError() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.error(error)
        runCurrent()

        h.store.itemUpdated("b")
        runCurrent()

        assertEquals(Paginator.State.Error(listOf("a", "b"), error), h.state)
    }

    @Test
    fun itemAdded_overAGeneralError_keepsTheListItAlreadyHas() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.error(error)
        runCurrent()

        h.store.itemAdded("c")
        runCurrent()

        // This used to fall through to State.Data(listOf(item)) and take the other two off the
        // screen along with the error the user was being shown.
        assertEquals(Paginator.State.Error(listOf("c", "a", "b"), error), h.state)
    }

    @Test
    fun itemAdded_withNothingLoaded_startsTheList() = runTest {
        val h = harness()
        h.store.replace(emptyList())
        runCurrent()

        h.store.itemAdded("a")
        runCurrent()

        assertEquals(Paginator.State.Data(listOf("a")), h.state)
    }

    @Test
    fun itemDeleted_emptyingASettledList_showsTheEmptyState() = runTest {
        val h = harness()
        h.settleWith("a")
        runCurrent()

        h.store.itemDeleted("a")
        runCurrent()

        assertEquals(Paginator.State.Empty, h.state)
    }

    @Test
    fun itemDeleted_emptyingAListWithAPageInFlight_holdsTheStateUntilThePageLands() = runTest {
        val h = harness()
        h.settleWith("a")
        runCurrent()
        h.store.loadNext()
        runCurrent()

        h.store.itemDeleted("a")
        runCurrent()

        // Long-standing behaviour, pinned here rather than changed: only a settled list may empty
        // the screen, and a loading state that emptied itself would leave the arriving page with
        // nowhere to land. The cost is that the removed row stays up until that page replaces it.
        assertEquals(Paginator.State.LoadingNext(listOf("a")), h.state)
    }

    @Test
    fun itemDeleted_ofSomethingNotInTheList_changesNothing() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        val before = h.state

        h.store.itemDeleted("z")
        runCurrent()

        assertEquals(before, h.state)
    }

    @Test
    fun itemUpdated_keepsTheStateItArrivedIn() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.loadNext()
        runCurrent()

        h.store.itemUpdated("b")
        runCurrent()

        assertEquals(Paginator.State.LoadingNext(listOf("a", "b")), h.state)
    }

    @Test
    fun refresh_keepsDrawingTheListItHas() = runTest {
        val h = harness()
        h.settleWith("a", "b")
        runCurrent()
        h.store.error(error)
        runCurrent()
        h.effects.clear()

        h.store.refresh()
        runCurrent()

        assertEquals(listOf(Paginator.SideEffect.LoadFirstPage), h.effects)
        assertEquals(Paginator.State.Refreshing(listOf("a", "b")), h.state)
    }
}
