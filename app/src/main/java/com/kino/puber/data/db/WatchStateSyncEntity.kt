package com.kino.puber.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How far the watch-state sync has already got.
 *
 * Kept in the same database as the rows it describes, and written in the same transaction, so the
 * bookmark can never claim to have read history whose rows were never stored — which is exactly
 * what a separate preferences file allowed, and what the "stamped but the table is empty" check
 * used to paper over.
 *
 * Exactly one row, so the bookmark cannot fork.
 */
@Entity(tableName = "watch_state_sync")
data class WatchStateSyncEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    /** `last_seen` of the newest history entry already folded into the index. */
    @ColumnInfo(name = "history_newest_seen") val historyNewestSeen: Long = 0L,
    @ColumnInfo(name = "full_history_walk_done") val fullHistoryWalkDone: Boolean = false,
    /**
     * Page the first full walk should resume from. The walk spans hundreds of requests and the app
     * can be backgrounded or killed part-way through; without this it would start over every launch.
     */
    @ColumnInfo(name = "history_resume_page") val historyResumePage: Int = 1,
    /** The pass currently being built. Every row written now is stamped with it. */
    @ColumnInfo(name = "generation") val generation: Long = FIRST_GENERATION,
    /** When the last full pass finished and pruned what it had not seen. */
    @ColumnInfo(name = "last_reconciled_at") val lastReconciledAt: Long? = null,
) {

    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
