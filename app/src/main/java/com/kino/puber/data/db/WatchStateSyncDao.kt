package com.kino.puber.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchStateSyncDao {

    @Query("SELECT * FROM watch_state_sync WHERE id = :id")
    fun observe(id: Int = WatchStateSyncEntity.SINGLE_ROW_ID): Flow<WatchStateSyncEntity?>

    @Query("SELECT * FROM watch_state_sync WHERE id = :id")
    suspend fun get(id: Int = WatchStateSyncEntity.SINGLE_ROW_ID): WatchStateSyncEntity?

    @Upsert
    suspend fun upsert(entity: WatchStateSyncEntity)

    @Query("UPDATE watch_state_sync SET history_newest_seen = :seen WHERE id = :id AND history_newest_seen < :seen")
    suspend fun raiseHistoryNewestSeen(seen: Long, id: Int = WatchStateSyncEntity.SINGLE_ROW_ID)

    @Query("DELETE FROM watch_state_sync")
    suspend fun clear()
}
