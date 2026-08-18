package com.kino.puber.core.contentlink

internal sealed interface ContentTarget {
    val itemId: Int

    data class Details(
        override val itemId: Int,
    ) : ContentTarget {
        init {
            require(itemId > 0) { "Item id must be positive" }
        }
    }

    data class EpisodeDetails(
        override val itemId: Int,
        val seasonNumber: Int,
        val episodeNumber: Int,
    ) : ContentTarget {
        init {
            require(itemId > 0) { "Item id must be positive" }
            require(seasonNumber > 0) { "Season number must be positive" }
            require(episodeNumber > 0) { "Episode number must be positive" }
        }
    }

    data class Playback(
        override val itemId: Int,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val videoNumber: Int? = null,
    ) : ContentTarget {
        init {
            require(itemId > 0) { "Item id must be positive" }
            require((seasonNumber == null) == (episodeNumber == null)) {
                "Season and episode must be supplied together"
            }
            require(seasonNumber == null || seasonNumber > 0) { "Season number must be positive" }
            require(episodeNumber == null || episodeNumber > 0) { "Episode number must be positive" }
            require(videoNumber == null || videoNumber > 0) { "Video number must be positive" }
        }
    }
}
