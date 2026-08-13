package com.kino.puber.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Two rules hold for every hand-written statement here.
 *
 * The insert half must name every NOT NULL column: the defaults on [WatchStateEntity] are Kotlin
 * defaults, not SQL ones, so a column left out of an INSERT fails the constraint rather than
 * falling back to zero.
 *
 * The update half only fires when the incoming data was observed no earlier than what the row
 * already holds — `updated_at` is when the source was *read*, not when the row was written. Without
 * that, a sync that fetched its data before the user's toggle would still land after it and undo a
 * mark the server had already confirmed.
 */
@Dao
abstract class WatchStateDao {

    @Query("SELECT * FROM watch_state")
    abstract fun observeAll(): Flow<List<WatchStateEntity>>

    @Query("SELECT * FROM watch_state WHERE item_id = :itemId")
    abstract suspend fun get(itemId: Int): WatchStateEntity?

    @Query("SELECT COUNT(*) FROM watch_state")
    abstract suspend fun count(): Int

    @Upsert
    abstract suspend fun upsert(entities: List<WatchStateEntity>)

    /**
     * Writes a batch of rows a sync produced. Runs as one transaction so observers see a single
     * change rather than one per row, and leaves rows with an unconfirmed local toggle alone.
     */
    @Transaction
    open suspend fun upsertAllFromServer(rows: List<WatchStateEntity>) {
        rows.forEach { row ->
            upsertFromServer(
                generation = row.generation,
                itemId = row.itemId,
                isSeriesLike = row.isSeriesLike,
                isFullyWatched = row.isFullyWatched,
                watchedEpisodes = row.watchedEpisodes,
                totalEpisodes = row.totalEpisodes,
                progressTime = row.progressTime,
                progressDuration = row.progressDuration,
                updatedAt = row.updatedAt,
            )
        }
    }

    /**
     * Writes rows read from the history. The walk goes newest-first and spans app launches, so a
     * row is only replaced by an entry at least as recent as the one already stored.
     */
    @Transaction
    open suspend fun upsertAllFromHistory(rows: List<WatchStateEntity>) {
        rows.forEach { row ->
            upsertFromHistory(
                generation = row.generation,
                itemId = row.itemId,
                isSeriesLike = row.isSeriesLike,
                isFullyWatched = row.isFullyWatched,
                progressTime = row.progressTime,
                progressDuration = row.progressDuration,
                updatedAt = row.updatedAt,
                historySeenAt = row.historySeenAt,
            )
        }
    }

    @Query(
        """
        INSERT INTO watch_state (
            item_id, is_series_like, is_fully_watched, progress_time, progress_duration,
            updated_at, history_seen_at, is_local_pending, generation
        )
        VALUES (
            :itemId, :isSeriesLike, :isFullyWatched, :progressTime, :progressDuration,
            :updatedAt, :historySeenAt, 0, :generation
        )
        ON CONFLICT(item_id) DO UPDATE SET
            is_fully_watched = excluded.is_fully_watched,
            progress_time = excluded.progress_time,
            progress_duration = excluded.progress_duration,
            updated_at = excluded.updated_at,
            history_seen_at = excluded.history_seen_at,
            generation = excluded.generation
        WHERE watch_state.is_local_pending = 0
          AND excluded.history_seen_at >= watch_state.history_seen_at
          AND excluded.updated_at >= watch_state.updated_at
        """
    )
    @Suppress("LongParameterList")
    protected abstract suspend fun upsertFromHistory(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        isFullyWatched: Boolean,
        progressTime: Int?,
        progressDuration: Int?,
        updatedAt: Long,
        historySeenAt: Long,
    )

    @Query(
        """
        INSERT INTO watch_state (
            item_id, is_series_like, is_fully_watched, watched_episodes, total_episodes,
            progress_time, progress_duration, updated_at, history_seen_at, is_local_pending,
            generation
        )
        VALUES (
            :itemId, :isSeriesLike, :isFullyWatched, :watchedEpisodes, :totalEpisodes,
            :progressTime, :progressDuration, :updatedAt, 0, 0, :generation
        )
        ON CONFLICT(item_id) DO UPDATE SET
            is_series_like = excluded.is_series_like,
            is_fully_watched = excluded.is_fully_watched,
            watched_episodes = excluded.watched_episodes,
            total_episodes = excluded.total_episodes,
            progress_time = excluded.progress_time,
            progress_duration = excluded.progress_duration,
            updated_at = excluded.updated_at,
            generation = excluded.generation
        WHERE watch_state.is_local_pending = 0
          AND excluded.updated_at >= watch_state.updated_at
        """
    )
    @Suppress("LongParameterList")
    protected abstract suspend fun upsertFromServer(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        isFullyWatched: Boolean,
        watchedEpisodes: Int?,
        totalEpisodes: Int?,
        progressTime: Int?,
        progressDuration: Int?,
        updatedAt: Long,
    )

    /**
     * Marks an item as started-but-not-finished without touching whatever progress numbers are
     * already stored. Used for sources that only report "this is in progress" and nothing else.
     */
    @Query(
        """
        INSERT INTO watch_state (
            item_id, is_series_like, is_fully_watched, updated_at, history_seen_at,
            is_local_pending, generation
        )
        VALUES (:itemId, :isSeriesLike, 0, :updatedAt, 0, 0, :generation)
        ON CONFLICT(item_id) DO UPDATE SET
            is_fully_watched = 0,
            updated_at = excluded.updated_at,
            generation = excluded.generation
        WHERE watch_state.is_local_pending = 0
          AND excluded.updated_at >= watch_state.updated_at
        """
    )
    abstract suspend fun markInProgress(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        updatedAt: Long,
    )

    @Query("UPDATE watch_state SET is_local_pending = 0 WHERE item_id = :itemId")
    abstract suspend fun clearPending(itemId: Int)

    /**
     * Clears every pending flag. The row a flag protects can only be rolled back from memory, so a
     * flag that outlived the process is orphaned — and a row no sync may touch would stay wrong for
     * good.
     */
    @Query("UPDATE watch_state SET is_local_pending = 0 WHERE is_local_pending = 1")
    abstract suspend fun clearAllPending()

    @Query("DELETE FROM watch_state WHERE item_id = :itemId")
    abstract suspend fun delete(itemId: Int)

    /**
     * Drops what the latest full pass never restamped.
     *
     * A pending row is exempt: it holds a mark the user just made and the server has not confirmed
     * yet, so no pass could have seen it.
     */
    @Query("DELETE FROM watch_state WHERE generation < :generation AND is_local_pending = 0")
    abstract suspend fun pruneOlderThan(generation: Long)

    @Query("DELETE FROM watch_state")
    abstract suspend fun clear()
}
