package com.kino.puber.ui.feature.main.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performKeyInput
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Heart
import com.kino.puber.core.ui.uikit.component.drawer.DrawerState
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.ModalNavigationDrawer
import com.kino.puber.core.ui.uikit.component.drawer.rememberDrawerState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class MainSideMenuFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun movingDownTwice_keepsFocusInOpenDrawer_untilFocusedItemIsClicked() {
        lateinit var drawerState: DrawerState
        var selectedTabFromAction = TabType.Favourites

        composeRule.setContent {
            PuberTheme {
                var selectedTab by remember { mutableStateOf(TabType.Favourites) }
                drawerState = rememberDrawerState(DrawerValue.Open)
                val contentFocusRequester = remember { FocusRequester() }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        MainSideMenuContent(
                            state = MainViewState(
                                tabs = testTabs(selectedTab),
                                selectedTab = selectedTab,
                            ),
                            drawerState = drawerState,
                            mainContentFocus = contentFocusRequester,
                            onAction = { action ->
                                if (action is CommonAction.ItemSelected<*>) {
                                    selectedTab = (action.item as MainTab).type
                                    selectedTabFromAction = selectedTab
                                }
                            },
                        )
                    },
                    content = {
                        Box(
                            Modifier
                                .focusRequester(contentFocusRequester)
                                .focusable(),
                        )
                    },
                )
            }
        }

        focusedItem("Favorites").press(Key.DirectionDown)
        focusedItem("History").press(Key.DirectionDown)

        focusedItem("Movies").assertExists()
        composeRule.runOnIdle {
            assertEquals(DrawerValue.Open, drawerState.currentValue)
            assertEquals(TabType.Favourites, selectedTabFromAction)
        }

        focusedItem("Movies").press(Key.Enter)

        composeRule.runOnIdle {
            assertEquals(TabType.Movies, selectedTabFromAction)
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

private fun testTabs(selectedTab: TabType): List<MainTab> = listOf(
    MainTab(
        type = TabType.Favourites,
        label = "Favorites",
        icon = PhosphorIcons.Duotone.Heart,
        isSelected = selectedTab == TabType.Favourites,
    ),
    MainTab(
        type = TabType.History,
        label = "History",
        icon = PhosphorIcons.Duotone.ClockCounterClockwise,
        isSelected = selectedTab == TabType.History,
    ),
    MainTab(
        type = TabType.Movies,
        label = "Movies",
        icon = PhosphorIcons.Duotone.FilmSlate,
        isSelected = selectedTab == TabType.Movies,
    ),
)
