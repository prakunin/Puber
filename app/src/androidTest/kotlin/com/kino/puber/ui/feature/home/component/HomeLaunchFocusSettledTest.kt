package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.home.model.HomeAction
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class HomeLaunchFocusSettledTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<UIAction>()

    @Test
    fun heroClaimSendsLaunchFocusSettledWithHeroFocused() {
        show(HomeViewState.Content(heroItems = listOf(heroItem()), sections = listOf(row()), heroSettled = true))
        composeRule.waitForIdle()

        assertEquals(listOf<UIAction>(HomeAction.LaunchFocusSettled), actions)
        composeRule.onNodeWithText(HERO_TITLE).assertIsFocused()
    }

    @Test
    fun emptyHeroSendsLaunchFocusSettledWithFirstRowFocused() {
        show(HomeViewState.Content(heroItems = emptyList(), sections = listOf(row()), heroSettled = true))
        composeRule.waitForIdle()

        assertEquals(listOf<UIAction>(HomeAction.LaunchFocusSettled), actions)
        composeRule.onNodeWithText(ITEM_TITLE).assertIsFocused()
    }

    @Test
    fun unsettledHeroSendsNothing() {
        show(HomeViewState.Content(heroItems = emptyList(), sections = listOf(row()), heroSettled = false))
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertTrue(actions.isEmpty())
    }

    private fun show(state: HomeViewState.Content) {
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize().focusGroup()) {
                    HomeScreenContent(
                        state = state,
                        onAction = { actions += it },
                        onHeroClick = {},
                        onCollectionClick = { _, _ -> },
                    )
                }
            }
        }
    }

    private fun row() = HomeSectionState(
        title = "Continue watching",
        type = HomeSectionType.ContinueWatching,
        items = listOf(VideoItemUIState(id = 1, title = ITEM_TITLE, imageUrl = "", bigImageUrl = "")),
    )

    private fun heroItem() = HeroItemState(
        id = 100,
        title = HERO_TITLE,
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "2026",
        genres = "Drama",
    )

    private companion object {
        const val HERO_TITLE = "Featured hero"
        const val ITEM_TITLE = "home-row-0-item-0"
    }
}
