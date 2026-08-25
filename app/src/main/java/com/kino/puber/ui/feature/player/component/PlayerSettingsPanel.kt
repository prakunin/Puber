package com.kino.puber.ui.feature.player.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

/**
 * One panel for everything the player can be told from the controls row.
 *
 * The three buttons — audio, video, info — no longer open three differently built panels; they
 * open the same root and put the focus on their own door. Settings that belong to another
 * setting live on its page instead of the root: the sound mode under the audio tracks, the
 * subtitle size under the subtitle tracks, the aspect ratio under the qualities.
 */
internal enum class SettingsDoor {
    Audio,
    Subtitles,
    Quality,
    Speed,
    Advanced,
    Info,
}

internal enum class SettingsPage {
    Root,
    Audio,
    Subtitles,
    Quality,
    Speed,
    Advanced,
    Info,
}

/** A door is hidden when the stream has nothing behind it; the rest are always there. */
internal fun settingsDoors(hasAudioTracks: Boolean, hasQualities: Boolean): List<SettingsDoor> {
    return SettingsDoor.entries.filter { door ->
        when (door) {
            SettingsDoor.Audio -> hasAudioTracks
            SettingsDoor.Quality -> hasQualities
            else -> true
        }
    }
}

/** Which door the pressed button lands on, falling forward when that door is not there. */
internal fun initialSettingsDoor(panel: ActivePanel, doors: List<SettingsDoor>): SettingsDoor {
    val preferred = when (panel) {
        ActivePanel.VideoSettings -> SettingsDoor.Quality
        ActivePanel.Info -> SettingsDoor.Info
        else -> SettingsDoor.Audio
    }
    return doors.firstOrNull { it.ordinal >= preferred.ordinal } ?: doors.first()
}

internal fun pageOf(door: SettingsDoor): SettingsPage {
    return when (door) {
        SettingsDoor.Audio -> SettingsPage.Audio
        SettingsDoor.Subtitles -> SettingsPage.Subtitles
        SettingsDoor.Quality -> SettingsPage.Quality
        SettingsDoor.Speed -> SettingsPage.Speed
        SettingsDoor.Advanced -> SettingsPage.Advanced
        SettingsDoor.Info -> SettingsPage.Info
    }
}

internal fun doorOf(page: SettingsPage): SettingsDoor? {
    return when (page) {
        SettingsPage.Root -> null
        SettingsPage.Audio -> SettingsDoor.Audio
        SettingsPage.Subtitles -> SettingsDoor.Subtitles
        SettingsPage.Quality -> SettingsDoor.Quality
        SettingsPage.Speed -> SettingsDoor.Speed
        SettingsPage.Advanced -> SettingsDoor.Advanced
        SettingsPage.Info -> SettingsDoor.Info
    }
}

private val RootSpacing = 4.dp
private val PageSpacing = 2.dp

/** [coerceIn] over an empty range throws; an empty page simply has nothing to focus. */
private fun Int.coerceToIndex(size: Int): Int = if (size <= 0) 0 else coerceIn(0, size - 1)

@Composable
internal fun PlayerSettingsPanel(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = content.activePanel
    val visible = panel == ActivePanel.AudioSubtitles ||
        panel == ActivePanel.VideoSettings ||
        panel == ActivePanel.Info
    val doors = settingsDoors(
        hasAudioTracks = content.audioTracks.isNotEmpty(),
        hasQualities = content.qualities.isNotEmpty(),
    )

    var page by remember { mutableStateOf(SettingsPage.Root) }
    var rootDoor by remember { mutableStateOf(SettingsDoor.Subtitles) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, panel) {
        page = SettingsPage.Root
        if (visible) {
            rootDoor = initialSettingsDoor(panel, doors)
        }
    }
    LaunchedEffect(visible, page, rootDoor) {
        if (!visible) return@LaunchedEffect
        withFrameNanos { frameTimeNanos -> frameTimeNanos }
        runCatching { focusRequester.requestFocus() }
    }
    BackHandler(enabled = visible && page != SettingsPage.Root) {
        page = SettingsPage.Root
    }

    PlayerSidePanel(
        visible = visible,
        title = settingsPageTitle(page),
        modifier = modifier,
    ) {
        key(page) {
            when (page) {
                SettingsPage.Root -> SettingsRoot(
                    content = content,
                    doors = doors,
                    focusedDoor = rootDoor,
                    focusRequester = focusRequester,
                    onDoorSelected = { door ->
                        rootDoor = door
                        page = pageOf(door)
                    },
                )
                SettingsPage.Audio -> AudioPage(content, onAction, focusRequester)
                SettingsPage.Subtitles -> SubtitlesPage(content, onAction, focusRequester)
                SettingsPage.Quality -> QualityPage(content, onAction, focusRequester)
                SettingsPage.Speed -> SpeedPage(content, onAction, focusRequester)
                SettingsPage.Advanced -> AdvancedPage(content, onAction, focusRequester)
                SettingsPage.Info -> PlayerInfoPage(
                    entries = playerInfoEntries(content),
                    focusRequester = focusRequester,
                )
            }
        }
    }
}

@Composable
private fun SettingsColumn(
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsRoot(
    content: PlayerContentState,
    doors: List<SettingsDoor>,
    focusedDoor: SettingsDoor,
    focusRequester: FocusRequester,
    onDoorSelected: (SettingsDoor) -> Unit,
) {
    SettingsColumn(spacing = RootSpacing) {
        doors.forEach { door ->
            PlayerPanelItem(
                text = doorTitle(door),
                trailingText = doorValue(door, content),
                onClick = { onDoorSelected(door) },
                focusRequester = focusRequester.takeIf { door == focusedDoor },
            )
        }
    }
}

@Composable
private fun AudioPage(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusIndex = content.selectedAudioTrackIndex.coerceToIndex(content.audioTracks.size)
    SettingsColumn(spacing = PageSpacing) {
        content.audioTracks.forEachIndexed { index, track ->
            PlayerPanelItem(
                text = track.label,
                selected = index == content.selectedAudioTrackIndex,
                onClick = { onAction(PlayerAction.SelectAudioTrack(index)) },
                focusRequester = focusRequester.takeIf { index == focusIndex },
            )
        }
        // The sound mode belongs to the audio track, not to the root; it cycles in place,
        // the way the subtitle size does. A single mode is not a choice, so it stays hidden.
        if (content.soundModes.size > 1) {
            PlayerPanelItem(
                text = stringResource(R.string.player_door_sound),
                trailingText = content.soundModes.getOrNull(content.selectedSoundModeIndex)?.label,
                onClick = {
                    val next = (content.selectedSoundModeIndex + 1) % content.soundModes.size
                    onAction(PlayerAction.SelectSoundMode(next))
                },
            )
        }
    }
}

@Composable
private fun SubtitlesPage(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val hasTracks = content.subtitleTracks.isNotEmpty()
    val focusIndex = content.selectedSubtitleIndex.coerceToIndex(content.subtitleTracks.size)
    SettingsColumn(spacing = PageSpacing) {
        content.subtitleTracks.forEachIndexed { index, track ->
            PlayerPanelItem(
                text = track.label,
                selected = index == content.selectedSubtitleIndex,
                onClick = { onAction(PlayerAction.SelectSubtitle(index)) },
                focusRequester = focusRequester.takeIf { index == focusIndex },
            )
        }
        PlayerPanelItem(
            text = stringResource(R.string.player_subtitle_size),
            trailingText = subtitleSizeLabel(content.subtitleSize),
            onClick = { onAction(PlayerAction.CycleSubtitleSize) },
            focusRequester = focusRequester.takeIf { !hasTracks },
        )
    }
}

@Composable
private fun QualityPage(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusIndex = content.selectedQualityIndex.coerceToIndex(content.qualities.size)
    SettingsColumn(spacing = PageSpacing) {
        content.qualities.forEachIndexed { index, quality ->
            PlayerPanelItem(
                text = quality.label,
                selected = index == content.selectedQualityIndex,
                onClick = { onAction(PlayerAction.SelectQuality(index)) },
                focusRequester = focusRequester.takeIf { index == focusIndex },
            )
        }
        if (content.aspectRatios.isNotEmpty()) {
            PlayerPanelItem(
                text = stringResource(R.string.player_door_aspect_ratio),
                trailingText = content.aspectRatios
                    .getOrNull(content.selectedAspectRatioIndex)?.label,
                onClick = {
                    val next = (content.selectedAspectRatioIndex + 1) % content.aspectRatios.size
                    onAction(PlayerAction.SelectAspectRatio(next))
                },
            )
        }
    }
}

@Composable
private fun SpeedPage(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusIndex = content.selectedSpeedIndex.coerceToIndex(content.speeds.size)
    SettingsColumn(spacing = PageSpacing) {
        content.speeds.forEachIndexed { index, speed ->
            PlayerPanelItem(
                text = speed.label,
                selected = index == content.selectedSpeedIndex,
                onClick = { onAction(PlayerAction.SelectSpeed(index)) },
                focusRequester = focusRequester.takeIf { index == focusIndex },
            )
        }
    }
}

@Composable
private fun AdvancedPage(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
    focusRequester: FocusRequester,
) {
    val focusIndex = content.selectedBufferPresetIndex.coerceToIndex(content.bufferPresets.size)
    SettingsColumn(spacing = PageSpacing) {
        if (content.bufferPresets.isNotEmpty()) {
            PlayerPanelSectionHeader(stringResource(R.string.player_panel_buffer))
            content.bufferPresets.forEachIndexed { index, preset ->
                PlayerPanelItem(
                    text = preset.label,
                    selected = index == content.selectedBufferPresetIndex,
                    onClick = { onAction(PlayerAction.SelectBufferPreset(index)) },
                    focusRequester = focusRequester.takeIf { index == focusIndex },
                )
            }
        }
        PlayerPanelSectionHeader(stringResource(R.string.player_panel_network))
        PlayerPanelItem(
            text = stringResource(R.string.player_fast_dns),
            supportingText = stringResource(R.string.player_fast_dns_hint),
            trailingText = stringResource(
                if (content.fastDnsEnabled) R.string.player_setting_enabled else R.string.player_setting_disabled,
            ),
            selected = content.fastDnsEnabled,
            onClick = { onAction(PlayerAction.ToggleFastDns) },
            focusRequester = focusRequester.takeIf { content.bufferPresets.isEmpty() },
        )
    }
}

@Composable
private fun doorTitle(door: SettingsDoor): String {
    return stringResource(
        when (door) {
            SettingsDoor.Audio -> R.string.player_door_audio
            SettingsDoor.Subtitles -> R.string.player_door_subtitles
            SettingsDoor.Quality -> R.string.player_door_quality
            SettingsDoor.Speed -> R.string.player_door_speed
            SettingsDoor.Advanced -> R.string.player_panel_advanced
            SettingsDoor.Info -> R.string.player_info_title
        }
    )
}

@Composable
private fun doorValue(door: SettingsDoor, content: PlayerContentState): String? {
    return when (door) {
        SettingsDoor.Audio -> content.audioTracks.getOrNull(content.selectedAudioTrackIndex)?.label
        SettingsDoor.Subtitles -> content.subtitleTracks
            .getOrNull(content.selectedSubtitleIndex)?.label
            ?: stringResource(R.string.player_subtitles_off)
        SettingsDoor.Quality -> content.qualities.getOrNull(content.selectedQualityIndex)?.label
        SettingsDoor.Speed -> content.speeds.getOrNull(content.selectedSpeedIndex)?.label
        SettingsDoor.Advanced -> content.bufferPresets
            .getOrNull(content.selectedBufferPresetIndex)?.label
        SettingsDoor.Info -> content.debugInfo?.videoResolution
    }
}

@Composable
private fun settingsPageTitle(page: SettingsPage): String {
    return stringResource(
        when (page) {
            SettingsPage.Root -> R.string.player_settings_title
            SettingsPage.Audio -> R.string.player_door_audio
            SettingsPage.Subtitles -> R.string.player_door_subtitles
            SettingsPage.Quality -> R.string.player_door_quality
            SettingsPage.Speed -> R.string.player_door_speed
            SettingsPage.Advanced -> R.string.player_panel_advanced
            SettingsPage.Info -> R.string.player_info_title
        }
    )
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
