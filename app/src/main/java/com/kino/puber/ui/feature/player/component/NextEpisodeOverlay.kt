package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerCountdowns


@Composable
internal fun NextEpisodeOverlay(
    countdown: Int?,
    onNextEpisode: () -> Unit,
    onCancel: () -> Unit,
    onSeek: (PlayerAction) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    PlayerTimedActionOverlay(
        visible = countdown != null,
        // No number: the bar is the countdown, and a digit ticking beside it only competes with it.
        actionLabel = stringResource(R.string.player_next_episode),
        cancelLabel = stringResource(R.string.player_next_episode_cancel),
        seconds = countdown ?: PlayerCountdowns.NEXT_EPISODE_SEC,
        focusRequester = focusRequester,
        debugName = "next_episode",
        compactCancel = true,
        onAction = onNextEpisode,
        onCancel = onCancel,
        onSeek = onSeek,
        bottomInset = bottomInset,
        modifier = modifier,
    )
}
