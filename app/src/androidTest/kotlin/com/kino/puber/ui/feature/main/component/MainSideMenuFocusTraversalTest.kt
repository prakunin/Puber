package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Heart
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoffEffect
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.ModalNavigationDrawer
import com.kino.puber.core.ui.uikit.component.drawer.rememberDrawerState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.domain.model.TabType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val ContentTag = "rail-content"
/** Comfortably past the handoff's own 5s give-up budget, so the test observes the outcome. */
private const val HandoffTimeoutMillis = 12_000L

internal class MainSideMenuFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Read from resources rather than written out: the rail resolves a tab's title at draw time,
    // so a literal here would only match in whatever language the device happens to be set to.
    private val favourites = tabTitle(R.string.main_tabs_favorites)
    private val history = tabTitle(R.string.main_tabs_history)
    private val movies = tabTitle(R.string.main_tabs_movies)

    @Test
    fun movingDownTwice_keepsFocusInOpenDrawer_untilFocusedItemIsClicked() {
        lateinit var drawerState: DrawerState
        var selectedTabFromAction = TabType.Favourites

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Open)
                RailScaffold(
                    drawerState = drawerState,
                    contentEverBecomesFocusable = true,
                    onTabSelected = { selectedTabFromAction = it },
                )
            }
        }

        focusedItem(favourites).press(Key.DirectionDown)
        focusedItem(history).press(Key.DirectionDown)

        focusedItem(movies).assertExists()
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Open, drawerState.currentValue)
            assertEquals(TabType.Favourites, selectedTabFromAction)
        }

        focusedItem(movies).press(Key.Enter)

        composeRule.waitUntil(HandoffTimeoutMillis) {
            drawerState.currentValue != DrawerValue.HandingOff
        }
        composeRule.runOnIdle {
            assertEquals(TabType.Movies, selectedTabFromAction)
            assertEquals(DrawerValue.Closed, drawerState.currentValue)
        }
        // The state alone would again be testing the projection. Focus has to actually be there.
        composeRule.onNodeWithTag(ContentTag).assertIsFocused()
    }

    @Test
    fun selectingATabWhoseContentNeverTakesFocus_reopensTheRail() {
        lateinit var drawerState: DrawerState

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Open)
                RailScaffold(
                    drawerState = drawerState,
                    contentEverBecomesFocusable = false,
                    onTabSelected = { },
                )
            }
        }

        focusedItem(favourites).press(Key.DirectionDown)
        focusedItem(history).press(Key.Enter)

        composeRule.waitUntil(HandoffTimeoutMillis) {
            drawerState.currentValue != DrawerValue.HandingOff
        }
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Open, drawerState.currentValue)
        }
    }

    @Test
    fun dismissingTheRailWhileTheContentAlreadyHoldsFocus_stillClosesIt() {
        lateinit var drawerState: DrawerState

        composeRule.setContent {
            PuberTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                RailScaffold(
                    drawerState = drawerState,
                    contentEverBecomesFocusable = true,
                    onTabSelected = { },
                    focusContentOnStart = true,
                )
            }
        }

        // The content is focused and the rail is closed. Opening it and dismissing it without ever
        // moving focus is the case where no focus change can arrive to confirm the handoff, so the
        // rail used to sit in HandingOff until the budget ran out and then reopen itself.
        composeRule.onNodeWithTag(ContentTag).assertIsFocused()
        composeRule.runOnIdle { drawerState.reveal() }
        composeRule.runOnIdle { drawerState.beginHandoff(expectsNewContent = false) }

        composeRule.waitUntil(HandoffTimeoutMillis) {
            drawerState.currentValue != DrawerValue.HandingOff
        }
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Closed, drawerState.currentValue)
        }
    }

    private fun focusedItem(label: String) = composeRule.onNode(
        isFocused() and hasAnyDescendant(hasText(label)),
        useUnmergedTree = true,
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.press(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }
}

/**
 * The rail wired to the real handoff contract, standing in for `MainScreenComponent`.
 *
 * @param contentEverBecomesFocusable `false` models an empty or errored tab — the case where the
 *   handoff must give up and hand the rail back rather than strand focus.
 */
@Composable
private fun RailScaffold(
    drawerState: DrawerState,
    contentEverBecomesFocusable: Boolean,
    onTabSelected: (TabType) -> Unit,
    focusContentOnStart: Boolean = false,
) {
    var selectedTab by remember { mutableStateOf(TabType.Favourites) }
    val contentFocusRequester = remember { FocusRequester() }
    val contentHasFocus = remember { booleanArrayOf(false) }
    val handoff = remember(drawerState, contentFocusRequester) {
        ContentFocusHandoff(drawerState, contentFocusRequester)
    }

    CompositionLocalProvider(LocalContentFocusHandoff provides handoff) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            handoff = handoff,
            drawerContent = {
                MainSideMenuContent(
                    state = MainViewState(
                        tabs = testTabs(selectedTab),
                        selectedTab = selectedTab,
                    ),
                    drawerState = drawerState,
                    onAction = { action ->
                        if (action is CommonAction.ItemSelected<*>) {
                            selectedTab = (action.item as MainTab).type
                            onTabSelected(selectedTab)
                        }
                    },
                )
            },
            content = {
                // Models a tab swap: tabRouter.openTab posts the new tab over a flow, so the
                // content has no focusable child for the first frames after a click. A content
                // focusable from frame one cannot make requestFocus() miss, which is why the
                // original version of this test passed against the broken behaviour.
                var contentReady by remember { mutableStateOf(contentEverBecomesFocusable) }
                LaunchedEffect(selectedTab) {
                    contentReady = false
                    repeat(3) { withFrameNanos { } }
                    contentReady = contentEverBecomesFocusable
                }
                Box(
                    Modifier
                        .focusRequester(contentFocusRequester)
                        .onFocusChanged { contentHasFocus[0] = it.hasFocus }
                        .focusGroup(),
                ) {
                    if (contentReady) {
                        Box(
                            Modifier
                                .testTag(ContentTag)
                                .focusable(),
                        )
                    }
                }
                if (focusContentOnStart) {
                    // Stands in for rememberFocusRequesterOnLaunch, which the real screen uses to
                    // put focus in the content before the user touches anything.
                    LaunchedEffect(contentReady) {
                        if (contentReady) runCatching { contentFocusRequester.requestFocus() }
                    }
                }
                ContentFocusHandoffEffect(
                    handoff = handoff,
                    restartKey = selectedTab,
                    contentFocusRequester = contentFocusRequester,
                    contentHasFocus = { contentHasFocus[0] },
                )
            },
        )
    }
}

private fun tabTitle(resId: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

private fun testTabs(selectedTab: TabType): List<MainTab> = listOf(
    MainTab(
        type = TabType.Favourites,
        icon = PhosphorIcons.Duotone.Heart,
        isSelected = selectedTab == TabType.Favourites,
    ),
    MainTab(
        type = TabType.History,
        icon = PhosphorIcons.Duotone.ClockCounterClockwise,
        isSelected = selectedTab == TabType.History,
    ),
    MainTab(
        type = TabType.Movies,
        icon = PhosphorIcons.Duotone.FilmSlate,
        isSelected = selectedTab == TabType.Movies,
    ),
)
