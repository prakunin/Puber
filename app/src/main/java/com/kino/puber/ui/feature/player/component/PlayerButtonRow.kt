package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.duotone.Eye
import com.adamglin.phosphoricons.fill.Eye
import com.kino.puber.R

@Composable
internal fun PlayerButtonRow(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (!shouldOpenEpisodesFromButtons(event.nativeKeyEvent.keyCode, state.isMovie)) {
                    false
                } else {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        actions.onEpisodesClick()
                    }
                    true
                }
            }
            .padding(
                start = PlayerControlsMetrics.SideMargin,
                end = PlayerControlsMetrics.SideMargin,
                top = PlayerControlsMetrics.ButtonRowTopPadding,
                bottom = PlayerControlsMetrics.BottomMargin,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeLibraryButton(
            state = state,
            actions = actions,
            focusRequesters = focusRequesters,
            modifier = Modifier.weight(1f),
        )
        TransportControls(
            state = state,
            actions = actions,
            focusRequesters = focusRequesters,
            modifier = Modifier.weight(1f),
        )
        SecondaryControls(
            state = state,
            actions = actions,
            focusRequesters = focusRequesters,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun shouldOpenEpisodesFromButtons(keyCode: Int, isMovie: Boolean): Boolean {
    return !isMovie && keyCode == KeyEvent.KEYCODE_DPAD_DOWN
}

internal data class PlayerButtonRowState(
    val isMovie: Boolean,
    val isPlaying: Boolean,
    val hasNextEpisode: Boolean,
    val hasPreviousEpisode: Boolean,
    val canMarkCurrentWatched: Boolean,
    val isCurrentMediaWatched: Boolean,
    val isMarkCurrentWatchedInFlight: Boolean,
)

@Composable
private fun EpisodeLibraryButton(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlayerControlsMetrics.ButtonGap, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isMovie) {
            ControlButton(
                description = stringResource(R.string.player_button_episodes),
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                onClick = actions.onEpisodesClick,
                modifier = Modifier.focusRequester(focusRequesters.episodesButton),
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            PlayerControlsMetrics.ButtonGap,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isMovie && state.hasPreviousEpisode) {
            ControlButton(
                description = stringResource(R.string.player_button_previous_episode),
                icon = Icons.Default.SkipPrevious,
                onClick = actions.onPreviousEpisodeClick,
            )
        }
        PlayPauseButton(
            isPlaying = state.isPlaying,
            onClick = actions.onTogglePlayPause,
            focusRequester = focusRequesters.firstButton,
        )
        if (!state.isMovie && state.hasNextEpisode) {
            ControlButton(
                description = stringResource(R.string.player_button_next_episode),
                icon = Icons.Default.SkipNext,
                onClick = actions.onNextEpisodeClick,
            )
        }
    }
}

@Composable
private fun SecondaryControls(
    state: PlayerButtonRowState,
    actions: PlayerControlActions,
    focusRequesters: PlayerControlFocusRequesters,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(PlayerControlsMetrics.ButtonGap, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            description = stringResource(R.string.player_button_audio_subtitles),
            icon = Icons.Default.Subtitles,
            onClick = actions.onAudioSubtitlesClick,
            modifier = Modifier.focusRequester(focusRequesters.audioSubtitlesButton),
        )
        ControlButton(
            description = stringResource(R.string.player_button_video),
            icon = Icons.Default.Videocam,
            onClick = actions.onVideoSettingsClick,
            modifier = Modifier.focusRequester(focusRequesters.videoSettingsButton),
        )
        ControlButton(
            description = stringResource(R.string.player_button_info),
            icon = Icons.Outlined.Info,
            onClick = actions.onInfoClick,
            modifier = Modifier.focusRequester(focusRequesters.infoButton),
        )
        if (state.canMarkCurrentWatched) {
            ControlButton(
                description = stringResource(R.string.player_button_mark_watched),
                icon = if (state.isCurrentMediaWatched) {
                    PhosphorIcons.Fill.Eye
                } else {
                    PhosphorIcons.Duotone.Eye
                },
                onClick = actions.onMarkCurrentWatchedClick,
                selected = state.isCurrentMediaWatched,
                loading = state.isMarkCurrentWatchedInFlight,
                stateDescription = stringResource(
                    if (state.isCurrentMediaWatched) {
                        R.string.player_button_mark_watched_state_watched
                    } else {
                        R.string.player_button_mark_watched_state_not_watched
                    }
                ),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    val description = stringResource(
        if (isPlaying) R.string.player_button_pause else R.string.player_button_play,
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .size(width = PlayerControlsMetrics.ButtonWidth, height = PlayerControlsMetrics.ButtonHeight)
            .focusRequester(focusRequester)
            .semantics { contentDescription = description },
        colors = transparentButtonColors(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(PlayerControlsMetrics.PlayPauseIcon),
        )
    }
}

@Composable
private fun ControlButton(
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    loading: Boolean = false,
    stateDescription: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = PlayerControlsMetrics.ButtonWidth, height = PlayerControlsMetrics.ButtonHeight)
            .semantics {
                contentDescription = description
                selected?.let { this.selected = it }
                stateDescription?.let { this.stateDescription = it }
            },
        enabled = !loading,
        colors = if (selected == true) selectedButtonColors() else transparentButtonColors(),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(PlayerControlsMetrics.ButtonIcon),
                strokeWidth = PlayerControlsMetrics.ButtonProgressStroke,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(PlayerControlsMetrics.ButtonIcon),
            )
        }
    }
}

@Composable
private fun transparentButtonColors() = ButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonContainerAlpha),
    contentColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonContentAlpha),
    focusedContainerColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonFocusedContainerAlpha),
    focusedContentColor = MaterialTheme.colorScheme.onSurface,
    pressedContainerColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonPressedContainerAlpha),
    pressedContentColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun selectedButtonColors() = ButtonDefaults.colors(
    containerColor = MaterialTheme.colorScheme.primary
        .copy(alpha = PlayerControlsMetrics.ButtonSelectedContainerAlpha),
    contentColor = MaterialTheme.colorScheme.primary,
    focusedContainerColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonFocusedContainerAlpha),
    focusedContentColor = MaterialTheme.colorScheme.onSurface,
    pressedContainerColor = MaterialTheme.colorScheme.onSurface
        .copy(alpha = PlayerControlsMetrics.ButtonPressedContainerAlpha),
    pressedContentColor = MaterialTheme.colorScheme.onSurface,
)
