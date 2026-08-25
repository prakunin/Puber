package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerCountdowns


@Composable
internal fun NextEpisodeOverlay(
    countdown: Int?,
    onNextEpisode: () -> Unit,
    onCancel: () -> Unit,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    PlayerTimedActionOverlay(
        visible = countdown != null,
        // No number: the bar is the countdown, and a digit ticking beside it only competes with it.
        actionLabel = stringResource(R.string.player_next_episode),
        cancelLabel = stringResource(R.string.player_next_episode_cancel),
        countdown = countdown ?: PlayerCountdowns.NEXT_EPISODE_SEC,
        totalSeconds = PlayerCountdowns.NEXT_EPISODE_SEC,
        focusPrimaryAction = true,
        debugName = "next_episode",
        compactCancel = true,
        onAction = onNextEpisode,
        onCancel = onCancel,
        bottomInset = bottomInset,
        modifier = modifier,
    )
}
