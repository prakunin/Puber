package com.kino.puber.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WatchStateSyncDao {

    @Query("SELECT * FROM watch_state_sync WHERE id = :id")
    suspend fun get(id: Int = WatchStateSyncEntity.SINGLE_ROW_ID): WatchStateSyncEntity?

    @Upsert
    suspend fun upsert(entity: WatchStateSyncEntity)

    @Query("DELETE FROM watch_state_sync")
    suspend fun clear()
}
