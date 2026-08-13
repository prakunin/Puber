package com.kino.puber.data.repository

import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isFullyWatched
import com.kino.puber.data.api.models.isSeriesLike
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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    val snapshot: StateFlow<Map<Int, WatchState>> = dao.observeAll()
        .map { rows -> rows.associate { row -> row.itemId to row.toWatchState() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    private val mutableVersion = MutableStateFlow(0L)
    val version: StateFlow<Long> = mutableVersion.asStateFlow()

    /**
     * Emits once the index has settled after a change — a sync landing, or the user marking
     * something watched.
     *
     * Debounced because the first history walk writes a batch of rows per page for hundreds of
     * pages; reacting to each one would redo every open screen hundreds of times over.
     */
    val settledChanges: Flow<Long> = version
        .drop(1)
        .debounce(SETTLE_DELAY)
        .conflate()

    /**
     * Rows replaced by an unconfirmed optimistic mark, keyed by item id. A null value means there
     * was no row before the mark, which is why this is not a ConcurrentHashMap.
     */
    private val rowsBeforeLocalMark = Collections.synchronizedMap(mutableMapOf<Int, WatchStateEntity?>())

    /**
     * Completes once the flags inherited from a previous process have been cleared. A mark made
     * before that runs would be caught by the cleanup and lose the protection it exists for, so
     * [markLocally] waits here rather than assuming the startup coroutine won the race.
     */
    private val inheritedFlagsCleared = CompletableDeferred<Unit>()

    init {
        scope.launch {
            snapshot.collect { mutableVersion.value += 1 }
        }
        scope.launch {
            try {
                // Nothing in this process marked anything yet, so any pending flag still in the
                // table belongs to a process that died mid-toggle and can never be resolved.
                dao.clearAllPending()
            } finally {
                // A cleanup that failed must not leave every later toggle waiting on it.
                inheritedFlagsCleared.complete(Unit)
            }
        }
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

    /** Drops the pending flag once the server has acknowledged the toggle. */
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

    suspend fun saveSyncCursor(cursor: WatchStateSyncCursor) = syncDao.upsert(cursor.toEntity())

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
        saveSyncCursor(cursor)
    }

    suspend fun clear() = transaction.run {
        rowsBeforeLocalMark.clear()
        dao.clear()
        syncDao.clear()
    }

    private companion object {
        val SETTLE_DELAY = 2.seconds
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

private fun Item.toWatchStateOrNull(): WatchState? {
    // `total` is an episode count, not watch state: a catalogue item carrying it while omitting
    // watched/new must fall through to the index instead of resolving to a synthetic "not watched".
    if (watched == null && new == null && watching == null) return null

    return WatchState(
        itemId = id,
        isSeriesLike = type.isSeriesLike(),
        isFullyWatched = isFullyWatched(),
        watchedEpisodes = watched,
        totalEpisodes = total,
        progressTime = watching?.time,
        progressDuration = watching?.duration,
    )
}

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
)

private fun WatchStateSyncEntity.toCursor() = WatchStateSyncCursor(
    lastSyncAt = lastSyncAt,
    historyNewestSeen = historyNewestSeen,
    fullHistoryWalkDone = fullHistoryWalkDone,
    historyResumePage = historyResumePage,
    generation = generation,
    lastReconciledAt = lastReconciledAt,
)

private fun WatchStateSyncCursor.toEntity() = WatchStateSyncEntity(
    lastSyncAt = lastSyncAt,
    historyNewestSeen = historyNewestSeen,
    fullHistoryWalkDone = fullHistoryWalkDone,
    historyResumePage = historyResumePage,
    generation = generation,
    lastReconciledAt = lastReconciledAt,
)
