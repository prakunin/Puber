package com.kino.puber.data.db

import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Deletes the rows the content cache wrote before its `content:v1:` namespace existed, once, when
 * the database moves from version 3 to 4. The prefixes are literals on purpose: a later edit of
 * `CacheKeys` must not change what this migration deletes.
 */
class LegacyPayloadCleanup : AutoMigrationSpec {

    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        PREFIXES.forEach { prefix ->
            connection.execSQL("DELETE FROM cached_payload WHERE key LIKE '$prefix%'")
        }
    }

    private companion object {
        val PREFIXES = listOf("home:", "item:", "similar:", "section:", "watchlist:", "history:")
    }
}
