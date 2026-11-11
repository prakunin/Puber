package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Heart
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.ContentFocusHandoffEffect
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalContentFocusHandoff
import com.kino.puber.core.ui.uikit.component.drawer.ModalNavigationDrawer
import com.kino.puber.core.ui.uikit.component.drawer.rememberDrawerState
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.main.component.MainSideMenuContent
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.showall.model.ShowAllViewState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Show all hosted behind the real rail and handoff contract, the way `MainScreenComponent` and
 * `FlowComponent` wire them. Closing the rail with RIGHT must give focus back to the card the user
 * left, not to whatever the tab box's default focus search finds after the handoff's grace frames.
 */
class ShowAllRailCloseFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = (1..ItemCount).map { id ->
        VideoItemUIState(id = id, title = "Card $id", imageUrl = "", bigImageUrl = "")
    }

    @Test
    fun showAllRailCloseKeepsFocusOnTheLowerRowCard() {
        val host = setUpScreen()
        awaitAnyFocus()
        repeat(DeepRows) { press(Key.DirectionDown) }
        val title = focusedTitle()

        repeat(Repeats) { closeRailAndAssertFocusOn(host.drawerState, title) }
    }

    // The top-row case is a guard: it also passes on base. The scrolled case is the discriminating half.
    @Test
    fun showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling() {
        val host = setUpScreen()
        awaitAnyFocus()
        val topTitle = focusedTitle()
        repeat(Repeats) { closeRailAndAssertFocusOn(host.drawerState, topTitle) }

        repeat(ScrollRows) { press(Key.DirectionDown) }
        press(Key.DirectionUp)
        val scrolledTitle = focusedTitle()
        repeat(Repeats) { closeRailAndAssertFocusOn(host.drawerState, scrolledTitle) }
    }

    // Guard: also passes on base.
    @Test
    fun showAllRailCloseWithoutMemoryBehavesAsBase() {
        // Rail open from the start and the grid never focused: nothing is remembered.
        val host = setUpScreen(initial = DrawerValue.Open, autoFocus = false)
        awaitAnyFocus()

        press(Key.DirectionRight)
        composeRule.waitUntil(TimeoutMillis) { host.drawerState.currentValue == DrawerValue.Closed }
        composeRule.waitForIdle()

        assertEquals(items.first().title, focusedTitle())
    }

    @Test
    fun showAllHandoffResponseDoesNotStealFocusFromADialog() {
        val host = setUpScreen()
        awaitAnyFocus()
        repeat(DeepRows) { press(Key.DirectionDown) }
        closeRailAndAssertFocusOn(host.drawerState, focusedTitle())

        // A popup standing in for a dialog takes focus out of the grid; the answered handoff id
        // must not pull it back when the content turns inactive and active again.
        composeRule.runOnIdle { host.popupShown = true }
        composeRule.onNodeWithTag(PopupTag).requestFocus()
        composeRule.waitForIdle()
        flipContentActive(host)

        composeRule.onNodeWithTag(PopupTag).assertIsFocused()
    }

    // Smoke check of the `inGrid` path. Inside the grid the remembered card follows focus, so a
    // re-fired attempt would target the focused card and cannot be seen here. The once-per-id
    // latch is covered by showAllHandoffResponseDoesNotStealFocusFromADialog and unit T-U4.
    @Test
    fun showAllRecompositionAfterSettleDoesNotRefocus() {
        val host = setUpScreen()
        awaitAnyFocus()
        repeat(DeepRows) { press(Key.DirectionDown) }
        closeRailAndAssertFocusOn(host.drawerState, focusedTitle())

        press(Key.DirectionRight)
        val moved = focusedTitle()
        flipContentActive(host)

        assertEquals(moved, focusedTitle())
    }

    private fun flipContentActive(host: Host) {
        composeRule.runOnIdle { host.forcedInactive = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { host.forcedInactive = false }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SettleMillis)
        composeRule.waitForIdle()
    }

    private fun closeRailAndAssertFocusOn(drawerState: DrawerState, title: String) {
        press(Key.DirectionLeft)
        composeRule.waitUntil(TimeoutMillis) { drawerState.currentValue == DrawerValue.Open }
        press(Key.DirectionRight)
        composeRule.waitUntil(TimeoutMillis) { drawerState.currentValue == DrawerValue.Closed }
        composeRule.waitForIdle()
        assertEquals(title, focusedTitle())
    }

    private fun setUpScreen(
        initial: DrawerValue = DrawerValue.Closed,
        autoFocus: Boolean = true,
    ): Host {
        val host = Host()
        composeRule.setContent {
            PuberTheme {
                host.drawerState = rememberDrawerState(initial)
                CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides autoFocus) {
                    RailWithShowAll(host, items)
                }
            }
        }
        return host
    }

    private fun press(key: Key) {
        composeRule.onNode(isFocused()).performDirection(key)
        composeRule.waitForIdle()
    }

    private fun awaitAnyFocus() {
        composeRule.waitUntil(TimeoutMillis) {
            composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun focusedTitle(): String = items.map { it.title }.first { title ->
        composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun SemanticsNodeInteraction.performDirection(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }

    private companion object {
        const val ItemCount = 24
        const val DeepRows = 5
        const val Repeats = 3
        const val ScrollRows = 7
        const val SettleMillis = 500L
        const val TimeoutMillis = 12_000L
    }
}

private const val PopupTag = "popup"

/** Test-side switches read by [RailWithShowAll]. */
private class Host {
    lateinit var drawerState: DrawerState
    var forcedInactive by mutableStateOf(false)
    var popupShown by mutableStateOf(false)
}

@Composable
private fun RailWithShowAll(host: Host, items: List<VideoItemUIState>) {
    val drawerState = host.drawerState
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
                        tabs = listOf(
                            MainTab(
                                type = TabType.Favourites,
                                icon = PhosphorIcons.Duotone.Heart,
                                isSelected = true,
                            ),
                        ),
                        selectedTab = TabType.Favourites,
                    ),
                    drawerState = drawerState,
                    onAction = {},
                )
            },
            content = {
                CompositionLocalProvider(
                    LocalContentFocusActive provides (drawerState.currentValue != DrawerValue.Open && !host.forcedInactive),
                ) {
                    // The tab box of FlowComponent: the blind request of the handoff lands here.
                    Box(
                        Modifier
                            .focusRequester(contentFocusRequester)
                            .onFocusChanged { contentHasFocus[0] = it.hasFocus }
                            .focusRestorer()
                            .focusGroup(),
                    ) {
                        ShowAllScreenContent(ShowAllViewState.Content(items), onAction = {})
                        if (host.popupShown) {
                            Box(Modifier.size(1.dp).testTag(PopupTag).focusable())
                        }
                    }
                }
                ContentFocusHandoffEffect(
                    handoff = handoff,
                    restartKey = Unit,
                    contentFocusRequester = contentFocusRequester,
                    contentHasFocus = { contentHasFocus[0] },
                )
            },
        )
    }
}
