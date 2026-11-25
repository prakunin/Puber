package com.kino.puber.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface CachedPayloadDao {

    /**
     * Rows over [maxBytes] of payload are not selected: the cursor window is about 2 MiB, and one row
     * past it fails the whole query. The size is counted in bytes, as the store counts it on write.
     */
    @Query("SELECT * FROM cached_payload WHERE key = :key AND length(CAST(payload AS BLOB)) <= :maxBytes")
    suspend fun read(key: String, maxBytes: Int): CachedPayloadEntity?

    @Query("SELECT * FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) <= :maxBytes")
    suspend fun readAll(keys: List<String>, maxBytes: Int): List<CachedPayloadEntity>

    @Query("SELECT key FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) > :maxBytes")
    suspend fun oversizedKeys(keys: List<String>, maxBytes: Int): List<String>

    @Query("DELETE FROM cached_payload WHERE key IN (:keys) AND length(CAST(payload AS BLOB)) > :maxBytes")
    suspend fun deleteOversized(keys: List<String>, maxBytes: Int)

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

    @Query("DELETE FROM cached_payload WHERE updated_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query(
        "DELETE FROM cached_payload WHERE key IN " +
            "(SELECT key FROM cached_payload ORDER BY updated_at DESC LIMIT -1 OFFSET :keepNewest)",
    )
    suspend fun deleteBeyondNewest(keepNewest: Int)

    @Query("DELETE FROM cached_payload")
    suspend fun clear()
}
