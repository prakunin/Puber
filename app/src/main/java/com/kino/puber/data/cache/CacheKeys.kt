package com.kino.puber.data.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Logical query keys used by the content cache.
 *
 * The repository adds its private storage namespace; interactors only describe which server query
 * they are caching and never construct persistent-store keys themselves.
 */
object CacheKeys {

    const val HomePrefix = "home:"
    const val ItemPrefix = "item:"
    const val SimilarPrefix = "similar:"
    const val SectionPrefix = "section:"
    const val WatchlistPrefix = "watchlist:"
    const val HistoryPrefix = "history:"
    const val CollectionsPrefix = "collections:"
    const val GenresPrefix = "genres:"
    const val BookmarkFolders = "bookmark-folders"

    fun home(section: String): String = HomePrefix + section

    fun item(id: Int): String = ItemPrefix + id

    fun similar(id: Int): String = SimilarPrefix + id

    fun section(id: String): String = SectionPrefix + id

    fun watchlist(): String = WatchlistPrefix + "subscribed"

    fun historyPage(page: Int): String = HistoryPrefix + page

    fun collectionsPage(page: Int): String = CollectionsPrefix + page

    fun genres(type: String?): String = GenresPrefix + (type ?: "all")
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

    val Collections: Duration = 3.minutes

    val Genres: Duration = 5.minutes

    /** Long enough to collapse simultaneous Home rows, short enough to see folders made elsewhere. */
    val BookmarkFolders: Duration = 20.seconds
}
