package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.PlayerContentState

/**
 * Read-only stream diagnostics — the «Поток» door inside [PlayerSettingsPanel].
 *
 * The step is a whole row, so an arrow press never parks half a reading under the top edge.
 */
@Composable
internal fun PlayerStreamPage(
    entries: List<PlayerInfoEntry>,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    PlayerPanelScrollBox(
        focusRequester = focusRequester,
        scrollStep = StreamScrollStep,
        scrollState = rememberScrollState(),
        modifier = modifier,
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

/** Two rows and their padding; the readings are single-line and evenly tall. */
private val StreamScrollStep = 88.dp

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
