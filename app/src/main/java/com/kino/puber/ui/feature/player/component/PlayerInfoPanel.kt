package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerContentState

/**
 * Read-only summary of what is actually being played: the picked quality, what the decoder
 * reports about the stream, and how far ahead the buffer runs.
 */
@Composable
internal fun PlayerInfoPanel(
    visible: Boolean,
    entries: List<PlayerInfoEntry>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        PlayerInfoPanelContainer {
            Text(
                text = stringResource(R.string.player_info_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp),
            )
            InfoEntryColumns(entries = entries, modifier = Modifier.weight(1f))
            CloseButton(onClose = onClose)
        }
    }
}

internal data class PlayerInfoEntry(val label: String, val value: String)

@Composable
internal fun playerInfoEntries(content: PlayerContentState): List<PlayerInfoEntry> {
    val unknown = stringResource(R.string.player_info_unknown)
    val debug = content.debugInfo
    val pairs = listOf(
        stringResource(R.string.player_info_quality) to
            content.qualities.getOrNull(content.selectedQualityIndex)?.label,
        stringResource(R.string.player_info_resolution) to debug?.videoResolution,
        stringResource(R.string.player_info_video_codec) to debug?.videoCodec,
        stringResource(R.string.player_info_bitrate) to debug?.videoBitrate,
        stringResource(R.string.player_info_frame_rate) to debug?.videoFrameRate,
        stringResource(R.string.player_info_audio_track) to
            content.audioTracks.getOrNull(content.selectedAudioTrackIndex)?.label,
        stringResource(R.string.player_info_audio_codec) to
            debug?.let { "${it.audioCodec} · ${it.audioChannels}" },
        stringResource(R.string.player_info_buffer) to debug?.bufferedDuration,
        stringResource(R.string.player_info_buffer_preset) to
            content.bufferPresets.getOrNull(content.selectedBufferPresetIndex)?.label,
        stringResource(R.string.player_info_dropped_frames) to debug?.droppedFrames,
    )
    return pairs.map { (label, value) ->
        PlayerInfoEntry(label, value?.takeIf(String::isNotBlank) ?: unknown)
    }
}

@Composable
private fun PlayerInfoPanelContainer(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                // The only focusable here is the close button, and the video surface behind
                // the panel stays focusable — without this the first sideways press would
                // hand the D-pad back to the hidden player.
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup(),
            content = content,
        )
    }
}

@Composable
private fun InfoEntryColumns(entries: List<PlayerInfoEntry>, modifier: Modifier = Modifier) {
    val half = (entries.size + 1) / 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        InfoEntryColumn(entries = entries.take(half), modifier = Modifier.weight(1f))
        InfoEntryColumn(entries = entries.drop(half), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoEntryColumn(entries: List<PlayerInfoEntry>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = entry.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CloseButton(onClose: () -> Unit) {
    // The panel has nothing else to focus; without this the D-pad would have no anchor.
    Button(
        onClick = onClose,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = 16.dp)
            .focusRequester(rememberRequestingFocusRequester()),
    ) {
        Text(text = stringResource(R.string.player_info_close))
    }
}
