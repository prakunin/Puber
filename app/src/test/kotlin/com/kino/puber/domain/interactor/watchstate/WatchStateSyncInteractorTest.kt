package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.repository.WatchStateSyncCursor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class WatchStateSyncInteractorTest {

    /** Long enough that a case can tell "still parked" from "moved on" by advancing past it. */
    private val chunkPause = 1.minutes

    /** Comfortably more pauses than any walk in these cases takes, so none is left half-run. */
    private val pastEveryPause = chunkPause.inWholeMilliseconds * 5

    private val api = mockk<KinoPubApiClient>()
    private val repository = mockk<WatchStateRepository>(relaxed = true)
    private var now = 1_000L

    /**
     * Stands in for the row the repository keeps the bookmark in, so a run reads back what the
     * previous one stored.
     */
    private var cursor = WatchStateSyncCursor()

    private val interactor = WatchStateSyncInteractor(
        api = api,
        repository = repository,
        clock = { now },
        staleAfter = 1.hours,
        // The cooldown between runs has its own test; here it would only stop back-to-back calls
        // these cases make on purpose.
        minTimeBetweenRuns = Duration.ZERO,
        // Likewise the pacing: these cases are about what a walk reads and writes, not about how it
        // is spread out, and a pause they never advance past would simply stall them.
        pauseBetweenChunks = Duration.ZERO,
    )

    @BeforeEach
    fun setUp() {
        coEvery { repository.syncCursor() } answers { cursor }
        coEvery { repository.saveSyncCursor(any()) } answers { cursor = firstArg() }
        coEvery { repository.recordHistoryPage(any(), any(), any(), any()) } answers { cursor = arg(3) }
        coEvery { repository.clear() } answers { cursor = WatchStateSyncCursor() }
        coEvery { api.getWatchingList(onlySubscribed = false) } returns success()
        coEvery { api.getWatchingMovies() } returns success()
        stubHistory(pages = 1)
    }

    @Test
    fun sync_sendsSerialsAndBareMoviesDownDifferentPaths() = runTest {
        val series = item(id = 1, type = ItemType.SERIAL)
        val movie = item(id = 2, type = ItemType.MOVIE)
        coEvery { api.getWatchingList(onlySubscribed = false) } returns success(series)
        coEvery { api.getWatchingMovies() } returns success(movie)
        val recordedSeries = slot<List<Item>>()
        val recordedMovies = slot<List<Item>>()
        coEvery { repository.recordFromServer(capture(recordedSeries), any()) } returns Unit
        coEvery { repository.recordInProgress(capture(recordedMovies), any()) } returns Unit

        assertTrue(interactor.syncIfStale())

        assertEquals(listOf(series), recordedSeries.captured)
        assertEquals(listOf(movie), recordedMovies.captured)
    }

    @Test
    fun sync_exposesLastHistoryPageProgress() = runTest {
        stubHistory(pages = 3)

        assertTrue(interactor.syncIfStale())

        assertFalse(interactor.progress.value.isSyncing)
        assertEquals(3, interactor.progress.value.currentPage)
        assertEquals(3, interactor.progress.value.totalPages)
        assertEquals(3, interactor.progress.value.totalHistoryItems)
    }

    @Test
    fun sync_leavesTheHistoryAloneWhenTheSerialsListFailed() = runTest {
        // Without that list every series in the history would have to be skipped — guessing would
        // mark every show ever played as finished — and the pass could not record how far it got.
        // Walking hundreds of pages to index only the movies is not worth the requests.
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))

        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 0) { api.getHistoryData(any()) }
        coVerify(exactly = 0) { repository.recordHistoryPage(any(), any(), any(), any()) }
    }

    @Test
    fun sync_doesNotCallTheWalkCompleteWhenTheSerialsListFailed() = runTest {
        // Claiming the walk here would suppress the next full pass for a week, and let the prune
        // below delete every series row the previous pass had stamped.
        stubHistory(pages = 1)
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))

        assertTrue(interactor.syncIfStale())

        assertFalse(cursor.fullHistoryWalkDone)
        assertNull(cursor.lastReconciledAt)
        assertNull(cursor.lastSyncAt)
        coVerify(exactly = 0) { repository.pruneStaleRows(any()) }
    }

    @Test
    fun reconciliation_keepsSeriesRowsWhenTheSerialsListFailed() = runTest {
        // The pass that reconciles is the one that deletes. A run that could not read a single
        // series must not be the one to decide which series the account no longer has.
        val interactor = reconcilingInteractor()
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        coVerify(exactly = 1) { repository.pruneStaleRows(1L) }

        now += 8.days.inWholeMilliseconds
        stubHistory(pages = 2)
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))
        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 0) { repository.pruneStaleRows(2L) }
        // The next run with the list in hand still walks the whole history and prunes then.
        coEvery { api.getWatchingList(onlySubscribed = false) } returns success()
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        coVerify(exactly = 1) { repository.pruneStaleRows(2L) }
    }

    @Test
    fun sync_leavesTheHistoryAloneWhenTheMoviesListFailed() = runTest {
        // A film the account started and later cleared from its history exists in that list and
        // nowhere else, so a walk without it cannot see everything the prune assumes it saw.
        coEvery { api.getWatchingMovies() } returns Result.failure(IllegalStateException("boom"))

        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 0) { api.getHistoryData(any()) }
        coVerify(exactly = 0) { repository.recordHistoryPage(any(), any(), any(), any()) }
        assertFalse(cursor.fullHistoryWalkDone)
        assertNull(cursor.lastSyncAt)
    }

    @Test
    fun reconciliation_keepsMovieRowsWhenTheMoviesListFailed() = runTest {
        // Same rule as for the serials list: the run that cannot read a source must not be the one
        // deciding which of that source's rows the account no longer has.
        val interactor = reconcilingInteractor()
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        coVerify(exactly = 1) { repository.pruneStaleRows(1L) }

        now += 8.days.inWholeMilliseconds
        stubHistory(pages = 2)
        coEvery { api.getWatchingMovies() } returns Result.failure(IllegalStateException("boom"))
        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 0) { repository.pruneStaleRows(2L) }
        // And the pass stays open, so the next run with the list in hand prunes instead.
        coEvery { api.getWatchingMovies() } returns success()
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        coVerify(exactly = 1) { repository.pruneStaleRows(2L) }
    }

    /**
     * Every write is a transaction, and every transaction re-runs the query behind the repository's
     * snapshot, which rebuilds the whole id-to-state map. Paid once per page across a first walk of
     * hundreds of pages, that dominates the cost of the walk — and none of it is visible until the
     * walk finishes anyway.
     */
    @Test
    fun theWalkWritesOncePerBatchOfPagesRatherThanOncePerPage() = runTest {
        // Twenty pages fall as one full chunk of fifteen and a remainder of five — two writes where
        // one per page would have been twenty.
        stubHistory(pages = 20)

        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 2) { repository.recordHistoryPage(any(), any(), any(), any()) }
    }

    /**
     * OkHttp allows five requests in flight per host. A first walk is hundreds of pages long, so run
     * flat out it holds those slots for its whole duration — in front of every request the screen
     * the user is looking at is waiting on. Reading in chunks with a pause between them is what
     * turns that burst into a trickle.
     */
    @Test
    fun theWalkStandsDownBetweenChunksRatherThanRunningFlatOut() = runTest {
        val interactor = pacedInteractor()
        stubHistory(pages = 20)

        backgroundScope.launch { interactor.syncIfStale() }
        runCurrent()

        // One chunk goes out, and then the walk waits rather than reaching for the next page.
        coVerify(exactly = 1) { api.getHistoryData(15) }
        coVerify(exactly = 0) { api.getHistoryData(16) }

        advanceTimeBy(chunkPause.inWholeMilliseconds + 1)
        runCurrent()

        coVerify(exactly = 1) { api.getHistoryData(16) }
    }

    /** The pause spreads the walk out; it must not cost it pages. */
    @Test
    fun aPacedWalkStillReadsEveryPage() = runTest {
        val interactor = pacedInteractor()
        stubHistory(pages = 20)

        backgroundScope.launch { interactor.syncIfStale() }
        // Advancing well past every pause the walk can take, rather than to idle: the walk runs in
        // the background scope, which `advanceUntilIdle` does not drain.
        advanceTimeBy(pastEveryPause)
        runCurrent()

        coVerify(exactly = 1) { api.getHistoryData(20) }
        assertTrue(cursor.fullHistoryWalkDone)
    }

    /**
     * A walk is minutes long, so the app can easily be left while one is running. Carrying on then
     * spends the network on an index nothing is going to read until the user comes back.
     *
     * The chunk boundary is the granularity: the chunk already in flight finishes and is written,
     * which is what leaves the walk somewhere it can be picked up from.
     */
    @Test
    fun theWalkParksBetweenChunksWhileTheAppIsOffScreen() = runTest {
        val foreground = AppForegroundState()
        val interactor = pacedInteractor(awaitForeground = foreground::awaitForeground)
        stubHistory(pages = 20)
        backgroundScope.launch { interactor.syncIfStale() }
        runCurrent()

        // Left during the gap between chunks, which is the window the check before it cannot see.
        foreground.onLeftForeground()
        advanceTimeBy(pastEveryPause)
        runCurrent()

        // The chunk in flight was finished; nothing past the boundary went out.
        coVerify(exactly = 1) { api.getHistoryData(15) }
        coVerify(exactly = 0) { api.getHistoryData(16) }
    }

    /**
     * The startup wait is five seconds and the walk is minutes long, so the user leaving in between
     * is ordinary. A run that went ahead anyway would spend both watching requests and a full chunk
     * of history before reaching the first check.
     */
    @Test
    fun theWalkSendsNothingAtAllWhileTheAppIsAlreadyOffScreen() = runTest {
        val foreground = AppForegroundState()
        foreground.onLeftForeground()
        val interactor = pacedInteractor(awaitForeground = foreground::awaitForeground)
        stubHistory(pages = 20)

        backgroundScope.launch { interactor.syncIfStale() }
        advanceTimeBy(pastEveryPause)
        runCurrent()

        coVerify(exactly = 0) { api.getWatchingList(any()) }
        coVerify(exactly = 0) { api.getWatchingMovies() }
        coVerify(exactly = 0) { api.getHistoryData(any()) }
    }

    /** Parking has to be a wait, not an end: coming back continues the walk where it stopped. */
    @Test
    fun aParkedWalkCarriesOnWhenTheAppComesBack() = runTest {
        val foreground = AppForegroundState()
        foreground.onLeftForeground()
        val interactor = pacedInteractor(awaitForeground = foreground::awaitForeground)
        stubHistory(pages = 20)
        backgroundScope.launch { interactor.syncIfStale() }
        advanceTimeBy(pastEveryPause)
        runCurrent()

        foreground.onEnteredForeground()
        advanceTimeBy(pastEveryPause)
        runCurrent()

        coVerify(exactly = 1) { api.getHistoryData(20) }
        assertTrue(cursor.fullHistoryWalkDone)
    }

    /**
     * A walk now spans minutes of paced requests, so a trigger arriving mid-walk would queue behind
     * one rather than brushing past it. It has nothing to add — the run under way is the one it
     * would have started — so it has to leave immediately instead of waiting the walk out.
     */
    @Test
    fun aTriggerArrivingMidWalkIsDroppedRatherThanQueuedBehindIt() = runTest {
        val interactor = pacedInteractor()
        stubHistory(pages = 20)
        backgroundScope.launch { interactor.syncIfStale() }
        runCurrent()

        assertFalse(interactor.syncIfStale())

        // Having waited would have let the first walk run on; it is still parked where it was.
        coVerify(exactly = 0) { api.getHistoryData(16) }
    }

    /**
     * A forced trigger wants the opposite. It is asking for a pass that has not happened — normally
     * because what the index describes has changed underneath it — and the run in flight is not that
     * pass. Dropping it the way an ordinary trigger is dropped would make `force` quietly mean
     * nothing exactly when it is being relied on, so it waits its turn instead.
     */
    @Test
    fun aForcedTriggerWaitsForTheWalkInFlightRatherThanGivingUp() = runTest {
        val interactor = pacedInteractor()
        stubHistory(pages = 20)
        backgroundScope.launch { interactor.syncIfStale() }
        runCurrent()

        val forced = async { interactor.syncIfStale(force = true) }
        advanceTimeBy(pastEveryPause)
        runCurrent()

        assertTrue(forced.await())
    }

    /**
     * A parked run can start hours after it was asked for, and the bookmark it walks from has to be
     * the one on disk when it actually starts. A logout wipes that row; resurrecting the copy read
     * before parking would tell the next account its history had already been indexed.
     */
    @Test
    fun aParkedRunWalksFromTheBookmarkOnDiskWhenItFinallyStarts() = runTest {
        val foreground = AppForegroundState()
        foreground.onLeftForeground()
        val interactor = pacedInteractor(awaitForeground = foreground::awaitForeground)
        stubHistory(pages = 2)
        backgroundScope.launch { interactor.syncIfStale() }
        advanceTimeBy(pastEveryPause)
        runCurrent()

        // Replaced while the run was parked: a pass is already done and page 1 holds nothing new.
        cursor = WatchStateSyncCursor(historyNewestSeen = 999L, fullHistoryWalkDone = true)

        foreground.onEnteredForeground()
        advanceTimeBy(pastEveryPause)
        runCurrent()

        // Walking from the copy read before parking would have made this a full pass and read both
        // pages; from the bookmark on disk, page 1 already says it has caught up.
        coVerify(exactly = 0) { api.getHistoryData(2) }
    }

    /**
     * A finished index only ever reads far enough to catch up, which is the whole point of the
     * bookmark — and exactly what a user asking for a rebuild is trying to get past.
     */
    @Test
    fun anOrdinaryRunOverAFinishedIndexStopsAtTheFirstPageItAlreadyKnows() = runTest {
        stubHistory(pages = 3)
        cursor = WatchStateSyncCursor(historyNewestSeen = 1_000L, fullHistoryWalkDone = true)

        assertTrue(interactor.syncIfStale(force = true))

        coVerify(exactly = 0) { api.getHistoryData(2) }
        assertTrue(cursor.fullHistoryWalkDone)
    }

    /**
     * The rebuild is for an index the user no longer trusts, so it may not wait for the
     * reconciliation timer the automatic pass is spaced by: it reopens the walk itself.
     */
    @Test
    fun aRebuildWalksTheWholeHistoryAgainOverAFinishedIndex() = runTest {
        stubHistory(pages = 3)
        cursor = WatchStateSyncCursor(historyNewestSeen = 1_000L, fullHistoryWalkDone = true)

        assertTrue(interactor.syncIfStale(force = true, rebuild = true))

        coVerify(exactly = 1) { api.getHistoryData(3) }
        assertTrue(cursor.fullHistoryWalkDone)
    }

    /**
     * Rows are pruned by generation, so a rebuild that did not open a new one would restamp
     * everything it saw and delete nothing — leaving behind exactly the stale rows it was asked to
     * clear out.
     */
    @Test
    fun aRebuildOpensANewGenerationAndPrunesWhatItNeverSaw() = runTest {
        stubHistory(pages = 3)
        val previousGeneration = 4L
        cursor = WatchStateSyncCursor(
            historyNewestSeen = 1_000L,
            fullHistoryWalkDone = true,
            generation = previousGeneration,
        )

        assertTrue(interactor.syncIfStale(force = true, rebuild = true))

        assertTrue(cursor.generation > previousGeneration)
        coVerify(exactly = 1) { repository.pruneStaleRows(cursor.generation) }
    }

    /** Batching may not lose an entry: every page the walk read still has to reach the repository. */
    @Test
    fun aBatchedWalkStillWritesEveryEntryItRead() = runTest {
        stubHistory(pages = 20)
        val written = mutableListOf<List<History>>()
        coEvery {
            repository.recordHistoryPage(capture(written), any(), any(), any())
        } answers { cursor = arg(3) }

        assertTrue(interactor.syncIfStale())

        assertEquals((1..20).toList(), written.flatten().map { it.item.id }.sorted())
    }

    /**
     * The walk ends when the server runs out, which lands wherever it lands — usually mid-batch. A
     * remainder left unwritten would be re-read by every later run, because the cursor that would
     * have said otherwise is written with it.
     */
    @Test
    fun aWalkEndingMidBatchStillWritesTheRemainder() = runTest {
        stubHistory(pages = 3)
        val written = mutableListOf<List<History>>()
        coEvery {
            repository.recordHistoryPage(capture(written), any(), any(), any())
        } answers { cursor = arg(3) }

        assertTrue(interactor.syncIfStale())

        assertEquals((1..3).toList(), written.flatten().map { it.item.id }.sorted())
        assertTrue(cursor.fullHistoryWalkDone)
    }

    /**
     * A page that fails ends the run, but what was already read is still good and its cursor is
     * still true. Dropping it would make the next run fetch those pages again for nothing.
     */
    @Test
    fun aWalkInterruptedMidBatchStillWritesWhatItHadRead() = runTest {
        stubHistory(pages = 20)
        coEvery { api.getHistoryData(3) } returns Result.failure(IllegalStateException("interrupted"))
        val written = mutableListOf<List<History>>()
        coEvery {
            repository.recordHistoryPage(capture(written), any(), any(), any())
        } answers { cursor = arg(3) }

        assertTrue(interactor.syncIfStale())

        assertEquals(listOf(1, 2), written.flatten().map { it.item.id }.sorted())
    }

    @Test
    fun sync_writesTheWatchingListsAfterTheHistory() = runTest {
        // History describes everything ever played; the watching lists describe the present, so
        // they have to land last.
        val series = item(id = 1, type = ItemType.SERIAL)
        coEvery { api.getWatchingList(onlySubscribed = false) } returns success(series)

        assertTrue(interactor.syncIfStale())

        coVerifyOrder {
            repository.recordHistoryPage(any(), any(), any(), any())
            repository.recordFromServer(listOf(series), any())
        }
    }

    @Test
    fun sync_throwsAwayWhatItWroteWhenTheSessionEndedMidRun() = runTest {
        // Those rows and that walk cursor belong to the account that just signed out; inheriting
        // either would show one account's viewing history under the next one.
        coEvery { api.getWatchingMovies() } coAnswers {
            interactor.invalidate()
            success()
        }

        assertFalse(interactor.syncIfStale())

        assertNull(cursor.lastSyncAt)
        assertEquals(0L, cursor.historyNewestSeen)
        assertFalse(cursor.fullHistoryWalkDone)
        coVerify(atLeast = 1) { repository.clear() }
    }

    @Test
    fun anUnfinishedWalkIsNotRestartedByTheNextResume() = runTest {
        // A walk that runs out of page budget never stamps lastSyncAt, so the staleness check
        // cannot see it. The ON_RESUME right after the main screen appears would otherwise start
        // the next 300 pages immediately, which is exactly what the budget exists to prevent.
        val interactor = WatchStateSyncInteractor(
            api = api,
            repository = repository,
            clock = { now },
            staleAfter = 1.hours,
            minTimeBetweenRuns = 5.minutes,
        )
        stubHistory(pages = 4)
        coEvery { api.getHistoryData(2) } returns Result.failure(IllegalStateException("interrupted"))
        assertTrue(interactor.syncIfStale())
        assertNull(cursor.lastSyncAt)

        assertFalse(interactor.syncIfStale())

        now += 5.minutes.inWholeMilliseconds
        stubHistory(pages = 4)
        assertTrue(interactor.syncIfStale())
    }

    @Test
    fun aNewSessionIsIndexedWithoutWaitingOutThePreviousOnesCooldown() = runTest {
        val interactor = WatchStateSyncInteractor(
            api = api,
            repository = repository,
            clock = { now },
            staleAfter = 1.hours,
            minTimeBetweenRuns = 5.minutes,
        )
        assertTrue(interactor.syncIfStale())

        interactor.invalidate()

        assertTrue(interactor.syncIfStale())
    }

    @Test
    fun aCompletedFullWalkDropsWhatItNeverSaw() = runTest {
        // Nothing in the incremental sources ever says a title is gone — a cleared history or a
        // mark undone on another device simply stops being mentioned. Only a pass that read the
        // history to its end knows what is missing.
        stubHistory(pages = 2)

        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 1) { repository.pruneStaleRows(1L) }
        assertEquals(now, cursor.lastReconciledAt)
    }

    @Test
    fun anIncrementalPassPrunesNothing() = runTest {
        // It only read as far back as the last walk, so everything older is unseen but not gone.
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        now += 1.hours.inWholeMilliseconds
        stubHistory(pages = 2)

        assertTrue(interactor.syncIfStale(force = true))

        coVerify(exactly = 1) { repository.pruneStaleRows(any()) }
    }

    @Test
    fun theIndexIsReconciledAgainAfterTheInterval() = runTest {
        val interactor = reconcilingInteractor()
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        assertEquals(1L, cursor.generation)

        now += 8.days.inWholeMilliseconds
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())

        // A fresh pass, walked in full again, pruning whatever the previous one left behind.
        assertEquals(2L, cursor.generation)
        coVerify(exactly = 1) { repository.pruneStaleRows(2L) }
    }

    @Test
    fun aWalkStillInProgressIsNotInterruptedByAReconciliation() = runTest {
        // Opening a pass mid-walk would orphan the rows already stamped by this one, and the prune
        // at the end would delete them.
        val interactor = reconcilingInteractor()
        stubHistory(pages = 4)
        coEvery { api.getHistoryData(2) } returns Result.failure(IllegalStateException("interrupted"))
        assertTrue(interactor.syncIfStale())
        assertFalse(cursor.fullHistoryWalkDone)

        now += 8.days.inWholeMilliseconds
        stubHistory(pages = 4)
        assertTrue(interactor.syncIfStale())

        assertEquals(1L, cursor.generation)
    }

    private fun reconcilingInteractor() = WatchStateSyncInteractor(
        api = api,
        repository = repository,
        clock = { now },
        staleAfter = 1.hours,
        minTimeBetweenRuns = Duration.ZERO,
        reconcileAfter = 7.days,
    )

    @Test
    fun theBookmarkNeverOutlivesTheRowsItDescribes() = runTest {
        // They live in one database and are dropped together, so the app cannot end up sitting on
        // an empty index believing it had already read the whole history.
        stubHistory(pages = 2)
        assertTrue(interactor.syncIfStale())
        assertTrue(cursor.fullHistoryWalkDone)

        interactor.invalidate()

        assertEquals(WatchStateSyncCursor(), cursor)
    }

    @Test
    fun firstSync_walksTheWholeHistory() = runTest {
        stubHistory(pages = 4)

        assertTrue(interactor.syncIfStale())

        (1..4).forEach { page -> coVerify(exactly = 1) { api.getHistoryData(page) } }
        assertTrue(cursor.fullHistoryWalkDone)
    }

    @Test
    fun interruptedWalk_resumesFromWhereItStopped() = runTest {
        // The walk spans hundreds of requests; a killed app must not send it back to page 1.
        stubHistory(pages = 4)
        coEvery { api.getHistoryData(3) } returns Result.failure(IllegalStateException("killed"))
        assertTrue(interactor.syncIfStale())
        assertFalse(cursor.fullHistoryWalkDone)
        assertEquals(3, cursor.historyResumePage)

        stubHistory(pages = 4)
        assertTrue(interactor.syncIfStale(force = true))

        assertTrue(cursor.fullHistoryWalkDone)
        // The resume overlaps a couple of pages, so page 1 is read again rather than skipped past.
        coVerify(exactly = 2) { api.getHistoryData(3) }
    }

    @Test
    fun interruptedWalk_keepsWhatItAlreadyRead() = runTest {
        stubHistory(pages = 4)
        coEvery { api.getHistoryData(3) } returns Result.failure(IllegalStateException("killed"))

        assertTrue(interactor.syncIfStale())

        // Pages 1-2 were folded in before the failure, so their newest stamp survives.
        assertEquals(999L, cursor.historyNewestSeen)
    }

    @Test
    fun laterSync_stopsAtTheFirstPageWithNothingNew() = runTest {
        stubHistory(pages = 4)
        assertTrue(interactor.syncIfStale())
        now += 1.hours.inWholeMilliseconds

        assertTrue(interactor.syncIfStale(force = true))

        // Page 1 holds the newest entries, which the first walk already folded in.
        coVerify(exactly = 2) { api.getHistoryData(1) }
        coVerify(exactly = 1) { api.getHistoryData(2) }
    }

    @Test
    fun laterSync_doesNotTreatAnEmptyPageAsHavingCaughtUp() = runTest {
        // Deleted entries leave gaps in the history; an empty page says nothing about how far the
        // new entries reach.
        stubHistory(pages = 3)
        assertTrue(interactor.syncIfStale())
        coEvery { api.getHistoryData(1) } returns Result.success(
            PaginatedResponse(items = emptyList(), pagination = Pagination(current = 1, perpage = 20, total = 3))
        )

        assertTrue(interactor.syncIfStale(force = true))

        coVerify(exactly = 2) { api.getHistoryData(2) }
    }

    @Test
    fun laterSync_keepsReadingWhileEntriesAreNewerThanTheLastWalk() = runTest {
        stubHistory(pages = 3)
        assertTrue(interactor.syncIfStale())
        // Everything on pages 1-2 is newer than what the previous walk saw.
        stubHistory(pages = 3, lastSeenBase = 10_000L)

        assertTrue(interactor.syncIfStale(force = true))

        coVerify(exactly = 2) { api.getHistoryData(2) }
    }

    @Test
    fun sync_skipsWhileTheIndexIsFresh() = runTest {
        assertTrue(interactor.syncIfStale())
        now += 1.hours.inWholeMilliseconds - 1

        assertFalse(interactor.syncIfStale())

        coVerify(exactly = 1) { api.getWatchingList(onlySubscribed = false) }
    }

    @Test
    fun sync_refetchesOnceTheIndexIsStale() = runTest {
        assertTrue(interactor.syncIfStale())
        now += 1.hours.inWholeMilliseconds

        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = false) }
    }

    @Test
    fun sync_keepsWhatOneEndpointReturnedWhenTheOtherFails() = runTest {
        val movie = item(id = 2, type = ItemType.MOVIE)
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))
        coEvery { api.getWatchingMovies() } returns success(movie)

        assertTrue(interactor.syncIfStale())

        coVerify { repository.recordInProgress(listOf(movie), any()) }
    }

    @Test
    fun sync_retriesImmediatelyAfterAPartialFailure() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))

        assertTrue(interactor.syncIfStale())
        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 2) { api.getWatchingList(onlySubscribed = false) }
    }

    @Test
    fun sync_doesNotStampTheClockWhenTheHistoryFails() = runTest {
        coEvery { api.getHistoryData(any()) } returns Result.failure(IllegalStateException("boom"))

        assertTrue(interactor.syncIfStale())
        assertTrue(interactor.syncIfStale())

        coVerify(exactly = 2) { api.getWatchingMovies() }
    }

    @Test
    fun sync_doesNotStampTheClockWhenBothWatchingListsFail() = runTest {
        coEvery { api.getWatchingList(onlySubscribed = false) } returns Result.failure(IllegalStateException("boom"))
        coEvery { api.getWatchingMovies() } returns Result.failure(IllegalStateException("boom"))

        assertFalse(interactor.syncIfStale())
        assertFalse(interactor.syncIfStale())

        coVerify(exactly = 2) { api.getWatchingMovies() }
        coVerify(exactly = 0) { repository.recordInProgress(any(), any()) }
    }

    @Test
    fun invalidate_makesTheNextSyncStartFromScratch() = runTest {
        stubHistory(pages = 3)
        assertTrue(interactor.syncIfStale())

        interactor.invalidate()
        assertTrue(interactor.syncIfStale())

        assertTrue(cursor.fullHistoryWalkDone)
        coVerify(exactly = 2) { api.getHistoryData(3) }
    }

    /**
     * Page 1 carries the newest entries, so `last_seen` decreases as the page number grows — the
     * ordering the incremental pass relies on.
     */
    /** For the cases about the pacing itself, which need a pause long enough to time against. */
    private fun pacedInteractor(awaitForeground: suspend () -> Unit = {}) = WatchStateSyncInteractor(
        api = api,
        repository = repository,
        clock = { now },
        staleAfter = 1.hours,
        minTimeBetweenRuns = Duration.ZERO,
        pauseBetweenChunks = chunkPause,
        awaitForeground = awaitForeground,
    )

    private fun stubHistory(pages: Int, lastSeenBase: Long = 1_000L) {
        (1..pages).forEach { page ->
            coEvery { api.getHistoryData(page) } returns Result.success(
                PaginatedResponse(
                    items = listOf(historyEntry(itemId = page, lastSeen = lastSeenBase - page)),
                    pagination = Pagination(
                        current = page,
                        perpage = 1,
                        total = pages,
                        totalItems = pages,
                    ),
                )
            )
        }
    }

    private fun historyEntry(itemId: Int, lastSeen: Long) = History(
        item = item(id = itemId, type = ItemType.MOVIE),
        video = Video(id = itemId * 100, duration = 100, watched = 1),
        updated = lastSeen.toString(),
    )

    private fun success(vararg items: Item) = Result.success(ApiResponseList(items = items.toList()))

    private fun item(id: Int, type: ItemType) = Item(id = id, title = "Item $id", type = type)
}
