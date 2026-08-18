package com.kino.puber.core.contentlink

import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.player.model.PlayerStartMode

internal fun ContentTarget.toScreen(screens: Screens): PuberScreen = when (this) {
    is ContentTarget.Details -> screens.details(itemId)
    is ContentTarget.EpisodeDetails -> screens.details(
        itemId = itemId,
        initialEpisode = DetailsEpisodeTarget(
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        ),
    )
    is ContentTarget.Playback -> screens.player(
        itemId = itemId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        videoNumber = videoNumber,
        startMode = PlayerStartMode.ResumeIfAvailable,
    )
}
