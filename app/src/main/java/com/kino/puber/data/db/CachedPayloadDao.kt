package com.kino.puber.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface CachedPayloadDao {

    @Query("SELECT * FROM cached_payload WHERE key = :key")
    suspend fun read(key: String): CachedPayloadEntity?

    @Query("SELECT * FROM cached_payload WHERE key IN (:keys)")
    suspend fun readAll(keys: List<String>): List<CachedPayloadEntity>

    @Upsert
    suspend fun upsert(entity: CachedPayloadEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CachedPayloadEntity>)

    @Query("UPDATE cached_payload SET updated_at = :updatedAt WHERE key = :key")
    suspend fun touch(key: String, updatedAt: Long)

    @Query("DELETE FROM cached_payload WHERE key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM cached_payload WHERE key LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM cached_payload")
    suspend fun clear()
}
