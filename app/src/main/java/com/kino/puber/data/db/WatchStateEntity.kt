package com.kino.puber.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally known watch state for one catalogue item.
 *
 * KinoPub's list endpoints (`/items`, `/items/{shortcut}`) return no watch fields at all, so the
 * catalogue cannot tell a finished series from an untouched one on its own. This table is the
 * app's own index, filled from the watching endpoints, from anything the user opens, and from the
 * user's own watched toggles.
 *
 * [watchedEpisodes]/[totalEpisodes] describe series-like items; [progressTime]/[progressDuration]
 * describe a movie's playback position. Either pair may be absent when the source did not report
 * it, which is why [isFullyWatched] is stored rather than derived on read.
 */
@Entity(tableName = "watch_state")
data class WatchStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "item_id") val itemId: Int,
    @ColumnInfo(name = "is_series_like") val isSeriesLike: Boolean,
    @ColumnInfo(name = "is_fully_watched") val isFullyWatched: Boolean,
    @ColumnInfo(name = "watched_episodes") val watchedEpisodes: Int? = null,
    @ColumnInfo(name = "total_episodes") val totalEpisodes: Int? = null,
    @ColumnInfo(name = "progress_time") val progressTime: Int? = null,
    @ColumnInfo(name = "progress_duration") val progressDuration: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /**
     * `last_seen` of the history entry this row came from, or 0 when it came from elsewhere.
     * History is walked newest-first and across several app launches, so without this an older
     * entry read later would overwrite a newer one.
     */
    @ColumnInfo(name = "history_seen_at") val historySeenAt: Long = 0L,
    /**
     * True between the moment the user toggles the watched mark and the moment the server confirms
     * it. A pending row wins over anything a sync brings in, so an optimistic toggle is not undone
     * by a sync that started before it.
     */
    @ColumnInfo(name = "is_local_pending") val isLocalPending: Boolean = false,
    /**
     * The reconciliation pass this row was last confirmed by.
     *
     * The index is a derived copy of server state, and nothing in the incremental sources ever says
     * "this is gone" — a cleared history or a title unmarked on another device simply stops being
     * mentioned. So a full pass restamps everything it still finds, and what it never restamped is
     * what the server no longer has.
     */
    @ColumnInfo(name = "generation") val generation: Long = FIRST_GENERATION,
)

/** Generation of the first pass. Nothing is older, so the first prune has nothing to remove. */
const val FIRST_GENERATION = 1L
