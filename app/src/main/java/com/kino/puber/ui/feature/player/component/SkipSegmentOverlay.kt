package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState

private const val COUNTDOWN_TOTAL_SEC = 7

@Composable
internal fun SkipSegmentOverlay(
    state: SkipSegmentUIState?,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerTimedActionOverlay(
        visible = state != null,
        actionLabel = state?.label.orEmpty(),
        cancelLabel = stringResource(R.string.skip_cancel),
        countdown = state?.countdown ?: COUNTDOWN_TOTAL_SEC,
        totalSeconds = COUNTDOWN_TOTAL_SEC,
        focusPrimaryAction = false,
        compactCancel = true,
        onAction = onSkip,
        onCancel = onCancel,
        modifier = modifier,
    )
}
