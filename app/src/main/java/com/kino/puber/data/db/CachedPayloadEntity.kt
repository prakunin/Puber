package com.kino.puber.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * One record owned by the content cache, encoded as JSON.
 *
 * The table is a convenience copy of what the server would return, held so a screen can draw before
 * the network answers. Nothing here is authoritative and nothing here is user data the app created:
 * Lists are normalized into ordered IDs while item data is stored once under its ID. Nothing here
 * is authoritative: it is dropped wholesale on logout, on a domain switch, and on any schema
 * change.
 *
 * [updatedAt] is when the payload was *read from the server*, in epoch milliseconds. Freshness is
 * judged against it, and marking an entry stale is done by moving it backwards rather than by
 * deleting the row — a readable stale payload is worth more than a spinner.
 */
@Entity(tableName = "cached_payload")
data class CachedPayloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "payload") val payload: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
