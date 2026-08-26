package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

internal class PlayerFocusRequesters {
    val player = FocusRequester()
    val firstButton = FocusRequester()
    val episodesButton = FocusRequester()
    val aboutButton = FocusRequester()
    val settingsButton = FocusRequester()
    val seekBar = FocusRequester()
}

@Composable
internal fun rememberPlayerFocusRequesters(): PlayerFocusRequesters {
    return remember { PlayerFocusRequesters() }
}
