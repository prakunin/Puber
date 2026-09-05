package com.kino.puber.ui.feature.home.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class HomeFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enteringHomeFocusesHeroBeforeTheFirstSection() {
        val contentFocus = FocusRequester()
        composeRule.setContent {
            PuberTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(contentFocus)
                        .focusGroup(),
                ) {
                    HomeScreenContent(
                        state = HomeViewState.Content(
                            heroItems = listOf(heroItem()),
                            sections = listOf(
                                HomeSectionState(
                                    title = "Continue watching",
                                    type = HomeSectionType.ContinueWatching,
                                    items = listOf(item(0, 0)),
                                ),
                            ),
                        ),
                        onAction = {},
                        onHeroClick = {},
                        onCollectionClick = { _, _ -> },
                    )
                }
            }
        }

        composeRule.runOnIdle { contentFocus.requestFocus() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(HERO_TITLE).assertIsFocused()
        composeRule.onNodeWithText(itemTitle(0, 0)).assertIsNotFocused()
    }

    @Test
    fun dpadFocusOnNonFallbackItemInNonTargetHomeRowRecordsActualIdentity() {
        var focusedItemId: Int? = null
        composeRule.setContent {
            PuberTheme {
                HomeSectionRow(
                    rowKey = "home_row",
                    items = listOf(item(0, 0), item(0, 1), item(0, 2)),
                    isTargetRow = false,
                    onSectionFocused = {},
                    onItemClick = {},
                    onItemContextMenu = null,
                    onItemFocused = { focusedItemId = it.id },
                    onRowEmpty = {},
                )
            }
        }

        requestFocus(itemTitle(row = 0, column = 0))
        composeRule.runOnIdle {
            focusedItemId = null
        }
        pressCurrent(Key.DirectionRight)

        composeRule.runOnIdle {
            assertEquals(1, focusedItemId)
        }
        composeRule.onNodeWithText(itemTitle(row = 0, column = 1)).assertIsFocused()
    }

    @Test
    fun downToEndRightThenUpRestoresHomeRowTargetsInsideViewport() {
        val sections = HOME_ROW_TYPES.mapIndexed { row, type ->
            HomeSectionState(
                title = "Home row $row",
                type = type,
                items = (0 until ITEM_COUNT).map { column -> item(row, column) },
            )
        }
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = HomeViewState.Content(sections = sections),
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        requestFocus(itemTitle(0, 0))
        seedStableTargets()

        val snapshots = buildList {
            repeat(HOME_ROW_TYPES.lastIndex) {
                add(pressCurrentAndCapture(Key.DirectionDown))
            }
            add(pressCurrentAndCapture(Key.DirectionRight))
            repeat(HOME_ROW_TYPES.lastIndex) {
                add(pressCurrentAndCapture(Key.DirectionUp))
            }
        }
        val expected = buildList {
            add(FocusTarget(HomeSectionType.WatchLater.name, 11))
            add(FocusTarget(HomeSectionType.Bookmarks.name, 20))
            add(FocusTarget(HomeSectionType.Fresh.name, 31))
            add(FocusTarget(HomeSectionType.PopularMovies.name, 41))
            add(FocusTarget(HomeSectionType.PopularMovies.name, 42))
            add(FocusTarget(HomeSectionType.Fresh.name, 31))
            add(FocusTarget(HomeSectionType.Bookmarks.name, 20))
            add(FocusTarget(HomeSectionType.WatchLater.name, 11))
            add(FocusTarget(HomeSectionType.ContinueWatching.name, 0))
        }

        assertEquals(
            "Home focus trace for Down-to-end → Right → Up",
            expected,
            snapshots.map(FocusSnapshot::target),
        )
        assertBoundsAndViewport(snapshots)
    }

    /**
     * The focused card stays put and the row travels under it, the way the catalogue rows behave.
     *
     * Home used to inherit the column's spec through `LocalBringIntoViewSpec`: a card already
     * fully visible moved the row not at all, so the highlight walked rightwards, and the first
     * card past the edge landed its leading edge at 30 % of the width, throwing the highlight back
     * again. That saw-tooth is what reads as the focus jumping about.
     */
    @Test
    fun rightAlongAHomeRowKeepsTheFocusedCardCentred() {
        val section = HomeSectionState(
            title = "Home row 0",
            type = HomeSectionType.ContinueWatching,
            items = (0 until CENTRING_ITEM_COUNT).map { column -> item(0, column) },
        )
        composeRule.setContent {
            PuberTheme {
                HomeScreenContent(
                    state = HomeViewState.Content(sections = listOf(section)),
                    onAction = {},
                    onHeroClick = {},
                    onCollectionClick = { _, _ -> },
                )
            }
        }

        requestFocus(itemTitle(0, 0))
        val viewport = composeRule.onRoot().getUnclippedBoundsInRoot()

        // The first cards cannot be centred -- the list is already at scroll zero -- so the walk
        // starts far enough in that there is room on both sides of the focused card.
        (CENTRING_FIRST_INDEX..CENTRING_LAST_INDEX).forEach { column ->
            pressUntilFocused(Key.DirectionRight, itemTitle(0, column), maxPresses = 8)
            assertCentred(column, focusedCard().getUnclippedBoundsInRoot(), viewport)
        }
    }

    private fun assertCentred(column: Int, card: DpRect, viewport: DpRect) {
        assertEquals(
            "card $column centre",
            (viewport.left.value + viewport.right.value) / 2f,
            (card.left.value + card.right.value) / 2f,
            BOUNDS_TOLERANCE,
        )
    }

    /**
     * Walks each row to the card it should remember, with the D-pad.
     *
     * A card cannot be seeded by asking it for focus: the row remembers the card the user moved
     * to, and a request placed from outside that bookkeeping is undone by it - focus stays on the
     * card the row was already holding. Right is what a user presses and what the row records.
     */
    private fun seedStableTargets() {
        repeat(HOME_ROW_TYPES.lastIndex) { index ->
            val row = index + 1
            pressUntilFocused(Key.DirectionDown, itemTitle(row, 0))
            if (row in SEEDED_SECOND_CARD_ROWS) {
                pressUntilFocused(Key.DirectionRight, itemTitle(row, 1))
            }
        }
        repeat(HOME_ROW_TYPES.lastIndex) {
            pressCurrent(Key.DirectionUp)
        }
        // Row 0 keeps its first card, so walking back up lands on it with nothing left to press.
        composeRule.onNodeWithText(itemTitle(0, 0)).assertIsFocused()
    }

    /**
     * Presses until the wanted card holds focus.
     *
     * Focus placed by a semantics request leaves the screen's hero claim unresolved, and the first
     * key press afterwards is spent settling it rather than moving anywhere. Pressing until the
     * card is reached says what the test means and does not depend on which press that is.
     */
    private fun pressUntilFocused(key: Key, title: String, maxPresses: Int = 6) {
        repeat(maxPresses) {
            if (composeRule.onAllNodes(isFocused() and hasText(title)).fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return
            }
            pressCurrent(key)
        }
        composeRule.onNodeWithText(title).assertIsFocused()
    }

    private fun requestFocus(title: String) {
        composeRule
            .onNodeWithText(title)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()
    }

    private fun pressCurrent(key: Key) {
        focusedCard().performKeyInput {
            keyDown(key)
            keyUp(key)
        }
        composeRule.waitForIdle()
    }

    private fun pressCurrentAndCapture(key: Key): FocusSnapshot {
        pressCurrent(key)
        val focusedCard = focusedCard()
        val title = focusedCard.fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = " ") { it.text }
        val match = requireNotNull(ITEM_TITLE_PATTERN.find(title)) {
            "Focused Home card has no item identity: $title"
        }
        val row = match.groupValues[1].toInt()
        val column = match.groupValues[2].toInt()
        return FocusSnapshot(
            target = FocusTarget(
                row = HOME_ROW_TYPES[row].name,
                item = row * 10 + column,
            ),
            bounds = focusedCard.getUnclippedBoundsInRoot(),
        )
    }

    private fun focusedCard(): SemanticsNodeInteraction {
        return composeRule.onNode(
            isFocused() and hasText(ITEM_TITLE_PREFIX, substring = true),
        )
    }

    private fun assertBoundsAndViewport(snapshots: List<FocusSnapshot>) {
        val viewport = composeRule.onRoot().getUnclippedBoundsInRoot()
        snapshots.forEach { snapshot ->
            assertTrue(
                "${snapshot.target} top ${snapshot.bounds.top} is above viewport ${viewport.top}",
                snapshot.bounds.top.value >= viewport.top.value - BOUNDS_TOLERANCE,
            )
            assertTrue(
                "${snapshot.target} bottom ${snapshot.bounds.bottom} is below viewport ${viewport.bottom}",
                snapshot.bounds.bottom.value <= viewport.bottom.value + BOUNDS_TOLERANCE,
            )
        }
        snapshots.zipWithNext().forEach { (before, after) ->
            assertTrue(
                "vertical focus delta from ${before.target} to ${after.target} was " +
                    "${abs(after.bounds.top.value - before.bounds.top.value)}dp",
                abs(after.bounds.top.value - before.bounds.top.value) <= MAX_VERTICAL_DELTA.value,
            )
        }
        assertVerticalBoundsEqual(
            before = snapshots[HOME_ROW_TYPES.lastIndex - 1].bounds,
            after = snapshots[HOME_ROW_TYPES.lastIndex].bounds,
        )
    }

    private fun assertVerticalBoundsEqual(before: DpRect, after: DpRect) {
        assertEquals("top after Right", before.top.value, after.top.value, BOUNDS_TOLERANCE)
        assertEquals("bottom after Right", before.bottom.value, after.bottom.value, BOUNDS_TOLERANCE)
    }

    private data class FocusTarget(
        val row: String,
        val item: Int,
    )

    private data class FocusSnapshot(
        val target: FocusTarget,
        val bounds: DpRect,
    )

    private companion object {
        const val ITEM_COUNT = 3
        const val HERO_TITLE = "Featured hero"
        const val ITEM_TITLE_PREFIX = "home-row-"
        const val BOUNDS_TOLERANCE = 1f

        /**
         * Eight cards leave room to centre the middle ones: at either end the list is clamped at
         * scroll zero or its maximum, so the card there sits at the edge rather than the centre.
         */
        val SEEDED_SECOND_CARD_ROWS = setOf(1, 3, 4)

        const val CENTRING_ITEM_COUNT = 8
        const val CENTRING_FIRST_INDEX = 3
        const val CENTRING_LAST_INDEX = 4
        val MAX_VERTICAL_DELTA = 240.dp
        val ITEM_TITLE_PATTERN = Regex("""home-row-(\d+)-item-(\d+)""")
        val HOME_ROW_TYPES = listOf(
            HomeSectionType.ContinueWatching,
            HomeSectionType.WatchLater,
            HomeSectionType.Bookmarks,
            HomeSectionType.Fresh,
            HomeSectionType.PopularMovies,
        )

        fun item(row: Int, column: Int) = VideoItemUIState(
            id = row * 10 + column,
            title = itemTitle(row, column),
            imageUrl = "",
            bigImageUrl = "",
        )

        fun itemTitle(row: Int, column: Int) = "home-row-$row-item-$column"

        fun heroItem() = HeroItemState(
            id = 100,
            title = HERO_TITLE,
            wideImageUrl = "",
            fallbackImageUrl = "",
            year = "2026",
            genres = "Drama",
        )
    }
}
