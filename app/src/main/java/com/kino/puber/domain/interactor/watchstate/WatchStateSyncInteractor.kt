package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.ApiResponseList
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.repository.WatchStateSyncCursor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
) {

    private val mutex = Mutex()

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
    suspend fun syncIfStale(force: Boolean = false): Boolean = mutex.withLock {
        val now = clock()
        var cursor = repository.syncCursor()
        if (!force && !isRunDue(now, cursor)) return false
        lastRunAt = now

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
        // The serials list is what the walk needs to tell a finished series from one still going,
        // so without it there is no walk at all. Reading the history anyway would index only the
        // movies in it, and a pass that skipped every series may neither end the full walk nor
        // prune by what it did not see — it would spend hundreds of requests on progress it is not
        // allowed to record. The history waits for a run that has the list.
        val walk = series?.let { inProgress ->
            syncHistory(
                generation = generation,
                observedAt = observedAt,
                cursor = cursor,
                seriesStillInProgress = inProgress.map(Item::id).toSet(),
            )
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
        // lists so their rows carry this pass too.
        if (walk?.completedFullWalk == true) {
            repository.pruneStaleRows(cursor.generation)
            cursor = cursor.copy(lastReconciledAt = now)
        }

        // Only a complete sync earns the stamp. Half an index must not suppress retries for an
        // hour. A walk that never ran (no serials list) fails this the same way a failed one does.
        val everySourceAnswered = movies != null && walk != null && walk.reachedTheEnd
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
     * check to see, which is what [minTimeBetweenRuns] covers: without it the ON_RESUME following
     * the main screen's first composition starts the next walk the instant the first one gives up
     * its page budget, and a cold start spends hundreds of back-to-back requests rather than the
     * single run's worth that budget exists to allow.
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

        while (pagesRead < MAX_HISTORY_PAGES_PER_RUN) {
            val response = api.getHistoryData(page).getOrNull()
                ?: return HistoryWalk(current, reachedTheEnd = false)
            val outcome = current.advancedOver(response, walkEverything, knownUpTo)
            current = outcome.cursor
            // Rows and bookmark together: a bookmark that outlived its rows would tell the next run
            // this stretch of history is already indexed, and those entries would never be re-read.
            repository.recordHistoryPage(response.items, seriesStillInProgress, observedAt, current)
            // The session ending mid-walk voids both, so nothing below may run for it.
            if (!sessionSurvived(generation)) break

            if (outcome.walkIsOver) {
                return HistoryWalk(
                    cursor = current,
                    reachedTheEnd = true,
                    completedFullWalk = walkEverything && outcome.serverExhausted,
                )
            }
            page = outcome.nextPage
            pagesRead++
        }

        // Out of page budget for this run, or the session ended under it. Either way the next run
        // resumes from where this stopped.
        return HistoryWalk(current, reachedTheEnd = false)
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
         */
        const val MAX_HISTORY_PAGES_PER_RUN = 300

        /** Pages re-read on resume, to absorb entries deleted since the walk stopped. */
        const val RESUME_OVERLAP_PAGES = 2
    }
}

private fun History.lastSeenOrZero(): Long = updated?.toLongOrNull() ?: 0L
