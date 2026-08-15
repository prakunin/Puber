package com.kino.puber.domain.interactor.prefetch

import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.data.repository.ItemDetailsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DetailsPrefetcherTest {

    private val details = mockk<ItemDetailsRepository>(relaxed = true)
    private val foreground = AppForegroundState()

    private val focusedDwell = 250.milliseconds
    private val neighbourDwell = 750.milliseconds

    /** Past every threshold, so a case that expects nothing has genuinely given it the chance. */
    private val pastEveryDwell = 5.seconds

    private fun TestScope.prefetcher(maxConcurrent: Int = 2): DetailsPrefetcher = DetailsPrefetcher(
        details = details,
        foreground = foreground,
        focusedDwell = focusedDwell,
        neighbourDwell = neighbourDwell,
        recentlyWarmedWindow = 60.seconds,
        maxConcurrent = maxConcurrent,
        scope = backgroundScope,
        clock = { currentTime },
    )

    private fun surface() = DetailsPrefetcher.SurfaceId()

    @Test
    fun travellingThroughARowIssuesNothing() = runTest {
        // The whole point of the dwell: holding the D-pad must not cost a request per card.
        val prefetcher = prefetcher()
        val surface = surface()

        repeat(TRAVELLED_CARDS) { step ->
            prefetcher.onFocused(surface, itemId = step, neighbours = listOf(step + 1))
            advanceTimeBy(focusedDwell / 2)
        }
        runCurrent()

        coVerify(exactly = 0) { details.warmItemDetails(any()) }
    }

    @Test
    fun afterTheFocusedDwellOnlyTheFocusedCardIsWarmed() = runTest {
        val prefetcher = prefetcher()

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2, 3))
        advanceTimeBy(focusedDwell + TICK)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
        coVerify(exactly = 0) { details.warmItemDetails(2) }
        coVerify(exactly = 0) { details.warmItemDetails(3) }
    }

    @Test
    fun afterTheNeighbourDwellTheNeighboursAreWarmedInTheGivenOrder() = runTest {
        val prefetcher = prefetcher()

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2, 3, 4))
        advanceTimeBy(neighbourDwell + TICK)
        runCurrent()

        coVerifyOrder {
            details.warmItemDetails(1)
            details.warmItemDetails(2)
            details.warmItemDetails(3)
            details.warmItemDetails(4)
        }
    }

    @Test
    fun aChangeOfFocusDropsTheNeighboursOfThePreviousPosition() = runTest {
        val prefetcher = prefetcher()
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = listOf(2, 3))
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 4, neighbours = listOf(5))
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
        coVerify(exactly = 0) { details.warmItemDetails(2) }
        coVerify(exactly = 0) { details.warmItemDetails(3) }
        coVerify(exactly = 1) { details.warmItemDetails(4) }
        coVerify(exactly = 1) { details.warmItemDetails(5) }
    }

    @Test
    fun aChangeOfFocusDropsAFocusedWarmThatHasNotStartedYet() = runTest {
        val prefetcher = prefetcher()
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell / 2)
        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 0) { details.warmItemDetails(1) }
        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun rockingFocusBackAndForthDoesNotWarmTheSameCardTwice() = runTest {
        val prefetcher = prefetcher()
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
    }

    @Test
    fun aWarmThatCompletedOnAStoredValueStillCountsAsWarmed() = runTest {
        // A revalidation that failed over a usable stored value returns normally from the
        // repository, and that value is what the details screen would read — so it is warm.
        // Retrying it would only re-run a request the cache's own freshness rule just declined.
        val prefetcher = prefetcher()
        val surface = surface()
        coEvery { details.warmItemDetails(1) } returns Unit

        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
    }

    @Test
    fun aFailedWarmCanBeAttemptedAgain() = runTest {
        // Nothing was cached, so the card is still cold. Suppressing the retry would leave it cold
        // for a minute over a single dropped request.
        val prefetcher = prefetcher()
        val surface = surface()
        coEvery { details.warmItemDetails(1) } throws IllegalStateException("network down")

        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 2) { details.warmItemDetails(1) }
    }

    @Test
    fun aFailedWarmDoesNotEscape() = runTest {
        // Reported anywhere, this would surface as a snackbar or an error state on a screen the user
        // is happily reading. The test fails if the exception reaches the scope.
        val prefetcher = prefetcher()
        coEvery { details.warmItemDetails(any()) } throws IllegalStateException("network down")

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2))
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun noMoreThanMaxConcurrentWarmsAreInFlight() = runTest {
        val prefetcher = prefetcher(maxConcurrent = 2)
        val release = CompletableDeferred<Unit>()
        var inFlight = 0
        var peak = 0
        coEvery { details.warmItemDetails(any()) } coAnswers {
            inFlight += 1
            peak = maxOf(peak, inFlight)
            release.await()
            inFlight -= 1
        }

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2, 3, 4))
        advanceTimeBy(neighbourDwell + TICK)
        runCurrent()

        assertEquals(2, peak)
        coVerify(exactly = 0) { details.warmItemDetails(4) }
        release.complete(Unit)
        advanceTimeBy(TICK)
        runCurrent()
        coVerify(exactly = 1) { details.warmItemDetails(4) }
    }

    @Test
    fun aCandidateAbandonedWhileWaitingForASlotCanBeWarmedLater() = runTest {
        // It never reached the network, so nothing about it is warm. Left counted as in flight it
        // would be refused for the rest of the session — the one card the user then opens.
        val prefetcher = prefetcher(maxConcurrent = 1)
        val release = CompletableDeferred<Unit>()
        coEvery { details.warmItemDetails(1) } coAnswers { release.await() }
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = listOf(2))
        advanceTimeBy(neighbourDwell + TICK)
        runCurrent()
        coVerify(exactly = 0) { details.warmItemDetails(2) }

        prefetcher.onFocused(surface, itemId = 3, neighbours = emptyList())
        release.complete(Unit)
        advanceTimeBy(pastEveryDwell)
        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun noWarmStartsWhileTheAppIsOffScreen() = runTest {
        val prefetcher = prefetcher()
        foreground.onLeftForeground()

        prefetcher.onFocused(surface(), itemId = 1, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 0) { details.warmItemDetails(1) }

        foreground.onEnteredForeground()
        advanceTimeBy(TICK)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(1) }
    }

    @Test
    fun aWarmQueuedForASlotDoesNotStartAfterTheAppLeaves() = runTest {
        // A card can queue for a slot while the app is on screen and reach the front of the queue
        // long after the user has left. Checking the foreground only on the way in misses that.
        val prefetcher = prefetcher(maxConcurrent = 1)
        val release = CompletableDeferred<Unit>()
        coEvery { details.warmItemDetails(1) } coAnswers { release.await() }

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2))
        advanceTimeBy(neighbourDwell + TICK)
        runCurrent()

        foreground.onLeftForeground()
        release.complete(Unit)
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 0) { details.warmItemDetails(2) }

        foreground.onEnteredForeground()
        advanceTimeBy(TICK)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun theNeighbourDwellIsMeasuredFromTheMomentFocusLanded() = runTest {
        // Not from whenever the focused card's own warm got away. That warm can be held up — by a
        // busy slot, or by the app being off screen — and the neighbours' threshold must not start
        // over behind it.
        val prefetcher = prefetcher()
        foreground.onLeftForeground()

        prefetcher.onFocused(surface(), itemId = 1, neighbours = listOf(2))
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        foreground.onEnteredForeground()
        advanceTimeBy(TICK)
        runCurrent()

        // Both thresholds elapsed long ago, so returning to the screen owes both warms at once.
        coVerify(exactly = 1) { details.warmItemDetails(1) }
        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun invalidateForgetsWhatWasWarmed() = runTest {
        // A domain switch or a logout empties the details cache. Nothing it held is warm any more,
        // and an id suppressed on the strength of a vanished entry is the card the user then opens.
        val prefetcher = prefetcher()
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        runCurrent()

        prefetcher.invalidate()

        prefetcher.onFocused(surface, itemId = 2, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        prefetcher.onFocused(surface, itemId = 1, neighbours = emptyList())
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 2) { details.warmItemDetails(1) }
    }

    @Test
    fun aWarmAlreadyInFlightFinishesAfterTheAppLeaves() = runTest {
        val prefetcher = prefetcher()
        val release = CompletableDeferred<Unit>()
        var finished = false
        coEvery { details.warmItemDetails(1) } coAnswers {
            release.await()
            finished = true
        }

        prefetcher.onFocused(surface(), itemId = 1, neighbours = emptyList())
        advanceTimeBy(focusedDwell + TICK)
        foreground.onLeftForeground()
        release.complete(Unit)
        runCurrent()

        assertTrue(finished)
    }

    @Test
    fun disposingAnOldSurfaceDoesNotCancelTheActiveSurfacesSchedule() = runTest {
        // A tab or navigation transition disposes the outgoing surface *after* the incoming one has
        // taken focus. Dropping the schedule on that late dispose would silence the new screen.
        val prefetcher = prefetcher()
        val outgoing = surface()
        val incoming = surface()

        prefetcher.onFocused(outgoing, itemId = 1, neighbours = emptyList())
        prefetcher.onFocused(incoming, itemId = 2, neighbours = emptyList())
        prefetcher.onSurfaceGone(outgoing)
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 1) { details.warmItemDetails(2) }
    }

    @Test
    fun disposingTheActiveSurfaceDropsItsSchedule() = runTest {
        val prefetcher = prefetcher()
        val surface = surface()

        prefetcher.onFocused(surface, itemId = 1, neighbours = listOf(2))
        prefetcher.onSurfaceGone(surface)
        advanceTimeBy(pastEveryDwell)
        runCurrent()

        coVerify(exactly = 0) { details.warmItemDetails(any()) }
    }

    private companion object {
        /** Enough cards that a per-card request would be unmistakable. */
        const val TRAVELLED_CARDS = 8

        /** Just past a threshold, so a case sits on the far side of it rather than exactly on it. */
        val TICK: Duration = 1.milliseconds
    }
}
