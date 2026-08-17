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
    const val SectionPrefix = "section:"
    const val WatchlistPrefix = "watchlist:"
    const val HistoryPrefix = "history:"

    fun home(section: String): String = HomePrefix + section

    fun item(id: Int): String = ItemPrefix + id

    fun similar(id: Int): String = SimilarPrefix + id

    fun section(id: String): String = SectionPrefix + id

    fun watchlist(): String = WatchlistPrefix + "subscribed"

    fun historyPage(page: Int): String = HistoryPrefix + page
}

/** How long each kind of payload is served without consulting the server. */
object CacheTtl {

    /** The row a finished episode makes wrong at once, so it is barely cached at all. */
    val ContinueWatching: Duration = 2.minutes

    /** Editorial rows. They move on the server's schedule, not the user's. */
    val HomeSection: Duration = 30.minutes

    val ItemDetails: Duration = 10.minutes

    val SimilarItems: Duration = 30.minutes

    /** Catalogue rows move on the server's schedule, like the editorial rows on home. */
    val CatalogueSection: Duration = 30.minutes

    /** Both of these are rewritten by the user's own playback, so they revalidate quickly. */
    val Watchlist: Duration = 2.minutes
    val HistoryPage: Duration = 2.minutes
}
