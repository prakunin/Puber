package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.House
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.domain.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class HomeSectionRemovalFocusTest {

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
        HomeRemovalProbeHost.reset()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { tabAppRouterHolder.dispose() }
        HomeRemovalProbeHost.reset()
        coroutineScope.cancel()
    }

    @Test
    fun removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 1,
            expectedId = 2,
        )
    }

    @Test
    fun removingFocusedMiddleCardFocusesTheRightNeighborAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3, 4),
            removedId = 2,
            expectedId = 3,
        )
    }

    @Test
    fun removingFocusedLastCardFocusesThePreviousCardAndNotSearch() {
        assertRetainedRowRemovalFocus(
            previousIds = listOf(1, 2, 3),
            removedId = 3,
            expectedId = 2,
        )
    }

    @Test
    fun removingTheFocusedSectionSelectsTheReplacementSection() {
        var state by mutableStateOf(
            homeState(
                sections = listOf(
                    section(HomeSectionType.ContinueWatching, "Focused"),
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = state,
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithText("Focused card")
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle {
            state = homeState(
                sections = listOf(
                    section(HomeSectionType.Fresh, "Replacement"),
                ),
            )
        }

        composeRule.onNodeWithText("Replacement card").assertIsFocused()
    }

    private fun assertRetainedRowRemovalFocus(
        previousIds: List<Int>,
        removedId: Int,
        expectedId: Int,
    ) {
        setHomeContentBesideChrome(
            homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds,
                    ),
                ),
            )
        )

        val chrome = chromeControl()
        chrome
            .assertExists()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()

        focusCard(previousIds = previousIds, targetId = removedId)
        chrome.assertIsNotFocused()

        composeRule.runOnIdle {
            HomeRemovalProbeHost.state = homeState(
                sections = listOf(
                    section(
                        type = HomeSectionType.ContinueWatching,
                        title = "Retained",
                        itemIds = previousIds.filterNot { it == removedId },
                    ),
                ),
            )
        }

        composeRule.onNodeWithText(cardTitle(expectedId)).assertIsFocused()
        chrome.assertIsNotFocused()
    }

    /**
     * Walks from the chrome into the row and along it, with the D-pad.
     *
     * The card is not asked for focus directly. A row remembers the card the user moved to, and
     * focus dropped onto a card from outside that bookkeeping is not remembered as the row's own -
     * so when that card is then removed the row has nothing to fall back to and focus escapes to
     * the chrome, which is the very thing this test exists to catch.
     */
    private fun focusCard(
        previousIds: List<Int>,
        targetId: Int,
    ) {
        val targetIndex = previousIds.indexOf(targetId)
        check(targetIndex >= 0)
        pressUntilFocused(Key.DirectionDown, cardTitle(previousIds.first()))
        repeat(targetIndex) { index ->
            pressUntilFocused(Key.DirectionRight, cardTitle(previousIds[index + 1]))
        }
    }

    /**
     * Presses until the wanted card holds focus. Focus placed by a semantics request leaves the
     * screen's hero claim unresolved, and the first press afterwards is spent settling it rather
     * than moving anywhere, so a single press cannot be counted on to arrive.
     */
    private fun pressUntilFocused(key: Key, title: String, maxPresses: Int = 6) {
        repeat(maxPresses) {
            if (composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return
            }
            composeRule.onNode(isFocused()).performKeyInput {
                keyDown(key)
                keyUp(key)
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    /**
     * Home beside a focusable control that does not belong to it. The control stands for whatever
     * chrome surrounds the screen — the side rail, in the app — and the point of it is that a row
     * losing a card must not pull focus away from something outside the rows.
     */
    private fun setHomeContentBesideChrome(state: HomeViewState) {
        HomeRemovalProbeHost.state = state
        composeRule.setContent {
            PuberTheme {
                Column {
                    Button(onClick = {}) {
                        Text(text = CHROME_LABEL)
                    }
                    HomeScreenContent(
                        state = HomeRemovalProbeHost.state,
                        onAction = {},
                        onHeroClick = {},
                        onCollectionClick = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitUntil {
            composeRule
                .onAllNodes(hasText("Retained card", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun chromeControl() = composeRule.onNodeWithText(CHROME_LABEL)

    private fun homeState(
        sections: List<HomeSectionState>,
    ) = HomeViewState.Content(sections = sections)

    private fun section(
        type: HomeSectionType,
        title: String,
        itemIds: List<Int> = listOf(title.hashCode()),
    ) = HomeSectionState(
        title = "$title section",
        type = type,
        items = itemIds.map { id ->
            VideoItemUIState(
                id = id,
                title = if (itemIds.size == 1 && id == title.hashCode()) {
                    "$title card"
                } else {
                    cardTitle(id)
                },
                imageUrl = "",
                bigImageUrl = "",
            )
        },
    )

    private companion object {
        const val CHROME_LABEL = "Chrome"

        fun cardTitle(id: Int) = "Retained card $id"
    }
}

private object HomeRemovalProbeHost {
    var state by mutableStateOf<HomeViewState>(HomeViewState.Content())

    fun reset() {
        state = HomeViewState.Content()
    }
}
