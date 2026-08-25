package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerContentState
import kotlinx.coroutines.launch

/**
 * Read-only stream diagnostics, a page inside [PlayerSettingsPanel].
 *
 * Nothing here is selectable, so the whole column takes the focus and the arrows scroll it —
 * focusing a row that does not act would promise something that is not there.
 */
@Composable
internal fun PlayerInfoPage(
    entries: List<PlayerInfoEntry>,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { 88.dp.toPx() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusShape = RoundedCornerShape(12.dp)

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                        shape = focusShape,
                    )
                } else {
                    Modifier
                },
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
            .padding(2.dp),
    ) {
        InfoSection(
            title = stringResource(R.string.player_info_section_video),
            entries = entries.filter { it.section == PlayerInfoSection.Video },
        )
        InfoSection(
            title = stringResource(R.string.player_info_section_playback),
            entries = entries.filter { it.section == PlayerInfoSection.Playback },
        )
        InfoSection(
            title = stringResource(R.string.player_info_section_audio),
            entries = entries.filter { it.section == PlayerInfoSection.Audio },
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

internal enum class PlayerInfoSection {
    Video,
    Playback,
    Audio,
}

internal data class PlayerInfoEntry(
    val section: PlayerInfoSection,
    val label: String,
    val value: String,
)

@Composable
internal fun playerInfoEntries(content: PlayerContentState): List<PlayerInfoEntry> {
    val unknown = stringResource(R.string.player_info_unknown)
    val debug = content.debugInfo
    val rows = listOf(
        InfoRow(
            PlayerInfoSection.Video,
            R.string.player_info_quality,
            content.qualities.getOrNull(content.selectedQualityIndex)?.label,
        ),
        InfoRow(PlayerInfoSection.Video, R.string.player_info_resolution, debug?.videoResolution),
        InfoRow(PlayerInfoSection.Video, R.string.player_info_bitrate, debug?.videoBitrate),
        InfoRow(PlayerInfoSection.Video, R.string.player_info_frame_rate, debug?.videoFrameRate),
        InfoRow(PlayerInfoSection.Playback, R.string.player_info_source, debug?.streamSource),
        InfoRow(
            PlayerInfoSection.Playback,
            R.string.player_info_buffer,
            cleanInfoParts(debug?.bufferedDuration, debug?.bufferedBytes),
        ),
        InfoRow(
            PlayerInfoSection.Playback,
            R.string.player_info_buffer_preset,
            content.bufferPresets.getOrNull(content.selectedBufferPresetIndex)?.label,
        ),
        InfoRow(PlayerInfoSection.Playback, R.string.player_info_dropped_frames, debug?.droppedFrames),
        InfoRow(PlayerInfoSection.Playback, R.string.player_info_video_codec, debug?.videoCodec),
        InfoRow(
            PlayerInfoSection.Playback,
            R.string.player_info_audio_codec,
            cleanInfoParts(debug?.audioCodec, debug?.audioChannels),
        ),
        InfoRow(
            PlayerInfoSection.Audio,
            R.string.player_info_audio_track,
            content.audioTracks.getOrNull(content.selectedAudioTrackIndex)?.label,
        ),
    )
    return rows.map { row ->
        PlayerInfoEntry(
            section = row.section,
            label = stringResource(row.labelRes),
            value = row.value?.takeIf(String::isNotBlank) ?: unknown,
        )
    }
}

private class InfoRow(
    val section: PlayerInfoSection,
    val labelRes: Int,
    val value: String?,
)

internal fun cleanInfoParts(vararg parts: String?): String? {
    return parts
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString(separator = " · ")
        .takeIf(String::isNotEmpty)
}

/**
 * No glass of its own: the panel underneath is already [PlayerGlass.Strong], and a second
 * translucent layer only muddied the edge and cost height. The heading and the gap group
 * the rows well enough.
 */
@Composable
private fun InfoSection(title: String, entries: List<PlayerInfoEntry>) {
    if (entries.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        PlayerPanelSectionHeader(text = title)
        entries.forEach { entry ->
            PlayerPanelReadOnlyRow(label = entry.label, value = entry.value)
        }
    }
}
