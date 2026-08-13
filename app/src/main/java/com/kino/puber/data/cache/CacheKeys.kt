package com.kino.puber.data.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The whole key space of the payload cache.
 *
 * Keys are built here and nowhere else. The store takes plain strings, so the only thing keeping two
 * namespaces from colliding — or a wipe by prefix from taking more than it meant to — is that every
 * key in the app comes from this object.
 */
object CacheKeys {

    const val HomePrefix = "home:"
    const val ItemPrefix = "item:"
    const val SimilarPrefix = "similar:"

    fun home(section: String): String = HomePrefix + section

    fun item(id: Int): String = ItemPrefix + id

    fun similar(id: Int): String = SimilarPrefix + id
}

/** How long each kind of payload is served without consulting the server. */
object CacheTtl {

    /** The row a finished episode makes wrong at once, so it is barely cached at all. */
    val ContinueWatching: Duration = 2.minutes

    /** Editorial rows. They move on the server's schedule, not the user's. */
    val HomeSection: Duration = 30.minutes

    val ItemDetails: Duration = 10.minutes

    val SimilarItems: Duration = 30.minutes
}
