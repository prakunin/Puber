package com.kino.puber.data.repository

import android.os.SystemClock
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isFullyWatched
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.api.models.lengthOr
import com.kino.puber.data.api.models.movieLength
import com.kino.puber.data.api.models.moviePosition
import com.kino.puber.data.db.DatabaseTransaction
import com.kino.puber.data.db.FIRST_GENERATION
import com.kino.puber.data.db.WatchStateDao
import com.kino.puber.data.db.WatchStateSyncDao
import com.kino.puber.data.db.WatchStateSyncEntity
import com.kino.puber.data.db.WatchStateEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * Locally known watch state, keyed by item id.
 *
 * The catalogue endpoints carry no watch fields, so every screen that wants to show a watched mark
 * or hide finished titles reads them from here instead of from the [Item] it just received.
 *
 * [snapshot] is kept warm from the database so mapping stays synchronous; [version] ticks whenever
 * the contents change, which is what open lists observe to re-page themselves.
 */
class WatchStateRepository(
    private val dao: WatchStateDao,
    private val syncDao: WatchStateSyncDao,
    private val transaction: DatabaseTransaction,
    private val clock: () -> Long = System::currentTimeMillis,
    private val windowClock: () -> Long = SystemClock::elapsedRealtime,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val awaitStartupSettled: suspend () -> Unit = {},
) {

    val snapshot: StateFlow<Map<Int, WatchState>> = dao.observeAll()
        .map { rows -> rows.associate { row -> row.itemId to row.toWatchState() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Observable bookmark for screens that must not race a sync finishing between separate reads. */
    val syncCursorState: StateFlow<WatchStateSyncCursor> = syncDao.observe()
        .map { entity -> entity?.toCursor() ?: WatchStateSyncCursor() }
        .stateIn(scope, SharingStarted.Eagerly, WatchStateSyncCursor())

    private val mutableVersion = MutableStateFlow(0L)
    val version: StateFlow<Long> = mutableVersion.asStateFlow()

    /**
     * Emits once the index has settled after a change — a sync landing, or the user marking
     * something watched.
     *
     * Debounced because a write lands per batch of history pages; reacting to each one would redo
     * every open screen many times over. The debounce alone is not enough for that, though: it only
     * collapses writes closer together than [SETTLE_DELAY], and a walk deliberately spaces its
     * chunks much further apart than that — so each chunk would settle separately and cost a full
     * round of re-mapping and re-paging. What actually holds them together is the sync window; see
     * [beginSyncWindow].
     */
    val settledChanges: Flow<Long> = version
        .drop(1)
        .debounce(SETTLE_DELAY)
        .conflate()

    /**
     * Everything the sync window decides with, behind one lock.
     *
     * A walk is minutes long and writes a chunk at a time. Every one of those writes is real and
     * durable, but none of them is worth telling the screens about on its own: the index is only
     * half-rebuilt, and the next chunk is seconds away. So the ticks they cause are held and paid
     * out as a single one when the walk ends — completed, stopped, or cancelled alike.
     *
     * One lock rather than a field per question, because the decisions read one field and write
     * another: separately atomic, the collector can find the window open just as the walk closes it,
     * and then set a "held" flag nobody will ever look at again — losing the one signal the walk had
     * to give. Whether to publish is a single decision and is taken as one.
     */
    private val windowLock = Any()
    private var openWalkWindows = 0
    private var openCatchUpWindows = 0

    /** A change that landed while a walk's window was open; the walk's to pay out at its end. */
    private var walkChangeHeld = false

    /** A change that landed while a catch-up's window was open; the catch-up's to pay out at its close. */
    private var catchUpChangeHeld = false

    /**
     * Until when the next emission is a catch-up's, in [windowClock] milliseconds, a monotonic
     * clock so a wall-clock step cannot stretch or cut the window; [NO_DEADLINE] when none
     * is. Armed on every catch-up close, because the database may not have published its last write
     * yet and a walk may already be open. Before the deadline the next emission goes out whatever
     * the walk is doing.
     *
     * A deadline rather than a flag: a catch-up that wrote nothing never gets an emission, and a
     * flag would then wait for the walk's first changing chunk and announce it early.
     * [CATCH_UP_EMISSION_WINDOW_MS] is far above Room's invalidation latency (tens of milliseconds)
     * and below a walk's first chunk, which needs its watching lists and a history page over the
     * network first.
     */
    private var catchUpEmissionDeadline = NO_DEADLINE

    /**
     * Marks the user has made that the index has not shown yet: item id to the `isFullyWatched` the
     * write is expected to produce, or null where it is expected to leave no row at all.
     *
     * What is held is the expectation rather than a "something is pending" flag, because the flag
     * cannot tell one emission from another. The database publishes asynchronously, so an emission
     * already on its way when the user toggled carries the row from before it; a flag would be spent
     * on that one — announcing nothing new — and the emission that does carry the toggle would then
     * be held with the walk's own writes for the rest of a multi-minute pass. Comparing against what
     * the write should produce answers the question that actually matters: can a screen reading the
     * index right now see it?
     */
    private val marksAwaitingIndex = mutableMapOf<Int, Boolean?>()

    /**
     * Rows replaced by an unconfirmed optimistic mark, keyed by item id. A null value means there
     * was no row before the mark, which is why this is not a ConcurrentHashMap.
     */
    private val rowsBeforeLocalMark = Collections.synchronizedMap(mutableMapOf<Int, WatchStateEntity?>())

    /**
     * Completes once the flags inherited from a previous process have been cleared, which happens
     * after startup settles ([awaitStartupSettled]). A mark made before that runs would be caught
     * by the cleanup and lose the protection it exists for, so [markLocally] waits here rather than
     * assuming the startup coroutine won the race.
     */
    private val inheritedFlagsCleared = CompletableDeferred<Unit>()

    init {
        scope.launch {
            snapshot.collect(::onIndexChanged)
        }
        scope.launch {
            try {
                // Held back until startup settles so the write does not compete with the first
                // frames. Nothing in this process marked anything yet, so any pending flag still in
                // the table belongs to a process that died mid-toggle and can never be resolved.
                awaitStartupSettled()
                dao.clearAllPending()
            } finally {
                // A cleanup that failed must not leave every later toggle waiting on it.
                inheritedFlagsCleared.complete(Unit)
            }
        }
    }

    /**
     * Decides what one change to the index is worth announcing, and announces it.
     *
     * Driven by [snapshot] rather than by the write that caused it, which is the only order that
     * works: the database publishes asynchronously, so a tick sent from the write itself can reach
     * a screen that then re-reads [snapshot] and finds the old value still there.
     */
    private fun onIndexChanged(index: Map<Int, WatchState>) {
        val publish = synchronized(windowLock) {
            // Cleared as they land rather than counted off: whichever emission finally carries a
            // mark is the one that releases it, and an earlier one that does not carry it changes
            // nothing here.
            val aMarkLanded = marksAwaitingIndex.entries.removeAll { (itemId, expected) ->
                index[itemId]?.isFullyWatched == expected
            }
            if (aMarkLanded) catchUpEmissionDeadline = NO_DEADLINE
            when {
                // The user is waiting on this one, and this is the first moment the index actually
                // shows it — so it goes out whatever a walk happens to be doing.
                aMarkLanded -> true
                openCatchUpWindows > 0 -> {
                    catchUpChangeHeld = true
                    false
                }
                catchUpEmissionDeadline != NO_DEADLINE && windowClock() <= catchUpEmissionDeadline -> {
                    catchUpEmissionDeadline = NO_DEADLINE
                    true
                }
                openWalkWindows > 0 -> {
                    walkChangeHeld = true
                    false
                }
                else -> true
            }
        }
        if (publish) mutableVersion.update { it + 1 }
    }

    /**
     * Opens a window over a server sync, within which its own writes do not reach [settledChanges].
     * Must be paired with [endSyncWindow] of the same [kind].
     *
     * This is about the *server* rebuilding the index behind the user's back, which is why it is
     * scoped to the sync rather than applied to every write: what the user does to the index is
     * something they are waiting to see, and [expectUserMark] keeps that immediate even with a
     * walk in flight.
     *
     * Ownership is by [kind], not by nesting: a catch-up's window pays out its own change whether
     * it opened before or after the walk's.
     */
    fun beginSyncWindow(kind: SyncWindowKind) {
        synchronized(windowLock) {
            when (kind) {
                SyncWindowKind.WALK -> openWalkWindows += 1
                SyncWindowKind.CATCH_UP -> openCatchUpWindows += 1
            }
        }
    }

    /**
     * Closes the window and pays out the one tick it earned, if it wrote anything at all. A walk
     * pays out what landed while only its window was open. A catch-up pays out what landed while its
     * own was open — never the walk's held change — and, while a walk is still open, also the next
     * emission, so a catch-up inside a walk is not left waiting for the walk's end.
     *
     * Runs for a sync that was cancelled or gave up as much as for one that finished: the rows it
     * did write are durable either way, so the screens showing them are owed the signal regardless
     * of how it ended.
     */
    fun endSyncWindow(kind: SyncWindowKind) {
        val publish = synchronized(windowLock) {
            when (kind) {
                SyncWindowKind.WALK -> {
                    openWalkWindows -= 1
                    if (openWalkWindows > 0) {
                        false
                    } else {
                        catchUpEmissionDeadline = NO_DEADLINE
                        walkChangeHeld.also { walkChangeHeld = false }
                    }
                }
                SyncWindowKind.CATCH_UP -> {
                    openCatchUpWindows -= 1
                    if (openCatchUpWindows > 0) {
                        false
                    } else {
                        catchUpEmissionDeadline = windowClock() + CATCH_UP_EMISSION_WINDOW_MS
                        catchUpChangeHeld.also { catchUpChangeHeld = false }
                    }
                }
            }
        }
        if (publish) mutableVersion.update { it + 1 }
    }

    /**
     * Registers what a mark the user is making should leave in the index, so the emission that
     * carries it outruns any window a walk is holding open.
     *
     * Registered before the write rather than announced after it: the announcement still waits for
     * the database to say the row is really there, so nothing is ever told about a change it cannot
     * yet read. Without this a toggle made during a walk would be held with the walk's own writes
     * and stay invisible for minutes.
     *
     * @param expectedFullyWatched what the index should show for [itemId] once the write lands, or
     * null where the write is expected to leave no row.
     */
    private fun expectUserMark(itemId: Int, expectedFullyWatched: Boolean?) {
        synchronized(windowLock) { marksAwaitingIndex[itemId] = expectedFullyWatched }
    }

    fun get(itemId: Int): WatchState? = snapshot.value[itemId]

    /**
     * Watch state for an item, preferring what the item itself reports — the details and watching
     * endpoints do carry watch fields — and falling back to the local index for everything the
     * catalogue returns without them.
     */
    fun resolve(item: Item): WatchState? = item.toWatchStateOrNull() ?: get(item.id)

    fun isFullyWatched(item: Item): Boolean = resolve(item)?.isFullyWatched == true

    /**
     * When each item the index can date was last played, keyed by item id.
     *
     * Read from the database rather than from [snapshot], because this is not part of [WatchState]:
     * nothing draws a card with it, and putting it there would make every history write that moves a
     * timestamp — thousands of them during a walk — a change the snapshot publishes and every open
     * screen re-reads.
     *
     * Only the history knows these times, so an item is missing here until the walk has reached it,
     * and its stamp is as old as the last sync. A caller ordering by recency wants a fresher source
     * in front of this one for what was played since.
     */
    suspend fun lastWatchedAt(): Map<Int, Long> =
        dao.lastWatchedAt().associate { row -> row.itemId to row.historySeenAt }

    /**
     * Records whatever watch fields the given items happen to carry. Items without any (everything
     * the catalogue returns) are skipped rather than written as "not watched", so an opportunistic
     * write can never erase what a sync already learned.
     *
     * @param observedAt when this data was read from the server. A long sync writes minutes after
     * it fetched, and in between the user may have marked something watched — stamping the write
     * with its own clock would let the older data win.
     */
    suspend fun recordFromServer(items: List<Item>, observedAt: Long = clock()) {
        val generation = currentGeneration()
        val rows = items.mapNotNull { item -> item.toWatchStateOrNull()?.toEntity(observedAt, generation) }
        if (rows.isEmpty()) return
        dao.upsertAllFromServer(rows)
    }

    /**
     * Records items a "still watching" list named without describing them. `/watching/movies`
     * returns nothing but id/title/type/posters, so the only thing it proves is that the title is
     * started and not finished — which is enough to keep it out of a "hide watched" filter.
     */
    suspend fun recordInProgress(items: List<Item>, observedAt: Long = clock()) {
        val generation = currentGeneration()
        items.forEach { item ->
            dao.markInProgress(
                generation = generation,
                itemId = item.id,
                isSeriesLike = item.type.isSeriesLike(),
                updatedAt = observedAt,
            )
        }
    }

    /**
     * Records what the watch history says, which is the only source that knows about titles the
     * account has *finished* — the watching lists only carry what is still in progress.
     *
     * A movie is decided by its own playback position. A series cannot be: history knows which
     * episodes were played but not how many exist, so a series is called finished only when it has
     * left [seriesStillInProgress] *and* the episode this entry describes was itself played to the
     * end. That list is required rather than optional: history read without it can say nothing
     * about series at all, so the caller waits for it instead of walking half an answer.
     */
    suspend fun recordFromHistory(
        entries: List<History>,
        seriesStillInProgress: Set<Int>,
        observedAt: Long = clock(),
    ) {
        val generation = currentGeneration()
        val rows = entries
            .mapNotNull { entry -> entry.toHistoryRowOrNull(seriesStillInProgress, observedAt, generation) }
            .groupBy(WatchStateEntity::itemId)
            .map { (_, rows) -> rows.reduce(::mergeHistoryRows) }
        if (rows.isEmpty()) return
        dao.upsertAllFromHistory(rows)
    }

    /**
     * Applies the user's own watched toggle before the server has confirmed it. The row is marked
     * pending so a concurrent sync cannot roll it back.
     */
    suspend fun markLocally(itemId: Int, isSeriesLike: Boolean, isFullyWatched: Boolean) {
        inheritedFlagsCleared.await()
        expectUserMark(itemId, isFullyWatched)
        val existing = dao.get(itemId)
        // Kept so a rejected toggle can put back what was there instead of losing it.
        if (!rowsBeforeLocalMark.containsKey(itemId)) rowsBeforeLocalMark[itemId] = existing
        val totalEpisodes = existing?.totalEpisodes
        dao.upsert(
            listOf(
                WatchStateEntity(
                    itemId = itemId,
                    isSeriesLike = isSeriesLike,
                    isFullyWatched = isFullyWatched,
                    watchedEpisodes = when {
                        !isSeriesLike -> null
                        isFullyWatched -> totalEpisodes
                        else -> existing?.watchedEpisodes
                    },
                    totalEpisodes = totalEpisodes,
                    progressTime = if (isFullyWatched) null else existing?.progressTime,
                    progressDuration = existing?.progressDuration,
                    updatedAt = clock(),
                    isLocalPending = true,
                    generation = currentGeneration(),
                )
            )
        )
    }

    /**
     * Drops the pending flag once the server has acknowledged the toggle.
     *
     * Nothing to announce: the flag is not part of [WatchState], so the snapshot this produces is
     * equal to the one before it and never reaches a collector.
     */
    suspend fun confirmLocalMark(itemId: Int) {
        rowsBeforeLocalMark.remove(itemId)
        dao.clearPending(itemId)
    }

    /**
     * Puts back what the optimistic mark replaced. A rejected toggle must not cost the item its
     * known progress — deleting the row would hide it from the filter until the next full sync.
     */
    suspend fun revertLocalMark(itemId: Int) {
        val previous = rowsBeforeLocalMark.remove(itemId)
        expectUserMark(itemId, previous?.isFullyWatched)
        if (previous == null) dao.delete(itemId) else dao.upsert(listOf(previous))
    }

    /**
     * The pass rows written now belong to. Read per write rather than cached: a reconciliation can
     * open a new pass between two writes, and anything after that point belongs to the new one.
     */
    private suspend fun currentGeneration(): Long = syncDao.get()?.generation ?: FIRST_GENERATION

    /**
     * Drops everything the latest full pass never restamped.
     *
     * This is the only thing that can remove a row. Nothing in the incremental sources ever says a
     * title is gone — a cleared history or a mark undone elsewhere simply stops being mentioned —
     * so without this the index only ever grows, and a stale "watched" hides a title for good.
     */
    suspend fun pruneStaleRows(generation: Long) = dao.pruneOlderThan(generation)

    /** How far the sync has already got. Defaults describe an index that has never been filled. */
    suspend fun syncCursor(): WatchStateSyncCursor = syncDao.get()?.toCursor() ?: WatchStateSyncCursor()

    suspend fun saveSyncCursor(cursor: WatchStateSyncCursor) = transaction.run { writeCursor(cursor) }

    /**
     * The mark only ever rises: a walk saves the cursor it read at its start, and a catch-up may
     * have raised the mark since, so storing that cursor as is would lower it.
     */
    private suspend fun writeCursor(cursor: WatchStateSyncCursor) {
        val stored = syncDao.get()?.historyNewestSeen ?: 0L
        syncDao.upsert(cursor.copy(historyNewestSeen = maxOf(stored, cursor.historyNewestSeen)).toEntity())
    }

    /**
     * Stores one page of history together with the bookmark that says it was read.
     *
     * Both in one transaction: a bookmark that survived a write which did not would tell the next
     * run this history is already indexed, and those entries would never be read again.
     */
    suspend fun recordHistoryPage(
        entries: List<History>,
        seriesStillInProgress: Set<Int>,
        observedAt: Long,
        cursor: WatchStateSyncCursor,
    ) = transaction.run {
        recordFromHistory(entries, seriesStillInProgress, observedAt)
        writeCursor(cursor)
    }

    /**
     * Stores the entries a catch-up read past the mark and raises the mark to the newest of them,
     * in one transaction, for the reason [recordHistoryPage] does. Touches nothing else of the
     * cursor, so a walk parked between chunks keeps its bookmark.
     */
    suspend fun recordCatchUpPage(
        entries: List<History>,
        seriesStillInProgress: Set<Int>,
        observedAt: Long,
        newestSeen: Long,
    ) = transaction.run {
        recordFromHistory(entries, seriesStillInProgress, observedAt)
        syncDao.raiseHistoryNewestSeen(newestSeen)
    }

    suspend fun clear() = transaction.run {
        rowsBeforeLocalMark.clear()
        dao.clear()
        syncDao.clear()
    }

    private companion object {
        val SETTLE_DELAY = 2.seconds
        const val CATCH_UP_EMISSION_WINDOW_MS = 1_000L
        const val NO_DEADLINE = Long.MIN_VALUE
    }
}

data class WatchState(
    val itemId: Int,
    val isSeriesLike: Boolean,
    val isFullyWatched: Boolean,
    val watchedEpisodes: Int? = null,
    val totalEpisodes: Int? = null,
    val progressTime: Int? = null,
    val progressDuration: Int? = null,
) {

    /**
     * How far through the item the account is, or null when it is untouched, finished, or the
     * source never reported enough to tell.
     */
    val progressPercent: Float?
        get() {
            if (isFullyWatched) return null
            val progress = if (isSeriesLike) {
                val total = totalEpisodes?.takeIf { it > 0 } ?: return null
                val watched = watchedEpisodes?.takeIf { it > 0 } ?: return null
                watched.toFloat() / total.toFloat()
            } else {
                val duration = progressDuration?.takeIf { it > 0 } ?: return null
                val time = progressTime ?: return null
                time.toFloat() / duration.toFloat()
            }
            return progress.takeIf { it > 0f && it < 1f }
        }
}

/**
 * Several history rows can belong to one item — one per episode, or one per replay. The most recent
 * one decides, for a series as much as for a movie: an attempt abandoned years ago must not veto a
 * later complete viewing. The walk crosses pages newest-first and the database resolves those the
 * same way, so any other rule here would disagree with itself depending on where a page happened
 * to break.
 */
private fun mergeHistoryRows(left: WatchStateEntity, right: WatchStateEntity): WatchStateEntity =
    if (left.historySeenAt >= right.historySeenAt) left else right

/**
 * @param seriesStillInProgress ids the watching list reports as unfinished.
 */
private fun History.toHistoryRowOrNull(
    seriesStillInProgress: Set<Int>,
    now: Long,
    generation: Long,
): WatchStateEntity? {
    val media = video ?: return null
    val isSeriesLike = item.type.isSeriesLike()

    val episodeFinished = WatchCompletionPolicy.isFinished(media.watching?.time, media.watching?.duration)
    return WatchStateEntity(
        itemId = item.id,
        isSeriesLike = isSeriesLike,
        // Leaving the in-progress list is what suggests a series is done, but on its own it is only
        // an absence. An episode the account stopped part-way through is direct evidence to the
        // contrary, and outranks it.
        isFullyWatched = episodeFinished &&
            (!isSeriesLike || item.id !in seriesStillInProgress),
        // Episode-level positions say nothing about how far through a series the account is, and
        // the episode counters that do come from the watching list.
        progressTime = media.watching?.time?.takeIf { !isSeriesLike },
        progressDuration = media.watching?.duration?.takeIf { !isSeriesLike && it > 0 },
        updatedAt = now,
        historySeenAt = updated?.toLongOrNull() ?: 0L,
        generation = generation,
    )
}

private fun WatchState.toEntity(now: Long, generation: Long) = WatchStateEntity(
    itemId = itemId,
    isSeriesLike = isSeriesLike,
    isFullyWatched = isFullyWatched,
    watchedEpisodes = watchedEpisodes,
    totalEpisodes = totalEpisodes,
    progressTime = progressTime,
    progressDuration = progressDuration,
    updatedAt = now,
    generation = generation,
)

private fun WatchStateEntity.toWatchState() = WatchState(
    itemId = itemId,
    isSeriesLike = isSeriesLike,
    isFullyWatched = isFullyWatched,
    watchedEpisodes = watchedEpisodes,
    totalEpisodes = totalEpisodes,
    progressTime = progressTime,
    progressDuration = progressDuration,
)

private fun Item.firstVideoCarriesWatchFields(): Boolean {
    val firstVideo = videos?.firstOrNull()
    return firstVideo?.watched != null || firstVideo?.watching != null
}

internal fun Item.toWatchStateOrNull(): WatchState? {
    val seriesLike = type.isSeriesLike()
    // `total` is an episode count, not watch state: a catalogue item carrying it while omitting
    // watched/new must fall through to the index instead of resolving to a synthetic "not watched".
    val itemCarriesWatchFields = watched != null || new != null || watching != null
    if (!itemCarriesWatchFields && (seriesLike || !firstVideoCarriesWatchFields())) return null

    val moviePosition = moviePosition()
    return WatchState(
        itemId = id,
        isSeriesLike = seriesLike,
        isFullyWatched = isFullyWatched(),
        watchedEpisodes = watched,
        totalEpisodes = total,
        progressTime = if (seriesLike) watching?.time else moviePosition?.time,
        progressDuration = if (seriesLike) watching?.duration else moviePosition?.lengthOr(movieLength()),
    )
}

/** Which sync a window belongs to; each pays out its own change. */
enum class SyncWindowKind { WALK, CATCH_UP }

/**
 * How far the watch-state sync has got, as the interactor works with it.
 *
 * A value type rather than a set of mutable properties: a run reads it once, carries it through the
 * walk, and stores it alongside the rows it belongs to.
 */
data class WatchStateSyncCursor(
    val lastSyncAt: Long? = null,
    val historyNewestSeen: Long = 0L,
    val fullHistoryWalkDone: Boolean = false,
    val historyResumePage: Int = 1,
    /** The pass being built. Bumped when a reconciliation starts; rows are stamped with it. */
    val generation: Long = FIRST_GENERATION,
    /** When the last full pass finished and pruned what it had not seen. */
    val lastReconciledAt: Long? = null,
    /** How many entries the account's history held when it was last read. */
    val historyTotalItems: Int? = null,
)

private fun WatchStateSyncEntity.toCursor() = WatchStateSyncCursor(
    lastSyncAt = lastSyncAt,
    historyNewestSeen = historyNewestSeen,
    fullHistoryWalkDone = fullHistoryWalkDone,
    historyResumePage = historyResumePage,
    generation = generation,
    lastReconciledAt = lastReconciledAt,
    historyTotalItems = historyTotalItems,
)

private fun WatchStateSyncCursor.toEntity() = WatchStateSyncEntity(
    lastSyncAt = lastSyncAt,
    historyNewestSeen = historyNewestSeen,
    fullHistoryWalkDone = fullHistoryWalkDone,
    historyResumePage = historyResumePage,
    generation = generation,
    lastReconciledAt = lastReconciledAt,
    historyTotalItems = historyTotalItems,
)
