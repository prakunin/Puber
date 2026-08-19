package com.kino.puber.ui.feature.contentlist.content

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class SectionRowFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    // `SettleScene` composes unconditional infinite animations -- the real `HeroCarousel`'s Ken
    // Burns transition, and the Loading shimmer's highlight -- that never let a clock-driven
    // `waitForIdle()` go idle on its own; left on, it would spin forever instead of finishing.
    // With auto-advance off, `waitForIdle()` only drains already-pending work at the current
    // clock time, so every interaction that can start a (bounded) scroll animation instead nudges
    // the clock forward by hand via `settleAfterInteraction()`.
    @Before
    fun disableAutoAdvance() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun dpadFocusOnNonFallbackItemInNonTargetRowRecordsActualIdentity() {
        var focusedItemId: Int? = null
        composeRule.setContent {
            PuberTheme {
                SectionRowContent(
                    state = SectionState.Content(
                        items = listOf(item(0, 0), item(0, 1), item(0, 2)),
                    ),
                    config = SectionConfig(id = "row_0", title = "Row 0"),
                    isTargetRow = false,
                    onItemClick = {},
                    onItemContextMenu = {},
                    onItemFocused = { focusedItemId = it.id },
                    onSectionFocused = {},
                    onRetry = {},
                    onLoadMore = {},
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
    fun downToEndRightThenUpRestoresGenericRowTargetsInsideViewport() {
        val rows = (0 until ROW_COUNT).map { rowIndex ->
            RowFixture(
                config = SectionConfig(id = "row_$rowIndex", title = "Row $rowIndex"),
                state = SectionState.Content(
                    items = listOf(
                        item(rowIndex, 0),
                        item(rowIndex, 1),
                        item(rowIndex, 2),
                    ),
                ),
            )
        }
        var focusedRowIndex by mutableIntStateOf(0)
        val trace = mutableStateOf<List<FocusTarget>>(emptyList())

        composeRule.setContent {
            PuberTheme {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(VIEWPORT_HEIGHT)
                        .testTag(VIEWPORT_TAG),
                ) {
                    PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(VIEWPORT_HEIGHT)
                                .focusGroup(),
                        ) {
                            items(rows, key = { it.config.id }) { row ->
                                SectionRowContent(
                                    state = row.state,
                                    config = row.config,
                                    isTargetRow = rows.indexOf(row) == focusedRowIndex,
                                    onItemClick = {},
                                    onItemContextMenu = {},
                                    onItemFocused = { item ->
                                        trace.value += FocusTarget(
                                            row = row.config.id,
                                            item = item.id,
                                        )
                                    },
                                    onSectionFocused = {
                                        focusedRowIndex = rows.indexOf(row)
                                    },
                                    onRetry = {},
                                    onLoadMore = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        requestFocus(itemTitle(0, 0))
        seedStableTargets()
        composeRule.runOnIdle {
            trace.value = emptyList()
        }

        val snapshots = buildList {
            repeat(ROW_COUNT - 1) {
                add(pressCurrentAndCapture(Key.DirectionDown, trace))
            }
            add(pressCurrentAndCapture(Key.DirectionRight, trace))
            repeat(ROW_COUNT - 1) {
                add(pressCurrentAndCapture(Key.DirectionUp, trace))
            }
        }
        val expected = buildList {
            add(FocusTarget("row_1", 11))
            add(FocusTarget("row_2", 20))
            add(FocusTarget("row_3", 31))
            add(FocusTarget("row_4", 41))
            add(FocusTarget("row_4", 42))
            add(FocusTarget("row_3", 31))
            add(FocusTarget("row_2", 20))
            add(FocusTarget("row_1", 11))
            add(FocusTarget("row_0", 0))
        }

        assertEquals(
            "generic settled focus trace for Down-to-end → Right → Up; " +
                "raw focus callbacks=${trace.value}",
            expected,
            snapshots.map(FocusSnapshot::target),
        )
        assertBoundsAndViewport(snapshots)
    }

    // Drives the real `rememberFocusedListItemScroller` from ContentListScreenContent.kt --
    // deleting or breaking that shared function breaks this test, not just a copy of it. Each
    // section is a viewport-sized item (heading + gap + SectionRowContent, wrapped in
    // `Modifier.fillParentMaxHeight()`), same as the hero. `SectionRowContent`'s `onFocusChanged`
    // fires for a focused Content card, the Loading shimmer, or the Error row's Retry button
    // alike, so all three states wire into the same scroller the same way production does.
    @Test
    fun dpadDownSettlesHeroAndEverySectionStateFlushWithViewportTop() {
        val rows = listOf(
            RowFixture(
                config = SectionConfig(id = "settle_content_0", title = "Settle content 0"),
                state = SectionState.Content(items = listOf(item(0, 0), item(0, 1))),
            ),
            RowFixture(
                config = SectionConfig(id = "settle_loading", title = "Settle loading"),
                state = SectionState.Loading,
            ),
            RowFixture(
                config = SectionConfig(id = "settle_error", title = "Settle error"),
                state = SectionState.Error("boom"),
            ),
            RowFixture(
                config = SectionConfig(id = "settle_content_1", title = "Settle content 1"),
                state = SectionState.Content(items = listOf(item(1, 0), item(1, 1))),
            ),
        )

        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = rows)
            }
        }

        requestFocusOnFirstFocusableIn(SETTLE_HERO_TAG)
        assertSettleFlush(SETTLE_HERO_TAG)

        (1..rows.size).forEach { rowIndex ->
            pressLeafFocused(Key.DirectionDown)
            assertSettleFlush(settleSectionTag(rowIndex))
        }
    }

    // Covers the round trip the original fix missed: leaving a section for the hero, then coming
    // straight back, without the section index itself ever changing. `focusedSectionIndex` alone
    // (0 written over 0) would not restart a correction keyed on it; the scroller is keyed on the
    // list-item index instead, which does change on this round trip (the hero and the section are
    // different indices), so the correction re-fires and the section is flush again on return.
    @Test
    fun dpadUpIntoHeroThenDownReSettlesSameSectionFlushWithViewportTop() {
        val rows = listOf(
            RowFixture(
                config = SectionConfig(id = "settle_roundtrip", title = "Settle roundtrip"),
                state = SectionState.Content(items = listOf(item(0, 0), item(0, 1))),
            ),
        )

        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = rows)
            }
        }

        requestFocusOnFirstFocusableIn(SETTLE_HERO_TAG)
        pressLeafFocused(Key.DirectionDown)
        assertSettleFlush(settleSectionTag(1))

        pressLeafFocused(Key.DirectionUp)
        assertSettleFlush(SETTLE_HERO_TAG)

        pressLeafFocused(Key.DirectionDown)
        assertSettleFlush(settleSectionTag(1))
    }

    // Mirrors ContentListLayout's item wiring: a hero item followed by one viewport-sized item
    // per section, sharing the same `rememberFocusedListItemScroller`.
    @Composable
    private fun SettleScene(rows: List<RowFixture>) {
        val lazyListState = rememberLazyListState()
        val onListItemFocused = rememberFocusedListItemScroller(lazyListState)
        Box(
            Modifier
                .fillMaxWidth()
                .height(SETTLE_VIEWPORT_HEIGHT)
                .testTag(SETTLE_VIEWPORT_TAG),
        ) {
            PositionFocusedItemInLazyLayout(keepFullyVisibleItemInPlace = true) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SETTLE_VIEWPORT_HEIGHT)
                        .focusGroup(),
                ) {
                    item(key = "hero", contentType = "hero") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight()
                                .testTag(SETTLE_HERO_TAG),
                        ) {
                            HeroCarousel(
                                items = listOf(settleHeroItem()),
                                onItemClick = {},
                                modifier = Modifier.fillMaxSize(),
                                onFocusedItemChanged = { onListItemFocused(0) },
                            )
                        }
                    }
                    items(rows, key = { it.config.id }) { row ->
                        val rowIndex = rows.indexOf(row) + 1
                        Column(
                            modifier = Modifier
                                .fillParentMaxHeight()
                                .testTag(settleSectionTag(rowIndex)),
                        ) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                text = row.config.title,
                                style = SectionTitleStyle,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            SectionRowContent(
                                state = row.state,
                                config = row.config,
                                isTargetRow = false,
                                onItemClick = {},
                                onItemContextMenu = {},
                                onItemFocused = {},
                                onSectionFocused = {},
                                onFocusChanged = { hasFocus -> if (hasFocus) onListItemFocused(rowIndex) },
                                onRetry = {},
                                onLoadMore = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun settleHeroItem() = HeroItemState(
        id = 0,
        title = "Settle hero",
        wideImageUrl = "",
        fallbackImageUrl = "",
        year = "",
        genres = "",
    )

    private fun settleSectionTag(rowIndex: Int) = "settle_section_$rowIndex"

    private fun assertSettleFlush(tag: String) {
        val viewportTop = composeRule
            .onNodeWithTag(SETTLE_VIEWPORT_TAG)
            .getUnclippedBoundsInRoot()
            .top
        val itemTop = composeRule
            .onNodeWithTag(tag)
            .getUnclippedBoundsInRoot()
            .top
        assertTrue(
            "$tag top $itemTop is not flush with viewport top $viewportTop",
            abs(itemTop.value - viewportTop.value) <= BOUNDS_TOLERANCE,
        )
    }

    // The initial focus target and each D-pad landing spot inside SettleScene has no text of its
    // own (the hero card is an image, the Loading shimmer and the Error row's Retry button carry
    // no label in this fixture), so unlike `requestFocus`/`pressCurrent` above, these can't select
    // by text. `isFocusable()` finds every candidate node in the row; `leafFocusedMatcher` then
    // picks out the one truly holding focus rather than an ancestor that merges it up.
    private fun requestFocusOnFirstFocusableIn(rowTag: String) {
        composeRule
            .onAllNodes(isFocusable() and hasAnyAncestor(hasTestTag(rowTag)), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.RequestFocus)
        settleAfterInteraction()
    }

    private fun pressLeafFocused(key: Key) {
        composeRule
            .onNode(leafFocusedMatcher, useUnmergedTree = true)
            .performKeyInput {
                keyDown(key)
                keyUp(key)
            }
        settleAfterInteraction()
    }

    private fun seedStableTargets() {
        repeat(ROW_COUNT - 1) { index ->
            val row = index + 1
            pressCurrent(Key.DirectionDown)
            requestFocus(
                when (row) {
                    1, 3, 4 -> itemTitle(row, 1)
                    else -> itemTitle(row, 0)
                },
            )
        }
        repeat(ROW_COUNT - 1) {
            pressCurrent(Key.DirectionUp)
        }
        requestFocus(itemTitle(0, 0))
    }

    private fun requestFocus(title: String) {
        composeRule
            .onNodeWithText(title)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        settleAfterInteraction()
    }

    private fun pressCurrent(key: Key) {
        composeRule
            .onNode(focusedCardMatcher, useUnmergedTree = true)
            .performKeyInput {
                keyDown(key)
                keyUp(key)
            }
        settleAfterInteraction()
    }

    // A focus change can start a bounded scroll animation (the automatic per-card bring-into-view
    // request, or -- for `SettleScene` -- `rememberFocusedListItemScroller`'s explicit correction).
    // With auto-advance off (see `disableAutoAdvance`), `waitForIdle()` alone will not drive that
    // animation forward, so nudge the clock by hand first, long past any realistic animation
    // duration, then let composition catch up.
    private fun settleAfterInteraction() {
        composeRule.mainClock.advanceTimeBy(ANIMATION_SETTLE_TIME_MS)
        composeRule.waitForIdle()
    }

    private fun pressCurrentAndCapture(
        key: Key,
        trace: androidx.compose.runtime.State<List<FocusTarget>>,
    ): FocusSnapshot {
        pressCurrent(key)
        val target = trace.value.last()
        val row = target.row.removePrefix("row_").toInt()
        val column = target.item % 10
        val bounds = composeRule
            .onNodeWithText(itemTitle(row, column))
            .assertIsFocused()
            .getUnclippedBoundsInRoot()
        return FocusSnapshot(target = target, bounds = bounds)
    }

    private fun assertBoundsAndViewport(snapshots: List<FocusSnapshot>) {
        val viewport = composeRule.onNodeWithTag(VIEWPORT_TAG).getUnclippedBoundsInRoot()
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
            before = snapshots[ROW_COUNT - 2].bounds,
            after = snapshots[ROW_COUNT - 1].bounds,
        )
    }

    private fun assertVerticalBoundsEqual(before: DpRect, after: DpRect) {
        assertEquals("top after Right", before.top.value, after.top.value, BOUNDS_TOLERANCE)
        assertEquals("bottom after Right", before.bottom.value, after.bottom.value, BOUNDS_TOLERANCE)
    }

    private data class RowFixture(
        val config: SectionConfig,
        val state: SectionState,
    )

    private data class FocusTarget(
        val row: String,
        val item: Int,
    )

    private data class FocusSnapshot(
        val target: FocusTarget,
        val bounds: DpRect,
    )

    private companion object {
        const val ROW_COUNT = 5
        const val VIEWPORT_TAG = "section_traversal_viewport"
        const val BOUNDS_TOLERANCE = 1f
        val VIEWPORT_HEIGHT = 420.dp
        val MAX_VERTICAL_DELTA = 210.dp

        // Comfortably past any bounded scroll animation's duration; harmless to overshoot since
        // `advanceTimeBy` just steps the virtual clock forward, it does not sleep in real time.
        const val ANIMATION_SETTLE_TIME_MS = 5_000L

        // Matches the real device: a 960x540 dp screen with a 270 dp list viewport below the
        // detail panel, and a 190 dp card (PuberTheme.Defaults.CatalogueRowItemHeight) — the
        // exact numbers behind the bug this test guards against.
        const val SETTLE_VIEWPORT_TAG = "settle_viewport"
        const val SETTLE_HERO_TAG = "settle_hero"
        val SETTLE_VIEWPORT_HEIGHT = 270.dp
        val focusedCardMatcher = isFocused() and hasAnyDescendant(
            hasText("row-", substring = true),
        )

        // Picks out the node truly holding focus rather than an ancestor a component (Card,
        // Button) merges it up to: the one with no focused descendant of its own.
        val leafFocusedMatcher = isFocused() and hasAnyDescendant(isFocused()).not()

        fun item(row: Int, column: Int) = VideoItemUIState(
            id = row * 10 + column,
            title = itemTitle(row, column),
            imageUrl = "",
            bigImageUrl = "",
        )

        fun itemTitle(row: Int, column: Int) = "row-$row-item-$column"
    }
}
