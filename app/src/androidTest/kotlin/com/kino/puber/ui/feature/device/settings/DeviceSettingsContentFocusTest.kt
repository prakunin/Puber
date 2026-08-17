package com.kino.puber.ui.feature.device.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val FocusTimeoutMillis = 3_000L

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

        composeRule.onNodeWithText("Automatic").assertDoesNotExist()
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
            .onNodeWithText("Use SSL", useUnmergedTree = true)
            .onParent()
            .assertIsNotEnabled()
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
    label = "Server",
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
    label = "Use SSL",
    value = true,
)
