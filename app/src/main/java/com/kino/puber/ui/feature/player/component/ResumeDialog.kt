package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.ResumeDialogState

private const val DIALOG_ENTER_MS = 210
private const val DIALOG_EXIT_MS = 150
private const val SCRIM_MIDDLE_STOP = 0.48f

@Composable
internal fun ResumeDialog(
    state: ResumeDialogState?,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resumeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state != null) {
        if (state != null) runCatching { resumeFocusRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state != null,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(DIALOG_ENTER_MS)),
            exit = fadeOut(tween(DIALOG_EXIT_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resumeScrim()),
            )
        }

        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp),
            transitionSpec = {
                (fadeIn(tween(DIALOG_ENTER_MS)) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(DIALOG_ENTER_MS),
                )).togetherWith(
                    fadeOut(tween(DIALOG_EXIT_MS)) + scaleOut(
                        targetScale = 0.98f,
                        animationSpec = tween(DIALOG_EXIT_MS),
                    )
                )
            },
            contentKey = { it != null },
            label = "player_resume_dialog",
        ) { displayedState ->
            displayedState?.let {
                ResumeDialogCard(
                    state = it,
                    resumeFocusRequester = resumeFocusRequester,
                    onResume = onResume,
                    onStartFromBeginning = onStartFromBeginning,
                )
            }
        }
    }
}

@Composable
private fun ResumeDialogCard(
    state: ResumeDialogState,
    resumeFocusRequester: FocusRequester,
    onResume: () -> Unit,
    onStartFromBeginning: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(520.dp)
            .playerGlass(
                shape = RoundedCornerShape(24.dp),
                level = PlayerGlass.Strong,
                elevation = 24.dp,
            )
            .padding(horizontal = 30.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.episodeInfo.isNullOrBlank()) {
            Text(
                text = state.episodeInfo,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            )
        }
        Text(
            text = stringResource(R.string.player_resume_title, state.formattedTime),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onResume,
                modifier = Modifier.focusRequester(resumeFocusRequester),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    focusedContentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(text = stringResource(R.string.player_resume_continue))
            }
            Button(
                onClick = onStartFromBeginning,
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
                    focusedContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(text = stringResource(R.string.player_resume_from_start))
            }
        }
    }
}

@Composable
private fun resumeScrim(): Brush {
    val scrim = MaterialTheme.colorScheme.scrim
    return Brush.horizontalGradient(
        colorStops = arrayOf(
            0f to scrim.copy(alpha = 0.70f),
            SCRIM_MIDDLE_STOP to scrim.copy(alpha = 0.45f),
            1f to scrim.copy(alpha = 0.16f),
        ),
    )
}
