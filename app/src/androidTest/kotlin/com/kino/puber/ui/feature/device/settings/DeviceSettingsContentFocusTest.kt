package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.device.DeviceSettingType
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingUIModel
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.settings.model.SettingOptionUi
import com.kino.puber.ui.feature.device.settings.model.SettingsSection
import com.kino.puber.ui.feature.device.settings.model.WatchIndexUiState
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.TabType

private const val FocusTimeoutMillis = 3_000L
private const val NeighbourTag = "settings-neighbour"

internal class DeviceSettingsContentFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun successInitiallyFocusesGeneralSection() {
        setSuccessContent()

        val tag = SettingsTestTags.section(SettingsSection.General.name)
        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().any { it.config.contains(
                androidx.compose.ui.semantics.SemanticsProperties.TestTag
            ) && it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] == tag }
        }

        composeRule.onNodeWithTag(tag).assertIsFocused()
    }

    @Test
    fun generalSectionOpensOnTheLanguageChoiceAndSelectingOneReportsIt() {
        val actions = mutableListOf<UIAction>()
        setSuccessContent(onAction = actions::add)
        val general = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.General.name))

        general.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.settings_app_language))
            .assertIsFocused()
            .press(Key.Enter)

        // Opens on the language in use, so a press of Down lands on the next one down the list
        // rather than wherever the expansion happened to leave focus.
        composeRule.onNodeWithText(context.getString(R.string.settings_app_language_english))
            .assertExists()
        focusedItem(context.getString(R.string.settings_app_language_system))
            .assertIsFocused()
            .press(Key.DirectionDown)
        focusedItem(context.getString(R.string.settings_app_language_russian))
            .assertIsFocused()
            .press(Key.Enter)

        composeRule.runOnIdle {
            assertTrue(
                actions.contains(DeviceSettingsActions.ChangeAppLanguage(AppLanguage.Russian))
            )
        }
    }

    @Test
    fun dpadFocusSelectsPlaybackSectionWithoutClickAndMovesIntoItsFirstSetting() {
        val actions = mutableListOf<UIAction>()
        setSuccessContent(onAction = actions::add)
        val general = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.General.name))
        general.requestFocus()

        general.press(Key.DirectionDown)
        composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
            .assertIsFocused()
            .press(Key.DirectionRight)

        focusedItem(context.getString(R.string.settings_prefer_surround_audio))
            .assertIsFocused()
            .press(Key.Enter)

        composeRule.runOnIdle {
            assertTrue(actions.contains(DeviceSettingsActions.ToggleSurroundAudio))
        }
    }

    @Test
    fun playbackSectionScrollsByFocusToLastSegmentSetting() {
        setSuccessContent()
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus()
        playback.press(Key.DirectionRight)

        repeat(5) { focusedNode().press(Key.DirectionDown) }

        focusedItem(context.getString(R.string.settings_skip_credits)).assertIsFocused()
    }

    @Test
    fun reEnteringTheSamePanelLandsOnItsFirstSettingAgain() {
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus().press(Key.DirectionRight)
        repeat(5) { focusedNode().press(Key.DirectionDown) }
        focusedItem(context.getString(R.string.settings_skip_credits)).press(Key.DirectionLeft)

        playback.press(Key.DirectionRight)

        focusedItem(context.getString(R.string.settings_prefer_surround_audio)).assertIsFocused()
    }

    @Test
    fun anotherSectionOpensOnItsFirstSettingRatherThanWhereTheLastOneStopped() {
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus().press(Key.DirectionRight)
        repeat(5) { focusedNode().press(Key.DirectionDown) }
        focusedItem(context.getString(R.string.settings_skip_credits)).press(Key.DirectionLeft)

        playback.press(Key.DirectionDown)
        composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Content.name))
            .assertIsFocused()
            .press(Key.DirectionRight)

        focusedItem(context.getString(R.string.settings_watched_indicators)).assertIsFocused()
    }

    @Test
    fun leftFromLowerSettingReturnsToTheActiveMenuSection() {
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus().press(Key.DirectionRight)
        repeat(5) { focusedNode().press(Key.DirectionDown) }

        focusedItem(context.getString(R.string.settings_skip_credits)).press(Key.DirectionLeft)

        playback.assertIsFocused()
    }

    @Test
    fun expandedNetworkListFocusesSelectedOptionAndBackCollapsesIt() {
        var state by mutableStateOf(successState(settings = listOf(serverSetting())))
        composeRule.setContent {
            PuberTheme {
                DeviceSettingsContent(
                    state = state,
                    apiDomain = apiDomain(),
                    initialSection = SettingsSection.Network,
                    onAction = { action ->
                        if (action is DeviceSettingsActions.ToggleListExpand) {
                            state = state.copy(
                                expandedType = if (state.expandedType == action.setting.type) {
                                    null
                                } else {
                                    action.setting.type
                                }
                            )
                        }
                    },
                )
            }
        }
        val network = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Network.name))
        network.requestFocus()
        network.press(Key.DirectionRight)
        focusedNode().press(Key.DirectionDown).press(Key.Enter)

        focusedItem("Automatic").assertIsFocused().press(Key.Back)
        composeRule.waitForIdle()

        // "Automatic" also names the collapsed row's current value, so the unselected option is
        // what tells the list apart from the row that opens it.
        composeRule.onNodeWithText("Europe").assertDoesNotExist()
        focusedItem(context.getString(R.string.device_setting_server_location)).assertIsFocused()
    }

    @Test
    fun navigationStyleOpensAsChoiceListAndBackCollapsesIt() {
        setSuccessContent(initialSection = SettingsSection.Navigation)
        val navigation = composeRule.onNodeWithTag(
            SettingsTestTags.section(SettingsSection.Navigation.name)
        )
        val sideMenu = context.getString(R.string.settings_navigation_drawer)
        val topTabs = context.getString(R.string.settings_navigation_top_tabs)

        navigation.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.settings_navigation_mode))
            .assertIsFocused()
            .press(Key.Enter)

        focusedItem(topTabs).assertIsFocused()
        composeRule.onNodeWithText(sideMenu).assertExists()

        focusedNode().press(Key.Back)

        composeRule.onNodeWithText(sideMenu).assertDoesNotExist()
        focusedItem(context.getString(R.string.settings_navigation_mode)).assertIsFocused()
    }

    @Test
    fun leftFromExpandedNavigationChoiceReturnsToNavigationSection() {
        setSuccessContent(initialSection = SettingsSection.Navigation)
        val navigation = composeRule.onNodeWithTag(
            SettingsTestTags.section(SettingsSection.Navigation.name)
        )

        navigation.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.settings_navigation_mode)).press(Key.Enter)
        focusedItem(context.getString(R.string.settings_navigation_top_tabs))
            .assertIsFocused()
            .press(Key.DirectionLeft)

        navigation.assertIsFocused()
    }

    @Test
    fun startupTabOpensAsChoiceListAndFocusesCurrentValue() {
        setSuccessContent(
            state = successState().copy(
                navigationMode = NavigationMode.TopTabs,
                startupTab = TabType.Home,
                startupTabOptions = listOf(TabType.Home, TabType.Movies),
            ),
            initialSection = SettingsSection.Navigation,
        )
        val navigation = composeRule.onNodeWithTag(
            SettingsTestTags.section(SettingsSection.Navigation.name)
        )
        val home = context.getString(R.string.main_tabs_home)
        val movies = context.getString(R.string.main_tabs_movies)

        navigation.requestFocus().press(Key.DirectionRight)
        focusedNode().press(Key.DirectionDown)
        focusedItem(context.getString(R.string.settings_startup_tab))
            .assertIsFocused()
            .press(Key.Enter)

        focusedItem(home).assertIsFocused()
        composeRule.onNodeWithText(movies).assertExists()

        focusedNode().press(Key.Back)

        composeRule.onNodeWithText(movies).assertDoesNotExist()
        focusedItem(context.getString(R.string.settings_startup_tab)).assertIsFocused()
    }

    @Test
    fun savingNetworkToggleIsVisiblyDisabled() {
        setSuccessContent(
            state = successState(settings = listOf(sslSetting())).copy(
                savingToggleType = DeviceSettingType.SUPPORT_SSL,
            ),
            initialSection = SettingsSection.Network,
        )

        composeRule
            .onNodeWithText(context.getString(R.string.device_setting_support_ssl), useUnmergedTree = true)
            .onParent()
            .assertIsNotEnabled()
    }

    @Test
    fun closingTheMirrorDialogPutsFocusBackInThePanel() {
        var dialogOpen by mutableStateOf(false)
        composeRule.setContent {
            PuberTheme {
                DeviceSettingsContent(
                    state = successState(settings = listOf(serverSetting())),
                    apiDomain = apiDomain(),
                    initialSection = SettingsSection.Network,
                    isApiDomainDialogOpen = dialogOpen,
                )
            }
        }
        val network = composeRule.onNodeWithTag(
            SettingsTestTags.section(SettingsSection.Network.name)
        )
        network.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.api_domain_open_action)).assertIsFocused()

        // The dialog is a sibling of this content, so the test can only play its effect on focus:
        // it takes focus away while open, and on close there is nothing left holding any.
        dialogOpen = true
        composeRule.waitForIdle()
        dialogOpen = false
        composeRule.waitForIdle()

        focusedItem(context.getString(R.string.api_domain_open_action)).assertIsFocused()
    }

    @Test
    fun errorInitiallyFocusesRetry() {
        composeRule.setContent {
            PuberTheme {
                DeviceSettingsContent(
                    state = DeviceSettingsState.Error("Network unavailable"),
                    apiDomain = apiDomain(),
                )
            }
        }

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(SettingsTestTags.ErrorRetry).assertIsFocused()
    }

    @Test
    fun screenAndSectionTitlesShareTheSameTopPosition() {
        setSuccessContent()

        val screenTitle = composeRule.onNodeWithTag(SettingsTestTags.ScreenTitle).fetchSemanticsNode()
        val sectionTitle = composeRule.onNodeWithTag(SettingsTestTags.SectionTitle).fetchSemanticsNode()

        assertEquals(screenTitle.boundsInRoot.top, sectionTitle.boundsInRoot.top, 1f)
    }

    @Test
    fun dataSectionSkipsInformationBlockWhenMovingFocusRight() {
        setSuccessContent(initialSection = SettingsSection.Data)
        val dataSection = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Data.name))
        dataSection.requestFocus().press(Key.DirectionRight)

        focusedItem(context.getString(R.string.settings_watch_index_rebuild_action)).assertIsFocused()
    }

    @Test
    fun syncingDataActionKeepsFocusWithoutDispatchingAnotherSync() {
        val actions = mutableListOf<UIAction>()
        setSuccessContent(
            state = successState().copy(
                watchIndex = WatchIndexUiState(
                    isSyncing = true,
                    currentPage = 13,
                    totalPages = 247,
                ),
            ),
            initialSection = SettingsSection.Data,
            onAction = actions::add,
        )
        val dataSection = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Data.name))

        dataSection.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.settings_watch_index_rebuild_action))
            .assertIsFocused()
            .press(Key.Enter)

        composeRule.runOnIdle {
            assertTrue(actions.isEmpty())
        }
        focusedNode().press(Key.DirectionLeft)
        dataSection.assertIsFocused()
    }

    /**
     * Entering the section list from the outside lands on the section that is actually open.
     *
     * The neighbour sits at the bottom, where the rail's own Settings entry sits, so a plain
     * directional search picks the section nearest to it rather than the selected one.
     */
    @Test
    fun enteringSectionListFromOutsideLandsOnTheSelectedSection() {
        setContentBesideNeighbour(
            initialSection = SettingsSection.Navigation,
            neighbourAlignment = Alignment.BottomStart,
        )
        composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Navigation.name))
            .requestFocus()

        composeRule.onNodeWithTag(NeighbourTag).requestFocus().press(Key.DirectionRight)

        composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Navigation.name))
            .assertIsFocused()
    }

    private fun setContentBesideNeighbour(
        initialSection: SettingsSection,
        neighbourAlignment: Alignment,
    ) {
        composeRule.setContent {
            PuberTheme {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxHeight().width(48.dp)) {
                        Box(
                            modifier = Modifier
                                .align(neighbourAlignment)
                                .height(48.dp)
                                .width(48.dp)
                                .testTag(NeighbourTag)
                                .focusable(),
                        )
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        DeviceSettingsContent(
                            state = successState(),
                            apiDomain = apiDomain(),
                            initialSection = initialSection,
                        )
                    }
                }
            }
        }
    }

    private fun setSuccessContent(
        state: DeviceSettingsState.Success = successState(),
        initialSection: SettingsSection = SettingsSection.General,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme {
                DeviceSettingsContent(
                    state = state,
                    apiDomain = apiDomain(),
                    onAction = onAction,
                    initialSection = initialSection,
                )
            }
        }
    }

    private fun focusedItem(text: String) = composeRule.onNode(
        isFocused() and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    )

    private fun focusedNode() = composeRule.onNode(isFocused(), useUnmergedTree = true)

    private fun SemanticsNodeInteraction.requestFocus(): SemanticsNodeInteraction {
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        return this
    }

    private fun SemanticsNodeInteraction.press(key: Key): SemanticsNodeInteraction {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
        return this
    }
}

private fun successState(
    settings: List<DeviceSettingUIModel> = emptyList(),
) = DeviceSettingsState.Success(
    settings = DeviceSettingsListUi(settings),
    device = DeviceUi(
        title = "Living room TV",
        hardware = "Google TV",
        software = "Android 14",
    ),
)

private fun apiDomain() = ApiDomainDialogState(
    currentDomain = "example.test",
    customDomain = null,
)

private fun serverSetting() = DeviceSettingUIModel.TypeList(
    type = DeviceSettingType.SERVER_LOCATION,
    values = listOf(
        SettingOptionUi(
            id = 1,
            label = "Automatic",
            description = "Selects the closest available server",
            selected = true,
        ),
        SettingOptionUi(
            id = 2,
            label = "Europe",
            description = "Use the European server",
            selected = false,
        ),
    ),
)

private fun sslSetting() = DeviceSettingUIModel.TypeValue(
    type = DeviceSettingType.SUPPORT_SSL,
    value = true,
)
