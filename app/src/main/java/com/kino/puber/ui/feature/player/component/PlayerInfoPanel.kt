package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerContentState
import kotlinx.coroutines.launch

/** Read-only stream diagnostics shown without interrupting playback. */
@Composable
internal fun PlayerInfoPanel(
    visible: Boolean,
    entries: List<PlayerInfoEntry>,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) runCatching { focusRequester.requestFocus() }
    }

    PlayerSidePanel(
        visible = visible,
        title = stringResource(R.string.player_info_title),
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        val scope = rememberCoroutineScope()
        val scrollStepPx = with(LocalDensity.current) { 88.dp.toPx() }
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused by interactionSource.collectIsFocusedAsState()
        val focusShape = RoundedCornerShape(12.dp)

        Column(
            modifier = Modifier
                .focusRequester(focusRequester)
                .border(
                    width = 1.dp,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                    } else {
                        Color.Transparent
                    },
                    shape = focusShape,
                )
                .background(
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                    } else {
                        Color.Transparent
                    },
                    shape = focusShape,
                )
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    val delta = when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> scrollStepPx
                        KeyEvent.KEYCODE_DPAD_UP -> -scrollStepPx
                        else -> return@onPreviewKeyEvent false
                    }
                    scope.launch { scrollState.animateScrollBy(delta) }
                    true
                }
                .focusable(interactionSource = interactionSource)
                .verticalScroll(scrollState)
                .padding(4.dp),
        ) {
            InfoSection(
                title = stringResource(R.string.player_info_section_video),
                entries = entries.take(VIDEO_ENTRY_COUNT),
            )
            InfoSection(
                title = stringResource(R.string.player_info_section_audio),
                entries = entries.drop(VIDEO_ENTRY_COUNT).take(AUDIO_ENTRY_COUNT),
            )
            InfoSection(
                title = stringResource(R.string.player_info_section_playback),
                entries = entries.drop(VIDEO_ENTRY_COUNT + AUDIO_ENTRY_COUNT),
            )
            Spacer(modifier = Modifier.height(16.dp))
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
        stringResource(R.string.player_info_source) to debug?.streamSource,
        stringResource(R.string.player_info_buffer) to
            debug?.let { "${it.bufferedDuration} · ${it.bufferedBytes}" },
        stringResource(R.string.player_info_buffer_preset) to
            content.bufferPresets.getOrNull(content.selectedBufferPresetIndex)?.label,
        stringResource(R.string.player_info_dropped_frames) to debug?.droppedFrames,
    )
    return pairs.map { (label, value) ->
        PlayerInfoEntry(label, value?.takeIf(String::isNotBlank) ?: unknown)
    }
}

@Composable
private fun InfoSection(title: String, entries: List<PlayerInfoEntry>) {
    if (entries.isEmpty()) return
    PlayerPanelSectionHeader(text = title)
    entries.forEach { entry ->
        PlayerPanelReadOnlyRow(label = entry.label, value = entry.value)
    }
}

private const val VIDEO_ENTRY_COUNT = 5
private const val AUDIO_ENTRY_COUNT = 2
