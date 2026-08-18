package com.kino.puber.ui.feature.main.toptabs

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performKeyInput
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.House
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.history.component.HISTORY_CARD_TEST_TAG_PREFIX
import com.kino.puber.ui.feature.history.component.HistoryScreenContent
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

// Read from resources rather than written out: the tab bar resolves a tab's title at draw time,
// so a literal here would only match in whatever language the device happens to be set to.
private val HOME_TAB = tabTitle(R.string.main_tabs_home)
private val MOVIES_TAB = tabTitle(R.string.main_tabs_movies)
private val HISTORY_TAB = tabTitle(R.string.main_tabs_history)

private fun tabTitle(resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
private const val SETTINGS = "Settings"

internal class TopTabMainContentHistoryFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var coroutineScope: CoroutineScope
    private lateinit var tabRouter: TabRouter
    private lateinit var tabAppRouterHolder: TabAppRouterHolder

    @Before
    fun setUp() {
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        tabRouter = TabRouter(coroutineScope)
        tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        TopTabHistoryProbeHost.bind(tabRouter, tabAppRouterHolder)
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        TopTabHistoryProbeHost.clear()
        coroutineScope.cancel()
    }

    @Test
    fun retainedHistoryFocusStaysWithinTheActiveTopTabsRegion() {
        composeRule.setContent {
            PuberTheme {
                FlowComponent(
                    scopeName = "TopTabMainContentHistoryFocusTest",
                    screen = TopTabHistoryProbeHostScreen,
                    moduleFactory = { scopeId, _ ->
                        module {
                            scope(named(scopeId)) {
                                scoped<AppLauncher> { NoOpAppLauncher }
                                scoped<Screens> { ScreensImpl }
                            }
                        }
                    },
                )
            }
        }
        composeRule.runOnIdle {
            tabRouter.openTab(
                PuberTab(
                    screen = RetainedHistoryProbeScreen,
                    tag = TabType.History,
                ),
            )
        }

        val historyCard = composeRule.onNode(historyCardMatcher, useUnmergedTree = true)
        historyCard.assertExists()
        composeRule.waitUntil {
            historyCard.fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Focused) == false
        }
        composeRule.waitForFocusedControlWithText(HISTORY_TAB)
        composeRule
            .focusedControlWithText(HISTORY_TAB)
            .assertIsFocused()
            .performDirection(Key.DirectionLeft)
        composeRule.focusedControlWithText(MOVIES_TAB).assertIsFocused()
        historyCard.assertIsNotFocused()

        composeRule
            .focusedControlWithText(MOVIES_TAB)
            .performDirection(Key.DirectionRight)
        composeRule.focusedControlWithText(HISTORY_TAB).assertIsFocused()
        historyCard.assertIsNotFocused()

        composeRule
            .focusedControlWithText(HISTORY_TAB)
            .performDirection(Key.DirectionRight)
        composeRule.focusedSettingsControl().assertIsFocused()
        historyCard.assertIsNotFocused()

        composeRule
            .focusedSettingsControl()
            .performDirection(Key.DirectionLeft)
        composeRule
            .focusedControlWithText(HISTORY_TAB)
            .assertIsFocused()
            .performDirection(Key.DirectionDown)
        composeRule.waitUntil {
            historyCard.fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        historyCard.assertIsFocused()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.focusedControlWithText(
        text: String,
    ) = onNode(
        matcher = isFocused() and hasAnyDescendant(hasText(text)),
        useUnmergedTree = true,
    )

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitForFocusedControlWithText(
        text: String,
    ) {
        waitUntil {
            onAllNodes(
                matcher = isFocused() and hasAnyDescendant(hasText(text)),
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.focusedSettingsControl() = onNode(
        matcher = isFocused() and hasAnyDescendant(hasContentDescription(SETTINGS)),
        useUnmergedTree = true,
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performDirection(
        key: Key,
    ) = performKeyInput {
        keyDown(key)
        keyUp(key)
    }

    private companion object {
        val historyCardMatcher = SemanticsMatcher("has opaque History card test tag") { node ->
            node.config
                .getOrNull(SemanticsProperties.TestTag)
                ?.startsWith(HISTORY_CARD_TEST_TAG_PREFIX) == true
        }
    }
}

private object TopTabHistoryProbeHost {
    var tabRouter: TabRouter? = null
    var tabAppRouterHolder: TabAppRouterHolder? = null

    fun bind(
        tabRouter: TabRouter,
        tabAppRouterHolder: TabAppRouterHolder,
    ) {
        this.tabRouter = tabRouter
        this.tabAppRouterHolder = tabAppRouterHolder
    }

    fun clear() {
        tabRouter = null
        tabAppRouterHolder = null
    }
}

@Parcelize
private data object TopTabHistoryProbeHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        TopTabMainContent(
            state = historyMainState(),
            onAction = {},
            tabRouter = requireNotNull(TopTabHistoryProbeHost.tabRouter),
            tabAppRouterHolder = requireNotNull(TopTabHistoryProbeHost.tabAppRouterHolder),
            onSearchClick = {},
            onSettingsClick = {},
        )
    }
}

@Parcelize
private data object RetainedHistoryProbeScreen : PuberScreen {
    @Composable
    override fun Content() {
        val item = historyItem()
        HistoryScreenContent(
            state = HistoryViewState.Content(
                items = listOf(item),
                focusKey = item.rowKey,
            ),
            presentation = HistoryPresentation.TopTabs,
            onAction = noOpAction,
        )
    }
}

private data object NoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private val noOpAction: (UIAction) -> Unit = {}

private fun historyMainState(): MainViewState {
    return MainViewState(
        tabs = listOf(
            mainTab(TabType.Home, PhosphorIcons.Duotone.House),
            mainTab(TabType.Movies, PhosphorIcons.Duotone.FilmSlate),
            mainTab(
                type = TabType.History,
                icon = PhosphorIcons.Duotone.ClockCounterClockwise,
                isSelected = true,
            ),
        ),
        selectedTab = TabType.History,
    )
}

private fun mainTab(
    type: TabType,
    icon: ImageVector,
    isSelected: Boolean = false,
): MainTab {
    return MainTab(
        type = type,
        icon = icon,
        isSelected = isSelected,
    )
}

private fun historyItem(): HistoryItemUIState {
    val semanticKey = HistorySemanticKey.Movie(
        itemId = 42,
        videoNumber = 1,
    )
    return HistoryItemUIState(
        itemId = semanticKey.itemId,
        deletionMediaId = 700,
        rowKey = HistoryRowKey.Media(semanticKey),
        semanticKey = semanticKey,
        videoNumber = semanticKey.videoNumber,
        seasonNumber = null,
        episodeNumber = null,
        progressPercent = 0.5f,
        isWatched = false,
        lastViewedAt = "2099-07-23T12:00:00Z",
        playbackTarget = HistoryPlaybackTarget.Movie(videoNumber = semanticKey.videoNumber),
        card = VideoItemUIState(
            id = semanticKey.itemId,
            title = "Synthetic retained History movie",
            imageUrl = "",
            bigImageUrl = "",
            wideImageUrl = "",
            showTitle = true,
            progressPercent = 0.5f,
        ),
    )
}
