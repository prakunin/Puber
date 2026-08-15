package com.kino.puber.ui.feature.player.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

@Composable
internal fun AudioSubtitlesPanel(
    visible: Boolean,
    soundModes: List<SoundModeUIState>,
    selectedSoundModeIndex: Int,
    audioTracks: List<AudioTrackUIState>,
    selectedAudioTrackIndex: Int,
    subtitleTracks: List<SubtitleTrackUIState>,
    selectedSubtitleIndex: Int,
    subtitleSize: SubtitleSize,
    onSoundModeSelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleSizeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocusRequester = remember { FocusRequester() }
    val initialItemIndex = when {
        audioTracks.isNotEmpty() -> 1 + selectedAudioTrackIndex.coerceIn(audioTracks.indices)
        subtitleTracks.isNotEmpty() -> 1 + selectedSubtitleIndex.coerceIn(subtitleTracks.indices)
        else -> 1
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    LaunchedEffect(visible) {
        if (visible) {
            listState.scrollToItem(initialItemIndex)
            withFrameNanos { frameTimeNanos -> frameTimeNanos }
            runCatching { initialFocusRequester.requestFocus() }
        }
    }

    PlayerSidePanel(
        visible = visible,
        title = stringResource(R.string.player_audio_subtitles_title),
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (audioTracks.isNotEmpty()) {
                item(key = "audio_header") {
                    PlayerPanelSectionHeader(stringResource(R.string.player_panel_audio))
                }
                itemsIndexed(audioTracks, key = { index, _ -> "audio_$index" }) { index, item ->
                    PlayerPanelItem(
                        text = item.label,
                        selected = index == selectedAudioTrackIndex,
                        onClick = { onAudioTrackSelected(index) },
                        focusRequester = initialFocusRequester.takeIf {
                            index == (selectedAudioTrackIndex.takeIf(audioTracks.indices::contains) ?: 0)
                        },
                    )
                }
            }

            item(key = "subtitles_header") {
                PlayerPanelSectionHeader(stringResource(R.string.player_panel_subtitles))
            }
            itemsIndexed(subtitleTracks, key = { index, _ -> "subtitle_$index" }) { index, item ->
                PlayerPanelItem(
                    text = item.label,
                    selected = index == selectedSubtitleIndex,
                    onClick = { onSubtitleSelected(index) },
                    focusRequester = initialFocusRequester.takeIf {
                        audioTracks.isEmpty() &&
                            index == (selectedSubtitleIndex.takeIf(subtitleTracks.indices::contains) ?: 0)
                    },
                )
            }
            item(key = "subtitle_size") {
                PlayerPanelItem(
                    text = stringResource(R.string.player_subtitle_size),
                    trailingText = subtitleSizeLabel(subtitleSize),
                    onClick = onSubtitleSizeClick,
                    focusRequester = initialFocusRequester.takeIf {
                        audioTracks.isEmpty() && subtitleTracks.isEmpty()
                    },
                )
            }

            if (soundModes.isNotEmpty()) {
                item(key = "sound_header") {
                    PlayerPanelSectionHeader(stringResource(R.string.player_panel_sound))
                }
                itemsIndexed(soundModes, key = { index, _ -> "sound_$index" }) { index, item ->
                    PlayerPanelItem(
                        text = item.label,
                        selected = index == selectedSoundModeIndex,
                        onClick = { onSoundModeSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun subtitleSizeLabel(size: SubtitleSize): String {
    return stringResource(
        when (size) {
            SubtitleSize.SMALL -> R.string.player_subtitle_size_small
            SubtitleSize.MEDIUM -> R.string.player_subtitle_size_medium
            SubtitleSize.LARGE -> R.string.player_subtitle_size_large
        }
    )
}
