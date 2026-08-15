package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kino.puber.R

private const val COUNTDOWN_TOTAL_SEC = 15

@Composable
internal fun NextEpisodeOverlay(
    countdown: Int?,
    onNextEpisode: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerTimedActionOverlay(
        visible = countdown != null,
        actionLabel = stringResource(R.string.player_next_episode_countdown, countdown ?: 0),
        cancelLabel = stringResource(R.string.player_next_episode_cancel),
        countdown = countdown ?: COUNTDOWN_TOTAL_SEC,
        totalSeconds = COUNTDOWN_TOTAL_SEC,
        focusPrimaryAction = true,
        compactCancel = true,
        onAction = onNextEpisode,
        onCancel = onCancel,
        modifier = modifier,
    )
}
