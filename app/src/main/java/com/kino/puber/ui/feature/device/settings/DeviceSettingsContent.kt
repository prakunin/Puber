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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.modifier.FOCUS_ON_LAUNCH_DELAY_MILLIS
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.SettingsChoiceOption
import com.kino.puber.ui.feature.device.settings.model.SettingsSection
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.delay

private val ScreenHorizontalPadding = 48.dp
private val ScreenVerticalPadding = 28.dp
private val NavigationWidth = 264.dp
private const val ReturnFocusDelayMillis = FOCUS_ON_LAUNCH_DELAY_MILLIS * 2

private enum class NavigationChoice {
    Mode,
    StartupTab,
}

@Composable
internal fun DeviceSettingsContent(
    state: DeviceSettingsState,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit = {},
    initialSection: SettingsSection = SettingsSection.General,
    isApiDomainDialogOpen: Boolean = false,
    restoreNetworkDiagnosticsFocus: Boolean = false,
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
                isApiDomainDialogOpen = isApiDomainDialogOpen,
                restoreNetworkDiagnosticsFocus = restoreNetworkDiagnosticsFocus,
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
    isApiDomainDialogOpen: Boolean,
    restoreNetworkDiagnosticsFocus: Boolean,
) {
    var selectedSection by rememberSaveable { mutableStateOf(initialSection) }
    val sections = remember {
        SettingsSection.entries.filter { it != SettingsSection.Developer || BuildConfig.DEBUG }
    }
    val initialFocusRequester = rememberFocusRequesterOnLaunch()
    val sectionFocusRequesters = remember(sections, initialSection, initialFocusRequester) {
        sections.associateWith { section ->
            if (section == initialSection) initialFocusRequester else FocusRequester()
        }
    }
    // Rebuilt with the panel it points into, which is keyed on the section below: a requester kept
    // across sections would still name a list that has been thrown away.
    val panelFocusRequester = remember(selectedSection) { FocusRequester() }
    val diagnosticsFocusRequester = remember { FocusRequester() }
    LaunchedEffect(restoreNetworkDiagnosticsFocus) {
        if (!restoreNetworkDiagnosticsFocus) return@LaunchedEffect
        selectedSection = SettingsSection.Network
        delay(ReturnFocusDelayMillis)
        diagnosticsFocusRequester.requestFocus()
        onAction(DeviceSettingsActions.NetworkDiagnosticsFocusRestored)
    }
    // The dialog takes focus into itself, and closing it leaves nothing holding focus at all: the
    // search that follows starts from the root and settles on the navigation rail, so dismissing
    // the dialog looks like the menu opening by itself. The panel takes its focus back, at the top
    // — which is where the row that opens the dialog sits anyway.
    var dialogWasOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isApiDomainDialogOpen) {
        if (!isApiDomainDialogOpen && dialogWasOpen) panelFocusRequester.requestFocus()
        dialogWasOpen = isApiDomainDialogOpen
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
                sectionFocusRequesters = sectionFocusRequesters,
                panelFocusRequester = panelFocusRequester,
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
                leftFocusRequester = sectionFocusRequesters.getValue(selectedSection),
                panelFocusRequester = panelFocusRequester,
                diagnosticsFocusRequester = diagnosticsFocusRequester,
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
    sectionFocusRequesters: Map<SettingsSection, FocusRequester>,
    panelFocusRequester: FocusRequester,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .focusProperties {
                // Entry from outside — from the navigation rail, say — is a directional focus
                // search, and geometry alone lands on whichever row happens to sit nearest the
                // arriving focus. The list is the picker for the open section, so coming back to
                // it belongs on that section rather than wherever the search points.
                onEnter = { sectionFocusRequesters.getValue(selectedSection).requestFocus() }
                // Leaving to the right is the way into the open section, and the same geometry
                // applies on the way out: a plain search lands on whichever setting happens to sit
                // level with the row being left, so walking down the sections walks down the
                // panel's rows too. Handing the move to the panel's own requester enters it at the
                // top instead, from every row and for every section.
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Right) {
                        panelFocusRequester.requestFocus()
                    }
                }
            }
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
                    .focusRequester(sectionFocusRequesters.getValue(section))
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) onSectionSelected(section)
                    }
                    .semantics { this.selected = selected }
                    .testTag(SettingsTestTags.section(section.name)),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
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
    leftFocusRequester: FocusRequester,
    panelFocusRequester: FocusRequester,
    diagnosticsFocusRequester: FocusRequester,
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
                leftFocusRequester = leftFocusRequester,
                panelFocusRequester = panelFocusRequester,
                diagnosticsFocusRequester = diagnosticsFocusRequester,
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
    leftFocusRequester: FocusRequester,
    panelFocusRequester: FocusRequester,
    diagnosticsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var expandedNavigationChoice by rememberSaveable(section) {
        mutableStateOf<NavigationChoice?>(null)
    }
    var languageChoiceExpanded by rememberSaveable(section) { mutableStateOf(false) }
    var panelHasFocus by remember { mutableStateOf(false) }
    // Arriving from the section list always lands on the setting at the top, whichever section it
    // is and whatever was touched there before. Restoring the previous row would mean the same
    // press of Right ends up somewhere different every time, and a row remembered from a section
    // the user has since left says nothing about the one they are looking at.
    //
    // Done on the way out rather than on the way in: focus entering the group resolves against the
    // items composed at that moment, so a list still scrolled down would hand focus to whatever sits
    // at its top edge before any correction here could run.
    LaunchedEffect(section, panelHasFocus) {
        if (!panelHasFocus) listState.scrollToItem(0)
    }
    CompositionLocalProvider(LocalSettingsLeftFocusRequester provides leftFocusRequester) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .onFocusChanged { panelHasFocus = it.hasFocus }
                .focusRequester(panelFocusRequester)
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (section) {
                SettingsSection.General -> generalItems(
                    state = state,
                    onAction = onAction,
                    leftFocusRequester = leftFocusRequester,
                    languageExpanded = languageChoiceExpanded,
                    onLanguageExpandedChange = { languageChoiceExpanded = it },
                )
                SettingsSection.Playback -> playbackItems(state, onAction)
                SettingsSection.Content -> contentItems(state, onAction)
                SettingsSection.Navigation -> navigationItems(
                    state = state,
                    onAction = onAction,
                    leftFocusRequester = leftFocusRequester,
                    expandedChoice = expandedNavigationChoice,
                    onExpandedChoiceChange = { expandedNavigationChoice = it },
                )
                SettingsSection.Network -> networkItems(
                    state,
                    apiDomain,
                    onAction,
                    leftFocusRequester,
                    diagnosticsFocusRequester,
                )
                SettingsSection.Data -> watchHistoryItems(state, onAction)
                SettingsSection.Developer -> developerItems(state, onAction)
            }
        }
    }
}

private fun LazyListScope.generalItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
    leftFocusRequester: FocusRequester,
    languageExpanded: Boolean,
    onLanguageExpandedChange: (Boolean) -> Unit,
) {
    item(key = "app-language") {
        SettingsChoiceItem(
            label = stringResource(R.string.settings_app_language),
            options = appLanguageOptions(state.appLanguage),
            isExpanded = languageExpanded,
            leftFocusRequester = leftFocusRequester,
            onToggleExpand = { onLanguageExpandedChange(!languageExpanded) },
            onOptionSelect = { key ->
                onLanguageExpandedChange(false)
                onAction(DeviceSettingsActions.ChangeAppLanguage(AppLanguage.valueOf(key)))
            },
        )
    }
    item(key = "auto-update") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_auto_update_check),
            description = stringResource(R.string.settings_auto_update_check_subtitle),
            checked = state.autoUpdateCheckEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleAutoUpdateCheck) },
        )
    }
    item(key = "check-update-now") {
        SettingsListItem(
            headline = stringResource(R.string.settings_check_for_updates_now),
            supportingText = stringResource(R.string.settings_check_for_updates_now_subtitle),
            role = Role.Button,
            onClick = { onAction(DeviceSettingsActions.CheckForUpdatesNow) },
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
    item(key = "auto-trailer") {
        SettingsToggleItem(
            label = stringResource(R.string.settings_auto_trailer),
            description = stringResource(R.string.settings_auto_trailer_description),
            checked = state.autoTrailerEnabled,
            onToggle = { onAction(DeviceSettingsActions.ToggleAutoTrailer) },
        )
    }
}

private fun LazyListScope.navigationItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
    leftFocusRequester: FocusRequester,
    expandedChoice: NavigationChoice?,
    onExpandedChoiceChange: (NavigationChoice?) -> Unit,
) {
    item(key = "navigation-mode") {
        SettingsChoiceItem(
            label = stringResource(R.string.settings_navigation_mode),
            description = stringResource(R.string.settings_navigation_restart_hint),
            options = navigationModeOptions(state.navigationMode),
            isExpanded = expandedChoice == NavigationChoice.Mode,
            leftFocusRequester = leftFocusRequester,
            onToggleExpand = {
                onExpandedChoiceChange(
                    if (expandedChoice == NavigationChoice.Mode) null else NavigationChoice.Mode
                )
            },
            onOptionSelect = { key ->
                onExpandedChoiceChange(null)
                onAction(DeviceSettingsActions.ChangeNavigationMode(NavigationMode.valueOf(key)))
            },
        )
    }
    item(key = "startup-tab") {
        SettingsChoiceItem(
            label = stringResource(R.string.settings_startup_tab),
            options = startupTabOptions(state.startupTabOptions, state.startupTab),
            isExpanded = expandedChoice == NavigationChoice.StartupTab,
            leftFocusRequester = leftFocusRequester,
            onToggleExpand = {
                onExpandedChoiceChange(
                    if (expandedChoice == NavigationChoice.StartupTab) {
                        null
                    } else {
                        NavigationChoice.StartupTab
                    }
                )
            },
            onOptionSelect = { key ->
                onExpandedChoiceChange(null)
                onAction(DeviceSettingsActions.ChangeStartupTab(TabType.valueOf(key)))
            },
        )
    }
    menuSectionItems(state, onAction)
}

private fun LazyListScope.menuSectionItems(
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
) {
    item(key = "menu-sections-heading") {
        SectionHeading(
            title = stringResource(R.string.settings_menu_sections_title),
            description = stringResource(R.string.settings_menu_sections_description),
        )
    }
    items(
        items = state.menuSections,
        key = { section -> "menu-section-${section.tab.name}" },
    ) { section ->
        val isStartupTab = section.tab == state.startupTab
        SettingsToggleItem(
            label = stringResource(section.tab.title),
            description = stringResource(R.string.settings_menu_section_is_startup_tab)
                .takeIf { isStartupTab },
            checked = section.visible,
            // The startup tab has to stay reachable, so its section cannot be switched off. The
            // row keeps its ordinary look — it is switched on, and saying so matters more than
            // signalling that it is fixed; the line underneath carries the reason.
            readOnly = isStartupTab,
            onToggle = { onAction(DeviceSettingsActions.ToggleMenuSection(section.tab)) },
        )
    }
}

@Composable
private fun appLanguageOptions(currentLanguage: AppLanguage) = AppLanguage.entries.map { language ->
    SettingsChoiceOption(
        key = language.name,
        label = stringResource(
            when (language) {
                AppLanguage.System -> R.string.settings_app_language_system
                AppLanguage.Russian -> R.string.settings_app_language_russian
                AppLanguage.English -> R.string.settings_app_language_english
            }
        ),
        selected = language == currentLanguage,
    )
}

@Composable
private fun navigationModeOptions(currentMode: NavigationMode) = NavigationMode.entries.map { mode ->
    SettingsChoiceOption(
        key = mode.name,
        label = stringResource(
            when (mode) {
                NavigationMode.SideDrawer -> R.string.settings_navigation_drawer
                NavigationMode.TopTabs -> R.string.settings_navigation_top_tabs
            }
        ),
        selected = mode == currentMode,
    )
}

@Composable
private fun startupTabOptions(options: List<TabType>, currentTab: TabType) = options.map { tab ->
    SettingsChoiceOption(
        key = tab.name,
        label = stringResource(tab.title),
        selected = tab == currentTab,
    )
}

private fun LazyListScope.networkItems(
    state: DeviceSettingsState.Success,
    apiDomain: ApiDomainDialogState,
    onAction: (UIAction) -> Unit,
    leftFocusRequester: FocusRequester,
    diagnosticsFocusRequester: FocusRequester,
) {
    val listSettings = state.settings.settingsList.filterIsInstance<DeviceSettingUIModel.TypeList>()
    val serverLocation = listSettings.firstOrNull { it.type == DeviceSettingType.SERVER_LOCATION }
    item(key = "api-domain") {
        SettingsListItem(
            headline = stringResource(R.string.api_domain_open_action),
            supportingText = stringResource(R.string.api_domain_settings_subtitle),
            trailingText = apiDomain.currentDomain,
            onClick = { onAction(DeviceSettingsActions.OpenApiDomainDialog) },
        )
    }
    serverLocation?.let {
        serverLocationItem(it, state, onAction, leftFocusRequester)
    }
    item(key = "network-diagnostics") {
        SettingsListItem(
            headline = stringResource(R.string.diagnostics_open_action),
            modifier = Modifier.focusRequester(diagnosticsFocusRequester),
            supportingText = stringResource(R.string.diagnostics_settings_subtitle),
            role = Role.Button,
            onClick = { onAction(DeviceSettingsActions.OpenNetworkDiagnostics) },
        )
    }
    // Stream selection follows the speed test; device capability switches stay under their heading.
    items(
        items = listSettings.filter { it.type != DeviceSettingType.SERVER_LOCATION },
        key = { setting -> setting.type.name },
    ) { setting ->
        SettingListItem(
            setting = setting,
            isExpanded = setting.type == state.expandedType,
            savingOptionId = if (setting.type == state.expandedType) state.savingOptionId else null,
            leftFocusRequester = leftFocusRequester,
            onToggleExpand = { onAction(DeviceSettingsActions.ToggleListExpand(setting)) },
            onOptionSelect = { onAction(DeviceSettingsActions.SelectOption(setting.type, it)) },
        )
    }
    item(key = "device-settings-heading") {
        SectionHeading(stringResource(R.string.device_settings_title))
    }
    items(
        items = state.settings.settingsList.filterIsInstance<DeviceSettingUIModel.TypeValue>(),
        key = { setting -> setting.type.name },
    ) { setting ->
        SettingSwitchItem(
            setting = setting,
            isSaving = state.savingToggleType == setting.type,
            onToggle = {
                onAction(DeviceSettingsActions.ChangeSettingValue(setting.copy(value = !setting.value)))
            },
        )
    }
}

private fun LazyListScope.serverLocationItem(
    setting: DeviceSettingUIModel.TypeList,
    state: DeviceSettingsState.Success,
    onAction: (UIAction) -> Unit,
    leftFocusRequester: FocusRequester,
) = item(key = setting.type.name) {
    SettingListItem(
        setting = setting,
        isExpanded = setting.type == state.expandedType,
        savingOptionId = if (setting.type == state.expandedType) state.savingOptionId else null,
        leftFocusRequester = leftFocusRequester,
        onToggleExpand = { onAction(DeviceSettingsActions.ToggleListExpand(setting)) },
        onOptionSelect = { onAction(DeviceSettingsActions.SelectOption(setting.type, it)) },
    )
}
