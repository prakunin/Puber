package com.kino.puber.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** One row of [WatchStateDao.lastWatchedAt]: an item and the `last_seen` of its newest history entry. */
data class ItemLastWatched(
    @ColumnInfo(name = "item_id") val itemId: Int,
    @ColumnInfo(name = "history_seen_at") val historySeenAt: Long,
)

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

    /**
     * When each item was last played, for the items the history walk has actually dated. Rows the
     * walk never reached carry a zero and are left out rather than returned as "never watched".
     */
    @Query("SELECT item_id, history_seen_at FROM watch_state WHERE history_seen_at > 0")
    abstract suspend fun lastWatchedAt(): List<ItemLastWatched>

    @Upsert
    abstract suspend fun upsert(entities: List<WatchStateEntity>)

    /**
     * Uses SQLite's legacy conflict syntax, which is supported by the older SQLite bundled with
     * Fire OS. The newer `ON CONFLICT DO UPDATE` form is not available on those devices.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(entity: WatchStateEntity): Long

    /**
     * Writes a batch of rows a sync produced. Runs as one transaction so observers see a single
     * change rather than one per row, and leaves rows with an unconfirmed local toggle alone.
     */
    @Transaction
    open suspend fun upsertAllFromServer(rows: List<WatchStateEntity>) {
        rows.forEach { row ->
            if (insertIfAbsent(row) == INSERT_FAILED) {
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
    }

    /**
     * Writes rows read from the history. The walk goes newest-first and spans app launches, so a
     * row is only replaced by an entry at least as recent as the one already stored.
     */
    @Transaction
    open suspend fun upsertAllFromHistory(rows: List<WatchStateEntity>) {
        rows.forEach { row ->
            if (insertIfAbsent(row) == INSERT_FAILED) {
                upsertFromHistory(
                    generation = row.generation,
                    itemId = row.itemId,
                    isFullyWatched = row.isFullyWatched,
                    progressTime = row.progressTime,
                    progressDuration = row.progressDuration,
                    updatedAt = row.updatedAt,
                    historySeenAt = row.historySeenAt,
                )
            }
        }
    }

    @Query(
        """
        UPDATE watch_state SET
            is_fully_watched = :isFullyWatched,
            progress_time = :progressTime,
            progress_duration = :progressDuration,
            updated_at = :updatedAt,
            history_seen_at = :historySeenAt,
            generation = :generation
        WHERE item_id = :itemId
          AND is_local_pending = 0
          AND :historySeenAt >= history_seen_at
          AND :updatedAt >= updated_at
        """
    )
    @Suppress("LongParameterList")
    protected abstract suspend fun upsertFromHistory(
        generation: Long,
        itemId: Int,
        isFullyWatched: Boolean,
        progressTime: Int?,
        progressDuration: Int?,
        updatedAt: Long,
        historySeenAt: Long,
    )

    @Query(
        """
        UPDATE watch_state SET
            is_series_like = :isSeriesLike,
            is_fully_watched = :isFullyWatched,
            watched_episodes = :watchedEpisodes,
            total_episodes = :totalEpisodes,
            progress_time = :progressTime,
            progress_duration = :progressDuration,
            updated_at = :updatedAt,
            generation = :generation
        WHERE item_id = :itemId
          AND is_local_pending = 0
          AND :updatedAt >= updated_at
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
    @Transaction
    open suspend fun markInProgress(
        generation: Long,
        itemId: Int,
        isSeriesLike: Boolean,
        updatedAt: Long,
    ) {
        val inserted = insertIfAbsent(
            WatchStateEntity(
                itemId = itemId,
                isSeriesLike = isSeriesLike,
                isFullyWatched = false,
                updatedAt = updatedAt,
                generation = generation,
            )
        )
        if (inserted == INSERT_FAILED) {
            updateInProgress(generation, itemId, updatedAt)
        }
    }

    @Query(
        """
        UPDATE watch_state SET
            is_fully_watched = 0,
            updated_at = :updatedAt,
            generation = :generation
        WHERE item_id = :itemId
          AND is_local_pending = 0
          AND :updatedAt >= updated_at
        """
    )
    protected abstract suspend fun updateInProgress(
        generation: Long,
        itemId: Int,
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

    private companion object {
        const val INSERT_FAILED = -1L
    }
}
