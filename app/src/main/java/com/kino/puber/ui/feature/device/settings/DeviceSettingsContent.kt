package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingsSection
import com.kino.puber.ui.feature.main.model.TabType

private val ScreenHorizontalPadding = 48.dp
private val ScreenVerticalPadding = 28.dp
private val NavigationWidth = 264.dp

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit = {},
    initialSection: SettingsSection = SettingsSection.General,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is DeviceSettingsState.Error -> ErrorView(
                error = state.error,
                onRetry = { onAction(CommonAction.RetryClicked) },
                onConfigureApiDomain = { onAction(DeviceSettingsActions.OpenApiDomainDialog) },
            )
            is DeviceSettingsState.Loading -> LoadingView()
            is DeviceSettingsState.Success -> DeviceSettingsPane(
                state = state,
                apiDomain = apiDomain,
                onAction = onAction,
                initialSection = initialSection,
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.settings_screen_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(
                horizontal = ScreenHorizontalPadding,
                vertical = ScreenVerticalPadding,
            ),
        )
        FullScreenProgressIndicator()
    }
}

@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit,
    onConfigureApiDomain: () -> Unit,
) {
    val retryFocusRequester = rememberFocusRequesterOnLaunch()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
    ) {
        Text(
            text = stringResource(R.string.settings_screen_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .focusRequester(retryFocusRequester)
                        .testTag(SettingsTestTags.ErrorRetry),
                ) {
                    Text(stringResource(R.string.device_settings_retry))
                }
                Button(onClick = onConfigureApiDomain) {
                    Text(stringResource(R.string.api_domain_open_action))
                }
            }
        }
    }
}

@Composable
private fun DeviceSettingsPane(
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    initialSection: SettingsSection,
) {
    var selectedSection by rememberSaveable { mutableStateOf(initialSection) }
    val sections = remember {
        SettingsSection.entries.filter { it != SettingsSection.Developer || BuildConfig.DEBUG }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenHorizontalPadding, vertical = ScreenVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier
                .width(NavigationWidth)
                .fillMaxHeight(),
        ) {
            Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.testTag(SettingsTestTags.ScreenTitle),
            )
            Spacer(modifier = Modifier.height(20.dp))
            SettingsNavigation(
                sections = sections,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        key(selectedSection) {
            SettingsSectionPanel(
                section = selectedSection,
                state = state,
                apiDomain = apiDomain,
                onAction = onAction,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SettingsNavigation(
    sections: List<SettingsSection>,
    selectedSection: SettingsSection,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocusRequester = rememberFocusRequesterOnLaunch()

    LazyColumn(
        modifier = modifier
            .focusRestorer()
            .focusGroup()
            .testTag(SettingsTestTags.Navigation),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(2.dp),
    ) {
        itemsIndexed(
            items = sections,
            key = { _, section -> section.name },
        ) { _, section ->
            val selected = section == selectedSection
            Surface(
                onClick = { onSectionSelected(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (selected) Modifier.focusRequester(initialFocusRequester) else Modifier
                    )
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onSectionSelected(section)
                    }
                    .semantics { this.selected = selected }
                    .testTag(SettingsTestTags.section(section.name)),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.surface,
                    pressedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    pressedContentColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionPanel(
    section: SettingsSection,
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(section.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag(SettingsTestTags.SectionTitle),
        )
        Text(
            text = sectionDescription(section),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                )
                .padding(top = 8.dp),
        ) {
            SettingsSectionContent(
                section = section,
                state = state,
                apiDomain = apiDomain,
                onAction = onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SettingsTestTags.Content),
            )
        }
    }
}

@Composable
private fun sectionDescription(section: SettingsSection): String = stringResource(
    when (section) {
        SettingsSection.General -> R.string.settings_general_description
        SettingsSection.Playback -> R.string.settings_playback_description
        SettingsSection.Content -> R.string.settings_content_description
        SettingsSection.Navigation -> R.string.settings_navigation_description
        SettingsSection.Network -> R.string.settings_network_description
        SettingsSection.Data -> R.string.settings_data_description
        SettingsSection.Developer -> R.string.settings_developer_description
    }
)

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier
            .focusRestorer()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (section) {
            SettingsSection.General -> generalItems(state, onAction)
            SettingsSection.Playback -> playbackItems(state, onAction)
            SettingsSection.Content -> contentItems(state, onAction)
            SettingsSection.Navigation -> navigationItems(state, onAction)
            SettingsSection.Network -> networkItems(state, apiDomain, onAction, listState)
            SettingsSection.Data -> watchHistoryItems(state, onAction)
            SettingsSection.Developer -> developerItems(state, onAction)
        }
    }
}

private fun LazyListScope.generalItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "auto-update") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_auto_update_check),
            description = stringResource(R.string.settings_auto_update_check_subtitle),
            checked = state.autoUpdateCheckEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleAutoUpdateCheck) },
        )
    }
    item(key = "about-heading") {
        SectionHeading(stringResource(R.string.settings_about_device_title))
    }
    item(key = "device") {
        DeviceInfoCard(device = state.device)
    }
}

private fun LazyListScope.playbackItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "surround") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_prefer_surround_audio),
            checked = state.preferSurroundAudio,
            onToggle = { onAction(DeviceSettingsActions.ToggleSurroundAudio) },
        )
    }
    item(key = "ok-play-pause") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_ok_toggles_play_pause),
            description = stringResource(R.string.settings_ok_toggles_play_pause_subtitle),
            checked = state.okTogglesPlayPause,
            onToggle = { onAction(DeviceSettingsActions.ToggleOkTogglesPlayPause) },
        )
    }
    item(key = "mark-watched") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_mark_watched_button),
            description = stringResource(R.string.settings_show_mark_watched_button_subtitle),
            checked = state.showMarkWatchedButton,
            onToggle = { onAction(DeviceSettingsActions.ToggleShowMarkWatchedButton) },
        )
    }
    item(key = "skip-heading") {
        SectionHeading(
            title = stringResource(R.string.settings_skip_segments_title),
            description = stringResource(R.string.settings_skip_segments_subtitle),
        )
    }
    item(key = "skip-intro") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_skip_intro),
            checked = state.skipIntroEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleSkipIntro) },
        )
    }
    item(key = "skip-recap") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_skip_recap),
            checked = state.skipRecapEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleSkipRecap) },
        )
    }
    item(key = "skip-credits") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_skip_credits),
            checked = state.skipCreditsEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleSkipCredits) },
        )
    }
}

private fun LazyListScope.contentItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "watched-indicators") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_watched_indicators),
            checked = state.watchedIndicatorsEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleWatchedIndicators) },
        )
    }
    item(key = "cartoons-tab") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_cartoons_tab),
            checked = state.showCartoonsTab,
            onToggle = { onAction(DeviceSettingsActions.ToggleCartoonsTab) },
        )
    }
    item(key = "anime-tab") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_anime_tab),
            checked = state.showAnimeTab,
            onToggle = { onAction(DeviceSettingsActions.ToggleAnimeTab) },
        )
    }
    item(key = "show-anime") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_show_anime),
            description = stringResource(R.string.settings_show_anime_description),
            checked = state.showAnime,
            onToggle = { onAction(DeviceSettingsActions.ToggleShowAnime) },
        )
    }
    item(key = "hide-watched") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_hide_watched),
            description = stringResource(R.string.settings_hide_watched_description),
            checked = state.hideWatched,
            onToggle = { onAction(DeviceSettingsActions.ToggleHideWatched) },
        )
    }
}

private fun LazyListScope.navigationItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "navigation-mode-heading") {
        SectionHeading(
            title = stringResource(R.string.settings_navigation_mode),
            description = stringResource(R.string.settings_navigation_restart_hint),
        )
    }
    item(key = "navigation-mode") {
        NavigationModeRadioGroup(
            currentMode = state.navigationMode,
            onModeSelected = { onAction(DeviceSettingsActions.ChangeNavigationMode(it)) },
        )
    }
    item(key = "startup-heading") {
        SectionHeading(stringResource(R.string.settings_startup_tab))
    }
    item(key = "startup-tab") {
        StartupTabRadioGroup(
            options = state.startupTabOptions,
            currentTab = state.startupTab,
            onTabSelected = { onAction(DeviceSettingsActions.ChangeStartupTab(it)) },
        )
    }
}

private fun LazyListScope.networkItems(
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    item(key = "api-domain") {
        SettingsListItem(
            headline = stringResource(R.string.api_domain_open_action),
            supportingText = stringResource(R.string.api_domain_settings_subtitle),
            trailingText = apiDomain.currentDomain,
            onClick = { onAction(DeviceSettingsActions.OpenApiDomainDialog) },
        )
    }
    item(key = "device-settings-heading") {
        SectionHeading(stringResource(R.string.device_settings_title))
    }
    itemsIndexed(
        items = state.settings.settingsList,
        key = { _, setting ->
            when (setting) {
                is DeviceSettingUIModel.TypeList -> setting.type.name
                is DeviceSettingUIModel.TypeValue -> setting.type.name
            }
        },
    ) { index, setting ->
        when (setting) {
            is DeviceSettingUIModel.TypeValue -> SettingSwitchItem(
                setting = setting,
                isSaving = state.savingToggleType == setting.type,
                onToggle = {
                    onAction(DeviceSettingsActions.ChangeSettingValue(setting.copy(value = !setting.value)))
                },
            )
            is DeviceSettingUIModel.TypeList -> SettingListItem(
                setting = setting,
                isExpanded = setting.type == state.expandedType,
                savingOptionId = if (setting.type == state.expandedType) state.savingOptionId else null,
                onToggleExpand = { onAction(DeviceSettingsActions.ToggleListExpand(setting)) },
                onOptionSelect = { onAction(DeviceSettingsActions.SelectOption(setting.type, it)) },
                listState = listState,
                lazyItemIndex = index + 2,
            )
        }
    }
}

private fun LazyListScope.watchHistoryItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "watch-history-heading") {
        SectionHeading(
            title = stringResource(R.string.settings_watch_index_title),
            description = stringResource(R.string.settings_watch_index_description),
        )
    }
    item(key = "watch-summary") {
        WatchHistorySummary(state)
    }
    item(key = "sync") {
        val status = when {
            state.watchIndex.isSyncing &&
                state.watchIndex.currentPage != null &&
                state.watchIndex.totalPages != null -> stringResource(
                R.string.settings_watch_index_progress,
                state.watchIndex.currentPage,
                state.watchIndex.totalPages,
            )
            state.watchIndex.isSyncing -> stringResource(R.string.settings_watch_index_syncing)
            state.watchIndex.fullHistoryWalkDone -> stringResource(R.string.settings_watch_index_complete)
            state.watchIndex.indexedItems > 0 -> stringResource(R.string.settings_watch_index_partial)
            else -> stringResource(R.string.settings_watch_index_not_built)
        }
        SettingsListItem(
            headline = stringResource(R.string.settings_watch_index_sync_action),
            trailingText = status,
            enabled = !state.watchIndex.isSyncing,
            onClick = { onAction(DeviceSettingsActions.SyncWatchIndex) },
        )
    }
}

@Composable
private fun WatchHistorySummary(state: DeviceSettingsState.Success) {
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
            value = state.watchIndex.fullyWatchedItems.toString(),
            modifier = Modifier.weight(1f),
        )
        InformationMetric(
            label = stringResource(R.string.settings_watch_index_local_total),
            value = state.watchIndex.indexedItems.toString(),
            modifier = Modifier.weight(1f),
        )
        state.watchIndex.totalHistoryItems?.let { total ->
            InformationMetric(
                label = stringResource(R.string.settings_watch_index_account_total),
                value = total.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun InformationMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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

private fun LazyListScope.developerItems(
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
private fun SectionHeading(
    title: String,
    description: String? = null,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
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
private fun NavigationModeRadioGroup(
    currentMode: NavigationMode,
    onModeSelected: (NavigationMode) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        NavigationMode.entries.forEach { mode ->
            SettingsListItem(
                headline = stringResource(
                    when (mode) {
                        NavigationMode.SideDrawer -> R.string.settings_navigation_drawer
                        NavigationMode.TopTabs -> R.string.settings_navigation_top_tabs
                    }
                ),
                selected = mode == currentMode,
                role = Role.RadioButton,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun StartupTabRadioGroup(
    options: List<TabType>,
    currentTab: TabType,
    onTabSelected: (TabType) -> Unit,
) {
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { tab ->
            SettingsListItem(
                headline = stringResource(tab.title),
                selected = tab == currentTab,
                role = Role.RadioButton,
                onClick = { onTabSelected(tab) },
            )
        }
    }
}

@Composable
private fun DeviceInfoCard(
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
        Text(
            text = stringResource(R.string.device_settings_name, device.title),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.device_settings_app_version, appVersionName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.device_settings_hardware, device.hardware),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.device_settings_software, device.software),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
