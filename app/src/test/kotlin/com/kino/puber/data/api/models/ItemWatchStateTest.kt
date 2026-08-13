package com.kino.puber.data.api.models

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val WATCHED_STATUS = 1

class ItemWatchStateTest {

    @Test
    fun movie_isFinishedAsSoonAsItHasBeenWatched() {
        assertTrue(movie(watched = 1).isFullyWatched())
        assertFalse(movie(watched = 0).isFullyWatched())
        assertFalse(movie(watched = null).isFullyWatched())
    }

    @Test
    fun movie_fallsBackToTheWatchingStatusWhenNoFlagIsSent() {
        // Endpoints differ in which of the two they fill in. Reading only `watched` calls a
        // finished movie unwatched, and that verdict overwrites the index row the history built.
        assertTrue(movie(watched = null, status = WATCHED_STATUS).isFullyWatched())
        assertFalse(movie(watched = null, status = 0).isFullyWatched())
    }

    @Test
    fun series_isDecidedByTheNumberOfUnwatchedEpisodes() {
        assertTrue(series(watched = 10, new = 0, total = 10).isFullyWatched())
        assertFalse(series(watched = 3, new = 7, total = 10).isFullyWatched())
    }

    @Test
    fun series_withoutTheUnwatchedCountFallsBackToTheEpisodeCount() {
        assertTrue(series(watched = 10, new = null, total = 10).isFullyWatched())
        assertFalse(series(watched = 3, new = null, total = 10).isFullyWatched())
    }

    @Test
    fun series_thatReportsNeitherIsNotCalledFinished() {
        // `watched` counts episodes, so on its own it only proves the account started watching.
        // Reading it as "finished" would hide a show three episodes in.
        assertFalse(series(watched = 3, new = null, total = null).isFullyWatched())
    }

    private fun movie(watched: Int?, status: Int? = null) = Item(
        id = 1,
        title = "Movie",
        type = ItemType.MOVIE,
        watched = watched,
        watching = status?.let { WatchingInfo(time = 0, duration = 0, status = it) },
    )

    private fun series(watched: Int?, new: Int?, total: Int?) = Item(
        id = 2,
        title = "Series",
        type = ItemType.SERIAL,
        watched = watched,
        new = new,
        total = total,
    )
}
