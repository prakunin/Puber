package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.repository.WatchStateSyncCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class WatchStateSyncProgress(
    val isSyncing: Boolean = false,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val totalHistoryItems: Int? = null,
)

/**
 * Fills the local watch-state index from the account's watching lists and its history.
 *
 * The catalogue endpoints report nothing about what has been watched. The watching lists cover what
 * is still in progress; only the history knows what has been *finished*, which is what the "hide
 * watched" setting is actually about. The history is walked in full once, then only far enough to
 * pick up entries newer than the last walk.
 */
class WatchStateSyncInteractor(
    private val api: KinoPubApiClient,
    private val repository: WatchStateRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val staleAfter: Duration = 1.hours,
    private val minTimeBetweenRuns: Duration = 5.minutes,
    private val reconcileAfter: Duration = 7.days,
    private val pauseBetweenChunks: Duration = 20.seconds,
    private val requestScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /**
     * Held at the boundary between chunks while the app is off screen. A walk is minutes long, so
     * without this it keeps pulling history over the network well after the user has left — for a
     * result nothing is going to read until they come back. Defaults to never waiting, which is what
     * the cases about the walk's own behaviour want.
     */
    private val awaitForeground: suspend () -> Unit = {},
) {

    private val mutex = Mutex()
    private val requestLock = Any()
    private var requestedSync: Job? = null
    private val mutableProgress = MutableStateFlow(WatchStateSyncProgress())
    val progress: StateFlow<WatchStateSyncProgress> = mutableProgress.asStateFlow()

    /** Bumped on logout, so a sync started under the previous session cannot claim to be current. */
    @Volatile
    private var generation = 0L

    /**
     * When the last run stopped, complete or not. Deliberately not persisted: it exists to space
     * runs out within one app session, not to survive it.
     */
    private var lastRunAt: Long? = null

    /**
     * Refreshes the index when the last sync is older than [staleAfter]. Returns true when rows
     * were written, so a caller can refresh what it is showing.
     */
    suspend fun syncIfStale(force: Boolean = false): Boolean {
        // A walk now spans minutes of deliberately paced requests, so the two kinds of caller want
        // opposite things from one already running.
        //
        // An ordinary trigger has nothing to add — the run under way is the one it would have
        // started — so it leaves rather than waiting the walk out to find the index already fresh.
        // A forced one is asking for a pass that has not happened yet, usually because what the
        // index describes has changed underneath it, and the run in flight is not that pass; giving
        // up would make `force` quietly mean nothing whenever it is most needed. So it waits its
        // turn instead. (Cancellable, and cheap in practice: a force comes from the screen, and a
        // walk only parks while there is no screen.)
        if (force) mutex.lock() else if (!mutex.tryLock()) return false
        try {
            mutableProgress.value = WatchStateSyncProgress(
                isSyncing = true,
                totalHistoryItems = mutableProgress.value.totalHistoryItems,
            )
            return runSync(force)
        } finally {
            mutableProgress.value = mutableProgress.value.copy(isSyncing = false)
            mutex.unlock()
        }
    }

    /**
     * Requests a sync owned by this application-wide interactor rather than by the screen that
     * initiated it. Closing settings therefore does not abandon a large first history walk.
     */
    fun requestSync(force: Boolean = true) {
        synchronized(requestLock) {
            if (requestedSync?.isActive == true) return
            requestedSync = requestScope.launch {
                try {
                    syncIfStale(force = force)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // Callers observe the run ending and retain the last durable cursor. The regular
                    // foreground sync will retry; a settings screen must not own this scope's error.
                    log(error, "Failed to sync watch state from settings")
                } finally {
                    synchronized(requestLock) {
                        requestedSync = null
                    }
                }
            }
        }
    }

    private suspend fun runSync(force: Boolean): Boolean {
        if (!force && !isRunDue(clock(), repository.syncCursor())) return false

        // Before the first request, not just between chunks. The startup wait is easily outlived by
        // the user leaving, and a run that went ahead anyway would spend both watching requests and
        // a whole chunk of history on an app that is no longer on screen.
        awaitForeground()

        // Both read after the wait rather than before it. Parked, this run can start hours after it
        // was asked for: every stamp below is meant to say when it actually ran, and the bookmark it
        // walks from has to be the one on disk now — a logout in between wipes it, and resurrecting
        // the copy read earlier would tell the next account its history had already been indexed.
        val now = clock()
        lastRunAt = now
        val cursor = repository.syncCursor()

        // Held open for the whole walk, so the chunk writes below settle as one change rather than
        // one per chunk. Without it the pauses between chunks are far enough apart to clear the
        // repository's debounce, and every chunk would cost each open screen a re-map or a re-page.
        repository.beginSyncWindow()
        try {
            return indexAccount(now, cursor)
        } finally {
            repository.endSyncWindow()
        }
    }

    /**
     * The walk itself, from the watching lists through the history to the prune. Split from
     * [runSync] so the sync window has a body to wrap.
     *
     * Every exit here is a guard abandoning the run — a source that did not answer, or a session
     * that ended under it — and each has to be taken at the point the run learns of it, because
     * what follows would write rows for an account that is no longer signed in. Nesting them into
     * one exit buries the writes several levels deep and puts each condition far from the step it
     * protects.
     */
    @Suppress("ReturnCount")
    private suspend fun indexAccount(now: Long, openingCursor: WatchStateSyncCursor): Boolean {
        var cursor = openingCursor
        // Opened before anything is written, so every row this run stores belongs to the new pass.
        cursor = cursor.withReconciliationIfDue(now)
        repository.saveSyncCursor(cursor)

        val generation = this.generation
        // One stamp for the whole run, taken before the first request. Every row this run writes
        // describes the account as it was at this moment, however long the walk then takes — so a
        // toggle the user makes meanwhile is newer than all of it and survives.
        val observedAt = clock()
        val series = fetch { api.getWatchingList(onlySubscribed = false) }
        val movies = fetch { api.getWatchingMovies() }
        if (series == null && movies == null) return false

        // History first, so the lists of what is *currently* in progress get the last word: they
        // describe the present, while history describes everything that ever played.
        //
        // Both lists are required for the walk, for the same reason. The serials list is what tells
        // a finished series from one still going, so without it the walk could only index the
        // movies in the history. The movies list is the only source for a film that is started and
        // no longer in the history at all, so without it a walk that runs to the end is not the
        // complete pass the prune below takes it for, and would delete those rows. Either missing,
        // and the history waits for a run that has both rather than spending hundreds of requests
        // on a pass it is not allowed to finish.
        val walk = if (series != null && movies != null) {
            syncHistory(
                generation = generation,
                observedAt = observedAt,
                cursor = cursor,
                seriesStillInProgress = series.map(Item::id).toSet(),
            )
        } else {
            null
        }
        if (walk != null) cursor = walk.cursor
        if (!sessionSurvived(generation)) return false
        // Serials come back with episode counters; movies come back as bare ids, so all they can
        // say is "started, not finished".
        repository.recordFromServer(series.orEmpty(), observedAt)
        if (!sessionSurvived(generation)) return false
        repository.recordInProgress(movies.orEmpty(), observedAt)
        if (!sessionSurvived(generation)) return false

        // A pass that read the history to its end has seen everything the account still has, so
        // whatever it did not restamp is gone from the server and goes now. Done after the watching
        // lists so their rows carry this pass too. A walk only runs when both of those lists
        // answered, which is what makes "did not restamp" mean "gone" rather than "not asked".
        if (walk?.completedFullWalk == true) {
            repository.pruneStaleRows(cursor.generation)
            cursor = cursor.copy(lastReconciledAt = now)
        }

        // Only a complete sync earns the stamp. Half an index must not suppress retries for an
        // hour. A walk that never ran (a watching list missing) fails this the same way a failed
        // one does, which is why the lists are not re-checked here.
        val everySourceAnswered = walk != null && walk.reachedTheEnd
        if (everySourceAnswered) {
            cursor = cursor.copy(lastSyncAt = now)
        }
        repository.saveSyncCursor(cursor)
        return true
    }

    /**
     * Whether a run is due.
     *
     * [staleAfter] governs syncs that finished. A run that stopped early stamps nothing for that
     * check to see, which is what [minTimeBetweenRuns] covers: without it every resume that follows
     * an interrupted walk starts the next one straight away, and a session spent switching in and
     * out of the app pays a fresh page budget each time rather than the one run's worth that budget
     * exists to allow.
     */
    private fun isRunDue(now: Long, cursor: WatchStateSyncCursor): Boolean {
        val lastSync = cursor.lastSyncAt
        if (lastSync != null && now - lastSync < staleAfter.inWholeMilliseconds) return false
        val lastRun = lastRunAt
        return lastRun == null || now - lastRun >= minTimeBetweenRuns.inWholeMilliseconds
    }

    /**
     * Drops the index and everything the sync remembers about it, so the next session starts from
     * scratch. Bumping the generation before the wipe is what lets a run already in flight notice
     * the session ended and throw away its own writes.
     */
    suspend fun invalidate() {
        generation += 1
        mutableProgress.value = WatchStateSyncProgress()
        // A new account has to be indexed now, not after the previous one's run has cooled off.
        lastRunAt = null
        // The bookmark lives in the same database as the rows, so one wipe takes both.
        repository.clear()
    }

    /**
     * True while this run still belongs to the session it started in.
     *
     * A logout wipes the index, but a write already on its way lands after that wipe — and both the
     * rows and the walk cursor it leaves behind belong to an account that has just been signed out.
     * So the check runs after every write, and drops whatever slipped through rather than letting
     * the next account inherit it.
     */
    private suspend fun sessionSurvived(generation: Long): Boolean {
        if (generation == this.generation) return true
        mutableProgress.value = WatchStateSyncProgress()
        repository.clear()
        return false
    }

    /**
     * The first walk spans hundreds of pages and may be cut short by the app being killed, so it
     * picks up where it stopped instead of starting over. Entries deleted meanwhile shift the rest
     * onto earlier pages, so the resume overlaps a little rather than risking a gap.
     */
    private fun firstPageToRead(walkEverything: Boolean, cursor: WatchStateSyncCursor): Int =
        if (walkEverything) {
            (cursor.historyResumePage - RESUME_OVERLAP_PAGES).coerceAtLeast(1)
        } else {
            1
        }

    /**
     * Opens a new pass when the index is due to be reconciled against the server.
     *
     * Only between walks: bumping mid-walk would orphan the rows this pass has already stamped, and
     * the prune at the end would delete them.
     */
    private fun WatchStateSyncCursor.withReconciliationIfDue(now: Long): WatchStateSyncCursor {
        // A walk already under way rules it out; so does never having finished one.
        val isDue = fullHistoryWalkDone &&
            lastReconciledAt != null &&
            now - lastReconciledAt >= reconcileAfter.inWholeMilliseconds
        if (!isDue) return this
        return copy(
            generation = generation + 1,
            fullHistoryWalkDone = false,
            historyResumePage = 1,
        )
    }

    /**
     * What one history walk did: how far the bookmark moved, whether it ran out of history, and
     * whether that was a full pass — only a full one has seen enough to prune by.
     */
    private data class HistoryWalk(
        val cursor: WatchStateSyncCursor,
        val reachedTheEnd: Boolean,
        val completedFullWalk: Boolean = false,
    )

    private suspend fun syncHistory(
        generation: Long,
        observedAt: Long,
        cursor: WatchStateSyncCursor,
        seriesStillInProgress: Set<Int>,
    ): HistoryWalk {
        val knownUpTo = cursor.historyNewestSeen
        val walkEverything = !cursor.fullHistoryWalkDone
        var current = cursor
        var page = firstPageToRead(walkEverything, cursor)
        var pagesRead = 0
        val pending = mutableListOf<History>()
        var pagesPending = 0

        /**
         * Stores what has accumulated, with the bookmark that covers exactly those pages. Returns
         * whether the run still belongs to its session — the caller must stop if it does not.
         *
         * Rows and bookmark still travel together, for the reason they always did: a bookmark that
         * outlived its rows would tell the next run this stretch of history is already indexed. That
         * holds for a batch as much as for a page — a batch lost in full loses its cursor too, so the
         * next run simply reads those pages again.
         */
        suspend fun flush(): Boolean {
            if (pending.isEmpty()) return sessionSurvived(generation)
            repository.recordHistoryPage(pending.toList(), seriesStillInProgress, observedAt, current)
            pending.clear()
            pagesPending = 0
            return sessionSurvived(generation)
        }

        while (pagesRead < MAX_HISTORY_PAGES_PER_RUN) {
            // Whatever has been read so far is still true and its cursor still describes it, so the
            // remainder is written by the flush below rather than re-fetched by the next run.
            val response = api.getHistoryData(page).getOrNull() ?: break
            mutableProgress.value = WatchStateSyncProgress(
                isSyncing = true,
                currentPage = response.pagination.current,
                totalPages = response.pagination.total,
                totalHistoryItems = response.pagination.totalItems,
            )
            val outcome = current.advancedOver(response, walkEverything, knownUpTo)
            current = outcome.cursor
            pending += response.items
            pagesPending += 1

            // The session ending mid-walk voids the rows and the cursor alike, so nothing below may
            // run for it. `flush` is only reached when a write is actually due.
            val chunkIsFull = pagesPending >= HISTORY_PAGES_PER_CHUNK
            if ((chunkIsFull || outcome.walkIsOver) && !flush()) {
                return HistoryWalk(current, reachedTheEnd = false)
            }
            if (outcome.walkIsOver) {
                return HistoryWalk(
                    cursor = current,
                    reachedTheEnd = true,
                    completedFullWalk = walkEverything && outcome.serverExhausted,
                )
            }
            page = outcome.nextPage
            pagesRead++

            // Between chunks the walk stands down. OkHttp allows five requests in flight per host,
            // and a first walk is hundreds of pages long: run flat out it holds those slots for its
            // whole duration, in front of every request the screen the user is looking at needs.
            // Pausing costs the index nothing that is observed — nothing reads it mid-walk, and the
            // chunk just flushed is already durable — and it is what turns a burst that competes
            // with the UI into a trickle that does not.
            //
            // Skipped once the budget is spent: the loop below is about to end, and a pause with no
            // chunk on the other side of it is time spent waiting for nothing.
            if (chunkIsFull && pagesRead < MAX_HISTORY_PAGES_PER_RUN) holdBeforeNextChunk()
        }

        // Out of page budget for this run, or a page failed. Either way the next run resumes from
        // where this stopped, which is what the final batch records.
        flush()
        return HistoryWalk(current, reachedTheEnd = false)
    }

    /**
     * Holds until the next chunk may go out.
     *
     * Foreground is checked on both sides of the gap, for two different reasons. Before, so that a
     * walk left running does not carry on in the background, and so the gap is served on the way
     * back — keeping the resumed walk clear of the reload the return itself sets off. After, because
     * the app can leave *during* the gap, and a chunk that went out on the far side of it would be
     * exactly the background traffic the first check just prevented.
     */
    private suspend fun holdBeforeNextChunk() {
        awaitForeground()
        delay(pauseBetweenChunks)
        awaitForeground()
    }

    /** Where one page leaves the bookmark, and whether there is anything left to read. */
    private data class PageOutcome(
        val cursor: WatchStateSyncCursor,
        val walkIsOver: Boolean,
        val serverExhausted: Boolean,
        val nextPage: Int,
    )

    private fun WatchStateSyncCursor.advancedOver(
        response: PaginatedResponse<History>,
        walkEverything: Boolean,
        knownUpTo: Long,
    ): PageOutcome {
        // An incremental pass is done as soon as a page holds nothing newer than the last walk. An
        // empty page proves nothing — entries deleted from the history leave gaps — so it must not
        // be mistaken for having caught up.
        val caughtUp = !walkEverything &&
            response.items.isNotEmpty() &&
            response.items.all { it.lastSeenOrZero() <= knownUpTo }
        val serverExhausted = response.pagination.current >= response.pagination.total
        val nextPage = response.pagination.current + 1
        return PageOutcome(
            cursor = copy(
                historyNewestSeen = maxOf(
                    historyNewestSeen,
                    response.items.maxOfOrNull(History::lastSeenOrZero) ?: 0L,
                ),
                fullHistoryWalkDone = fullHistoryWalkDone || (walkEverything && serverExhausted),
                // Where the next run picks up. Reset once the full walk is behind us.
                historyResumePage = when {
                    !walkEverything -> historyResumePage
                    serverExhausted -> 1
                    else -> nextPage
                },
            ),
            walkIsOver = caughtUp || serverExhausted,
            serverExhausted = serverExhausted,
            nextPage = nextPage,
        )
    }

    private suspend fun fetch(request: suspend () -> Result<ApiResponseList<Item>>): List<Item>? =
        request().getOrNull()?.items

    private companion object {
        /**
         * How far one run will walk. A deep history is covered across several runs rather than in
         * one burst, so a cold start is never spent entirely on catching up.
         *
         * This is a ceiling on the whole run, not on how hard it pushes: the run is broken into
         * [HISTORY_PAGES_PER_CHUNK] chunks with a pause between them, so reaching this number takes
         * minutes of mostly idle time rather than hundreds of back-to-back requests.
         */
        const val MAX_HISTORY_PAGES_PER_RUN = 300

        /**
         * How many pages the walk reads back-to-back before it flushes and stands down.
         *
         * One number serves both jobs because both want the same boundary. Every write is a
         * transaction, and every transaction re-runs the query behind [WatchStateRepository.snapshot],
         * which rebuilds the whole id-to-state map — per page across a walk hundreds of pages long,
         * that rebuild costs more than the requests do. And the pause has to fall where the pages
         * already read are durable, so a chunk interrupted by the app closing is not re-read.
         */
        const val HISTORY_PAGES_PER_CHUNK = 15

        /** Pages re-read on resume, to absorb entries deleted since the walk stopped. */
        const val RESUME_OVERLAP_PAGES = 2
    }
}

private fun History.lastSeenOrZero(): Long = updated?.toLongOrNull() ?: 0L
