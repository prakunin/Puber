package com.kino.puber.ui.feature.player.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.ui.feature.player.model.AspectRatioUIState
import com.kino.puber.ui.feature.player.model.BufferPresetUIState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.SpeedUIState

private enum class VideoSettingsPage {
    ROOT,
    QUALITY,
    SPEED,
    ASPECT_RATIO,
    ADVANCED,
}

@Composable
internal fun VideoSettingsPanel(
    visible: Boolean,
    qualities: List<QualityUIState>,
    selectedQualityIndex: Int,
    speeds: List<SpeedUIState>,
    selectedSpeedIndex: Int,
    aspectRatios: List<AspectRatioUIState>,
    selectedAspectRatioIndex: Int,
    bufferPresets: List<BufferPresetUIState>,
    selectedBufferPresetIndex: Int,
    fastDnsEnabled: Boolean,
    onQualitySelected: (Int) -> Unit,
    onSpeedSelected: (Int) -> Unit,
    onAspectRatioSelected: (Int) -> Unit,
    onBufferPresetSelected: (Int) -> Unit,
    onToggleFastDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(VideoSettingsPage.ROOT) }
    var rootFocusTarget by remember { mutableStateOf(VideoSettingsPage.QUALITY) }
    val pageFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, page) {
        if (!visible) {
            page = VideoSettingsPage.ROOT
        } else {
            runCatching { pageFocusRequester.requestFocus() }
        }
    }
    BackHandler(enabled = visible && page != VideoSettingsPage.ROOT) {
        page = VideoSettingsPage.ROOT
    }

    PlayerSidePanel(
        visible = visible,
        title = videoSettingsPageTitle(page),
        modifier = modifier,
    ) {
        when (page) {
            VideoSettingsPage.ROOT -> VideoSettingsRoot(
                selectedQuality = qualities.getOrNull(selectedQualityIndex)?.label,
                selectedSpeed = speeds.getOrNull(selectedSpeedIndex)?.label,
                selectedAspectRatio = aspectRatios.getOrNull(selectedAspectRatioIndex)?.label,
                selectedBufferPreset = bufferPresets.getOrNull(selectedBufferPresetIndex)?.label,
                focusTarget = rootFocusTarget,
                focusRequester = pageFocusRequester,
                onPageSelected = {
                    rootFocusTarget = it
                    page = it
                },
            )
            VideoSettingsPage.QUALITY -> SelectionPage(
                items = qualities.map { it.label },
                selectedIndex = selectedQualityIndex,
                focusRequester = pageFocusRequester,
                onItemSelected = onQualitySelected,
            )
            VideoSettingsPage.SPEED -> SelectionPage(
                items = speeds.map { it.label },
                selectedIndex = selectedSpeedIndex,
                focusRequester = pageFocusRequester,
                onItemSelected = onSpeedSelected,
            )
            VideoSettingsPage.ASPECT_RATIO -> SelectionPage(
                items = aspectRatios.map { it.label },
                selectedIndex = selectedAspectRatioIndex,
                focusRequester = pageFocusRequester,
                onItemSelected = onAspectRatioSelected,
            )
            VideoSettingsPage.ADVANCED -> AdvancedSettingsPage(
                bufferPresets = bufferPresets,
                selectedBufferPresetIndex = selectedBufferPresetIndex,
                fastDnsEnabled = fastDnsEnabled,
                focusRequester = pageFocusRequester,
                onBufferPresetSelected = onBufferPresetSelected,
                onToggleFastDns = onToggleFastDns,
            )
        }
    }
}

@Composable
private fun VideoSettingsRoot(
    selectedQuality: String?,
    selectedSpeed: String?,
    selectedAspectRatio: String?,
    selectedBufferPreset: String?,
    focusTarget: VideoSettingsPage,
    focusRequester: FocusRequester,
    onPageSelected: (VideoSettingsPage) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item(key = "quality") {
            PlayerPanelItem(
                text = stringResource(R.string.player_panel_quality),
                trailingText = selectedQuality,
                onClick = { onPageSelected(VideoSettingsPage.QUALITY) },
                focusRequester = focusRequester.takeIf { focusTarget == VideoSettingsPage.QUALITY },
            )
        }
        item(key = "speed") {
            PlayerPanelItem(
                text = stringResource(R.string.player_panel_speed),
                trailingText = selectedSpeed,
                onClick = { onPageSelected(VideoSettingsPage.SPEED) },
                focusRequester = focusRequester.takeIf { focusTarget == VideoSettingsPage.SPEED },
            )
        }
        item(key = "aspect") {
            PlayerPanelItem(
                text = stringResource(R.string.player_panel_aspect_ratio),
                trailingText = selectedAspectRatio,
                onClick = { onPageSelected(VideoSettingsPage.ASPECT_RATIO) },
                focusRequester = focusRequester.takeIf { focusTarget == VideoSettingsPage.ASPECT_RATIO },
            )
        }
        item(key = "advanced") {
            PlayerPanelItem(
                text = stringResource(R.string.player_panel_advanced),
                trailingText = selectedBufferPreset,
                onClick = { onPageSelected(VideoSettingsPage.ADVANCED) },
                focusRequester = focusRequester.takeIf { focusTarget == VideoSettingsPage.ADVANCED },
            )
        }
    }
}

@Composable
private fun SelectionPage(
    items: List<String>,
    selectedIndex: Int,
    focusRequester: FocusRequester,
    onItemSelected: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    val focusIndex = selectedIndex.coerceIn(items.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex)
    LaunchedEffect(Unit) {
        listState.scrollToItem(focusIndex)
        withFrameNanos { frameTimeNanos -> frameTimeNanos }
        runCatching { focusRequester.requestFocus() }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        itemsIndexed(items, key = { index, _ -> index }) { index, item ->
            PlayerPanelItem(
                text = item,
                selected = index == selectedIndex,
                onClick = { onItemSelected(index) },
                focusRequester = focusRequester.takeIf {
                    index == focusIndex
                },
            )
        }
    }
}

@Composable
private fun AdvancedSettingsPage(
    bufferPresets: List<BufferPresetUIState>,
    selectedBufferPresetIndex: Int,
    fastDnsEnabled: Boolean,
    focusRequester: FocusRequester,
    onBufferPresetSelected: (Int) -> Unit,
    onToggleFastDns: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        item(key = "buffer_header") {
            PlayerPanelSectionHeader(stringResource(R.string.player_panel_buffer))
        }
        itemsIndexed(bufferPresets, key = { index, _ -> "buffer_$index" }) { index, item ->
            PlayerPanelItem(
                text = item.label,
                selected = index == selectedBufferPresetIndex,
                onClick = { onBufferPresetSelected(index) },
                focusRequester = focusRequester.takeIf {
                    index == selectedBufferPresetIndex.coerceIn(bufferPresets.indices)
                },
            )
        }
        item(key = "network_header") {
            PlayerPanelSectionHeader(stringResource(R.string.player_panel_network))
        }
        item(key = "fast_dns") {
            PlayerPanelItem(
                text = stringResource(R.string.player_fast_dns),
                supportingText = stringResource(R.string.player_fast_dns_hint),
                trailingText = stringResource(
                    if (fastDnsEnabled) R.string.player_setting_enabled else R.string.player_setting_disabled,
                ),
                selected = fastDnsEnabled,
                onClick = onToggleFastDns,
                focusRequester = focusRequester.takeIf { bufferPresets.isEmpty() },
            )
        }
    }
}

@Composable
private fun videoSettingsPageTitle(page: VideoSettingsPage): String {
    return stringResource(
        when (page) {
            VideoSettingsPage.ROOT -> R.string.player_video_settings_title
            VideoSettingsPage.QUALITY -> R.string.player_panel_quality
            VideoSettingsPage.SPEED -> R.string.player_panel_speed
            VideoSettingsPage.ASPECT_RATIO -> R.string.player_panel_aspect_ratio
            VideoSettingsPage.ADVANCED -> R.string.player_panel_advanced
        }
    )
}
