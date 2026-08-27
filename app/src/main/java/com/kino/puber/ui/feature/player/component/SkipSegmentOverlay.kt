package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerCountdowns
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState


@Composable
internal fun SkipSegmentOverlay(
    state: SkipSegmentUIState?,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    PlayerTimedActionOverlay(
        visible = state != null,
        // No number: the bar is the countdown, and a digit ticking beside it only competes with it.
        actionLabel = state?.label.orEmpty(),
        cancelLabel = stringResource(R.string.skip_cancel),
        countdown = state?.countdown ?: PlayerCountdowns.SKIP_SEGMENT_SEC,
        // Per prompt, not a constant: a segment with little left is offered a shorter countdown.
        totalSeconds = state?.totalSeconds ?: PlayerCountdowns.SKIP_SEGMENT_SEC,
        focusPrimaryAction = false,
        debugName = state?.type?.let { "skip_${it.name.lowercase()}" } ?: "skip_segment",
        // One segment can take over from another with the plate never leaving the screen. Keyed on
        // where it lands as well as its kind, so two ranges of one kind are two prompts.
        promptKey = state?.let { it.type to it.targetPositionMs },
        compactCancel = true,
        onAction = onSkip,
        onCancel = onCancel,
        bottomInset = bottomInset,
        modifier = modifier,
    )
}
