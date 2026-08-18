package com.kino.puber.ui.feature.device.settings

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvSafeButton
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.WatchIndexUiState

internal fun LazyListScope.watchHistoryItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "watch-history-heading") {
        SectionHeading(
            title = stringResource(R.string.settings_watch_index_title),
            description = stringResource(R.string.settings_watch_index_description),
        )
    }
    item(key = "watch-summary") { WatchHistorySummary(state.watchIndex) }
    item(key = "watch-status") {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            Text(
                text = watchIndexStatus(state.watchIndex),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.settings_watch_index_last_sync,
                    lastSyncLabel(state.watchIndex.lastSyncAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "watch-actions") {
        TvSafeButton(
            text = stringResource(R.string.settings_watch_index_rebuild_action),
            enabled = !state.watchIndex.isSyncing,
            focusableWhenDisabled = true,
            primary = true,
            onClick = { onAction(DeviceSettingsActions.RebuildWatchIndex) },
        )
    }
}

@Composable
private fun watchIndexStatus(index: WatchIndexUiState): String {
    val percent = index.walkedPercent
    return when {
        index.isSyncing && percent != null ->
            stringResource(R.string.settings_watch_index_syncing_percent, percent)
        index.isSyncing -> stringResource(R.string.settings_watch_index_syncing)
        index.fullHistoryWalkDone -> stringResource(R.string.settings_watch_index_complete)
        index.indexedItems > 0 -> stringResource(R.string.settings_watch_index_partial)
        else -> stringResource(R.string.settings_watch_index_not_built)
    }
}

@Composable
private fun WatchHistorySummary(index: WatchIndexUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.WatchSummary)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        InformationMetric(
            label = stringResource(R.string.settings_watch_index_fully_watched),
            value = index.fullyWatchedItems.toString(),
            modifier = Modifier.weight(1f),
        )
        index.totalHistoryItems?.let { total ->
            InformationMetric(
                label = stringResource(R.string.settings_watch_index_history_entries),
                value = total.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun lastSyncLabel(lastSyncAt: Long?): String {
    if (lastSyncAt == null) return stringResource(R.string.settings_watch_index_never_synced)
    return DateUtils.getRelativeTimeSpanString(
        lastSyncAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}

@Composable
private fun InformationMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun LazyListScope.developerItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "debug-overlay") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_debug_overlay),
            checked = state.debugOverlayEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleDebugOverlay) },
        )
    }
}

@Composable
internal fun SectionHeading(title: String, description: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
internal fun DeviceInfoCard(
    device: DeviceUi,
    appVersionName: String = BuildConfig.VERSION_NAME,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.device_settings_name, device.title), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.device_settings_app_version, appVersionName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.device_settings_hardware, device.hardware),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.device_settings_software, device.software),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
