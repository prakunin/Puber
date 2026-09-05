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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.DeviceSettingType
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
import com.kino.puber.domain.model.TabType

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
        // The section has to be the open one: the list is the picker for it, so asking for focus
        // on any other row enters the group and its onEnter hands focus back to the open section.
        // Walking the panel is what this test is about; switching sections has its own test.
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus()
        playback.press(Key.DirectionRight)

        pressDownUntil(rowWithText(context.getString(R.string.settings_skip_credits)))

        focusedItem(context.getString(R.string.settings_skip_credits)).assertIsFocused()
    }

    /**
     * Everything below the update rows in General is information — the device card and the TMDB
     * attribution. A lazy column on the television scrolls only when focus moves into an item, so
     * unless those blocks can hold focus themselves the bottom of the section stays off screen no
     * matter what the user presses.
     */
    @Test
    fun generalSectionScrollsByFocusThroughItsInformationToTheTmdbAttribution() {
        setSuccessContent()
        val general = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.General.name))
        general.requestFocus().press(Key.DirectionRight)

        pressDownUntil(hasTestTag(SettingsTestTags.AboutDevice))
        composeRule.onNodeWithTag(SettingsTestTags.AboutDevice).assertIsFocused()

        focusedNode().press(Key.DirectionDown)

        val attribution = composeRule.onNodeWithTag(SettingsTestTags.TmdbAttribution)
        attribution.assertIsFocused()
        // Bounds come back already clipped to the panel, so they alone cannot tell a block that
        // fits from one cut off at the fold. The block's own height is what the clipped bounds
        // have to match for all of it to be on screen.
        val node = attribution.fetchSemanticsNode()
        assertEquals(
            "the attribution is ${node.size.height}px tall but only ${node.boundsInRoot.height}px show",
            node.size.height.toFloat(),
            node.boundsInRoot.height,
            1f,
        )
        val panel = composeRule.onNodeWithTag(SettingsTestTags.Content).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "the attribution at ${node.boundsInRoot} is outside the panel at $panel",
            node.boundsInRoot.top >= panel.top - 1f && node.boundsInRoot.bottom <= panel.bottom + 1f,
        )
    }

    @Test
    fun leftFromTheTmdbAttributionReturnsToTheGeneralSection() {
        setSuccessContent()
        val general = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.General.name))
        general.requestFocus().press(Key.DirectionRight)
        pressDownUntil(hasTestTag(SettingsTestTags.TmdbAttribution))

        composeRule.onNodeWithTag(SettingsTestTags.TmdbAttribution)
            .assertIsFocused()
            .press(Key.DirectionLeft)

        general.assertIsFocused()
    }

    @Test
    fun reEnteringTheSamePanelLandsOnItsFirstSettingAgain() {
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus().press(Key.DirectionRight)
        pressDownUntil(rowWithText(context.getString(R.string.settings_skip_credits)))
        focusedItem(context.getString(R.string.settings_skip_credits)).press(Key.DirectionLeft)

        playback.press(Key.DirectionRight)

        focusedItem(context.getString(R.string.settings_prefer_surround_audio)).assertIsFocused()
    }

    @Test
    fun anotherSectionOpensOnItsFirstSettingRatherThanWhereTheLastOneStopped() {
        setSuccessContent(initialSection = SettingsSection.Playback)
        val playback = composeRule.onNodeWithTag(SettingsTestTags.section(SettingsSection.Playback.name))
        playback.requestFocus().press(Key.DirectionRight)
        pressDownUntil(rowWithText(context.getString(R.string.settings_skip_credits)))
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
        pressDownUntil(rowWithText(context.getString(R.string.settings_skip_credits)))

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
        // The panel is entered on its top row, the mirror one, so the server-location list is the
        // next row down; the speed test sits below it.
        pressDownUntil(rowWithText(context.getString(R.string.device_setting_server_location)))
        focusedNode().press(Key.Enter)

        focusedItem("Automatic").assertIsFocused().press(Key.Back)
        composeRule.waitForIdle()

        // "Automatic" also names the collapsed row's current value, so the unselected option is
        // what tells the list apart from the row that opens it.
        composeRule.onNodeWithText("Europe").assertDoesNotExist()
        focusedItem(context.getString(R.string.device_setting_server_location)).assertIsFocused()
    }

    @Test
    fun leftFromExpandedNavigationChoiceReturnsToNavigationSection() {
        setSuccessContent(
            state = successState().copy(
                startupTab = TabType.Home,
                startupTabOptions = listOf(TabType.Home, TabType.Movies),
            ),
            initialSection = SettingsSection.Navigation,
        )
        val navigation = composeRule.onNodeWithTag(
            SettingsTestTags.section(SettingsSection.Navigation.name)
        )

        navigation.requestFocus().press(Key.DirectionRight)
        focusedItem(context.getString(R.string.settings_startup_tab)).press(Key.Enter)
        focusedItem(context.getString(TabType.Home.title))
            .assertIsFocused()
            .press(Key.DirectionLeft)

        navigation.assertIsFocused()
    }

    @Test
    fun startupTabOpensAsChoiceListAndFocusesCurrentValue() {
        setSuccessContent(
            state = successState().copy(
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

        // The row's disabled semantics sit on the clickable surface, which the merged tree returns
        // for the label directly. Walking up from the unmerged label instead stops on the layout
        // wrapper in between, which carries nothing to assert on.
        composeRule
            .onNodeWithText(context.getString(R.string.device_setting_support_ssl))
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
        // Entering the panel from the section list always lands on its top row.
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
    fun returningFromSpeedTest_restoresFocusToTheSpeedTestRow() {
        val actions = mutableListOf<UIAction>()
        setSuccessContent(
            state = successState(settings = listOf(serverSetting())),
            restoreNetworkDiagnosticsFocus = true,
            onAction = actions::add,
        )
        val title = context.getString(R.string.diagnostics_open_action)

        composeRule.waitUntil(FocusTimeoutMillis) {
            composeRule.onAllNodes(
                isFocused() and hasAnyDescendant(hasText(title)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }

        focusedItem(title).assertIsFocused()
        assertTrue(DeviceSettingsActions.NetworkDiagnosticsFocusRestored in actions)
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
        restoreNetworkDiagnosticsFocus: Boolean = false,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme {
                DeviceSettingsContent(
                    state = state,
                    apiDomain = apiDomain(),
                    onAction = onAction,
                    initialSection = initialSection,
                    restoreNetworkDiagnosticsFocus = restoreNetworkDiagnosticsFocus,
                )
            }
        }
    }

    private fun focusedItem(text: String) = composeRule.onNode(
        isFocused() and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    )

    private fun focusedNode() = composeRule.onNode(isFocused(), useUnmergedTree = true)

    /**
     * Walks the open panel down until the wanted row holds focus.
     *
     * Sections grow: a count of presses written against the list as it stood keeps passing for a
     * while after a setting is inserted, then quietly starts asserting about whatever row the old
     * count now lands on. Pressing until the row is reached says what the test means, and the
     * assertion that follows still fails plainly if the row is unreachable.
     */
    private fun pressDownUntil(matcher: SemanticsMatcher, maxPresses: Int = 12) {
        repeat(maxPresses) {
            if (composeRule.onAllNodes(isFocused() and matcher, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                return
            }
            focusedNode().press(Key.DirectionDown)
        }
        throw AssertionError(
            "no row matched after $maxPresses presses; focus stopped on " +
                composeRule.onNode(isFocused(), useUnmergedTree = true).printToString(maxDepth = 3)
        )
    }

    private fun rowWithText(text: String) = hasAnyDescendant(hasText(text))

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
