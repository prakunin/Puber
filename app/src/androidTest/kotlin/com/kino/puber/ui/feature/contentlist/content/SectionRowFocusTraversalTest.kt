package com.kino.puber.ui.feature.contentlist.content

import android.os.PowerManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
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
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.core.ui.uikit.component.HeroCarousel
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.PositionFocusedItemInLazyLayout
import com.kino.puber.core.ui.uikit.component.details.PanelPosition
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.component.modifier.FOCUS_ON_LAUNCH_DELAY_MILLIS
import com.kino.puber.core.ui.uikit.component.moviesList.FocusableRow
import com.kino.puber.core.ui.uikit.component.moviesList.nearestNonEmptyRowKey
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.SectionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class SectionRowFocusTraversalTest {

    @get:Rule
    val composeRule = createComposeRule()

    // A television that has gone to sleep still launches the test activity, but never resumes it.
    // Compose registers a root only on `Lifecycle.Event.ON_RESUME`, so every node lookup in this
    // class then fails with "No compose hierarchies found in the app" -- a message that says
    // nothing about the screen and reads like a broken test. Say what is actually wrong instead.
    @Before
    fun requireInteractiveDisplay() {
        val powerManager = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getSystemService(PowerManager::class.java)
        assertTrue(
            "The device display is off. Wake it and re-run: a sleeping device never resumes the " +
                "test activity, and Compose then reports \"No compose hierarchies found in the " +
                "app\" for every assertion in this class, whatever the code under test does.",
            powerManager.isInteractive,
        )
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
    fun dpadRightReportsFocusedPositionWithRowKeyAndIndex() {
        val positions = mutableListOf<PanelPosition>()
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
                    onItemFocused = {},
                    onItemPositionFocused = { positions += it },
                    onSectionFocused = {},
                    onRetry = {},
                    onLoadMore = {},
                )
            }
        }

        requestFocus(itemTitle(row = 0, column = 0))
        composeRule.runOnIdle {
            positions.clear()
        }
        pressCurrent(Key.DirectionRight)

        composeRule.runOnIdle {
            assertEquals(listOf(PanelPosition("row_0", 1, 1)), positions.toList())
        }
    }

    @Test
    fun horizontalRowShowsThreeCardsAndKeepsMovableFocusCentred() {
        composeRule.setContent {
            PuberTheme {
                Box(
                    Modifier
                        .width(HORIZONTAL_ROW_VIEWPORT_WIDTH)
                        .testTag(HORIZONTAL_ROW_VIEWPORT_TAG),
                ) {
                    SectionRowContent(
                        state = SectionState.Content(
                            items = (0 until 6).map { column -> item(0, column) },
                        ),
                        config = SectionConfig(id = "row_0", title = "Row 0"),
                        isTargetRow = true,
                        onItemClick = {},
                        onItemContextMenu = {},
                        onItemFocused = {},
                        onSectionFocused = {},
                        onRetry = {},
                        onLoadMore = {},
                    )
                }
            }
        }

        val viewport = composeRule
            .onNodeWithTag(HORIZONTAL_ROW_VIEWPORT_TAG)
            .getUnclippedBoundsInRoot()
        requestFocus(itemTitle(row = 0, column = 0))

        val first = focusedCardBounds(row = 0, column = 0)
        assertEquals(
            "first card leading edge",
            viewport.left.value + HORIZONTAL_ROW_PADDING.value,
            first.left.value,
            BOUNDS_TOLERANCE,
        )
        val thirdBeforeScroll = composeRule
            .onNodeWithText(itemTitle(row = 0, column = 2))
            .getUnclippedBoundsInRoot()
        assertEquals(
            "exactly three cards fit before horizontal scrolling",
            viewport.right.value - HORIZONTAL_ROW_PADDING.value,
            thirdBeforeScroll.right.value,
            BOUNDS_TOLERANCE,
        )

        pressCurrent(Key.DirectionRight)
        assertHorizontallyCentred(focusedCardBounds(row = 0, column = 1), viewport)
        pressCurrent(Key.DirectionRight)
        assertHorizontallyCentred(focusedCardBounds(row = 0, column = 2), viewport)

        repeat(3) { pressCurrent(Key.DirectionRight) }
        val last = focusedCardBounds(row = 0, column = 5)
        assertEquals(
            "last card trailing edge",
            viewport.right.value - HORIZONTAL_ROW_PADDING.value,
            last.right.value,
            BOUNDS_TOLERANCE,
        )
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

    // The two pieces of production arithmetic the other SettleScene tests never reach: the
    // list-item index of a section when there is *no* hero above it, and an empty section
    // collapsing to nothing instead of owning a page of its own.
    @Test
    fun withoutHeroAnEmptySectionOwnsNoPageAndDpadDownSkipsIt() {
        val rows = listOf(
            RowFixture(
                config = SectionConfig(id = "settle_empty_first", title = "Settle empty first"),
                state = SectionState.Empty,
            ),
            RowFixture(
                config = SectionConfig(id = "settle_no_hero_0", title = "Settle no hero 0"),
                state = SectionState.Content(items = listOf(item(0, 0), item(0, 1))),
            ),
            RowFixture(
                config = SectionConfig(id = "settle_empty_middle", title = "Settle empty middle"),
                state = SectionState.Empty,
            ),
            RowFixture(
                config = SectionConfig(id = "settle_no_hero_1", title = "Settle no hero 1"),
                state = SectionState.Content(items = listOf(item(1, 0), item(1, 1))),
            ),
        )

        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = rows, isHeroItemPresent = false)
            }
        }

        // Nothing has been focused and nothing has scrolled, so the first section with content is
        // flush with the viewport top only if the empty section above it took up no height at all.
        assertSettleFlush(settleSectionTag(1))

        // With no hero, section 0 is list item 0 and section 1 is list item 1. Were the hero
        // offset applied anyway, focusing this section would scroll list item 2 to the top and
        // carry this one off the screen.
        requestFocusOnFirstFocusableIn(settleSectionTag(1))
        assertSettleFlush(settleSectionTag(1))

        // The empty section holds nothing focusable, so Down lands two sections on -- and the
        // scroller has to name *that* section's list index for it to settle flush.
        pressLeafFocused(Key.DirectionDown)
        assertSettleFlush(settleSectionTag(3))
    }

    @Test
    fun browseEntryUpFromFirstShelfCardThenDownReturnsToThatCard() {
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = entryRows(), isHeroItemPresent = false, entryLabel = ENTRY_LABEL)
            }
        }
        requestFocus(itemTitle(0, 0))
        pressCurrent(Key.DirectionRight)
        composeRule.onNodeWithText(itemTitle(0, 1)).assertIsFocused()

        pressCurrent(Key.DirectionUp)
        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsFocused()

        pressLeafFocused(Key.DirectionDown)
        composeRule.onNodeWithText(itemTitle(0, 1)).assertIsFocused()
    }

    @Test
    fun browseEntryIsNotFocusableWhileItsSectionIsLoading() {
        val rows = listOf(
            RowFixture(SectionConfig(id = "entry_loading", title = "Loading"), SectionState.Loading),
        )
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = rows, isHeroItemPresent = false, entryLabel = ENTRY_LABEL)
            }
        }

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assert(!isFocused())
    }

    @Test
    fun browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop() {
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = entryRows(), isHeroItemPresent = false, entryLabel = ENTRY_LABEL)
            }
        }
        assertSettleFlush(settleSectionTag(0))
        val viewport = composeRule.onNodeWithTag(SETTLE_VIEWPORT_TAG).getUnclippedBoundsInRoot()
        val card = composeRule.onNodeWithText(itemTitle(0, 0)).getUnclippedBoundsInRoot()
        assertTrue(
            "card bottom ${card.bottom} is below viewport ${viewport.bottom}",
            card.bottom.value <= viewport.bottom.value + BOUNDS_TOLERANCE,
        )

        requestFocus(itemTitle(0, 0))
        pressCurrent(Key.DirectionDown)

        assertSettleFlush(settleSectionTag(1))
        pressCurrent(Key.DirectionUp)
        assertSettleFlush(settleSectionTag(0))
    }

    @Test
    fun browseEntryIsReachableWithUpWhenSectionZeroIsEmpty() {
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = emptyThenContentRows(), isHeroItemPresent = false, entryLabel = ENTRY_LABEL)
            }
        }

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsDisplayed()
        requestFocus(itemTitle(1, 0))
        pressCurrent(Key.DirectionUp)

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsFocused()
    }

    @Test
    fun launchFocusSkipsTheEntryWhenSectionZeroIsEmpty() {
        composeRule.setContent {
            PuberTheme {
                SettleScene(
                    rows = emptyThenContentRows(),
                    isHeroItemPresent = false,
                    entryLabel = ENTRY_LABEL,
                    launchFocus = true,
                )
            }
        }
        composeRule.waitUntil(LAUNCH_WAIT_MILLIS) { focusedNodeCount() > 0 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(itemTitle(1, 0)).assertIsFocused()
        composeRule.onAllNodes(hasTestTag(SETTLE_ENTRY_TAG) and isFocused()).assertCountEquals(0)
    }

    // The sections load first (the shimmer holds focus), then every one turns out Empty: the shimmer
    // is gone and focus would stay nowhere until a key press, so the entry takes it.
    @Test
    fun browseEntryTakesFocusWhenEverySectionTurnsEmptyAfterLoading() {
        var rows by mutableStateOf(allRows(SectionState.Loading))
        composeRule.setContent {
            PuberTheme {
                SettleScene(
                    rows = rows,
                    isHeroItemPresent = false,
                    entryLabel = ENTRY_LABEL,
                    launchFocus = true,
                )
            }
        }
        composeRule.waitUntil(LAUNCH_WAIT_MILLIS) { focusedNodeCount() > 0 }

        rows = allRows(SectionState.Empty)
        runCatching { composeRule.waitUntil(LAUNCH_WAIT_MILLIS) { entryIsFocused() } }

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsFocused()
        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsFocused()
    }

    // The hero holds focus and every section is Empty: the entry must not take it once the launch
    // delay has passed. The clock is held so the focus lands before the all-Empty delay can fire.
    @Test
    fun browseEntryDoesNotTakeFocusFromTheHeroWhenEverySectionIsEmpty() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuberTheme {
                SettleScene(
                    rows = allRows(SectionState.Empty),
                    isHeroItemPresent = true,
                    entryLabel = ENTRY_LABEL,
                    // The entry has to be composed for the test to see it take focus.
                    heroFillsViewport = false,
                )
            }
        }
        requestFocusOnFirstFocusableIn(SETTLE_HERO_TAG)
        composeRule.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag(SETTLE_HERO_TAG)), useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.mainClock.advanceTimeBy(FOCUS_ON_LAUNCH_DELAY_MILLIS * PAST_LAUNCH_DELAY_FACTOR)
        composeRule.waitForIdle()

        composeRule.onAllNodes(isFocused() and hasAnyAncestor(hasTestTag(SETTLE_HERO_TAG)), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.onAllNodes(hasTestTag(SETTLE_ENTRY_TAG) and isFocused()).assertCountEquals(0)
    }

    // The paginator replays an initial Empty ahead of the first real state, so one all-Empty frame
    // followed by Content must not make the entry focusable before launch focus has landed on a card.
    @Test
    fun aOneFrameAllEmptyThenContentKeepsLaunchFocusOffTheEntry() {
        var rows by mutableStateOf(allRows(SectionState.Empty))
        composeRule.setContent {
            PuberTheme {
                SettleScene(
                    rows = rows,
                    isHeroItemPresent = false,
                    entryLabel = ENTRY_LABEL,
                    launchFocus = true,
                )
            }
        }
        rows = entryRows()
        composeRule.waitUntil(LAUNCH_WAIT_MILLIS) { focusedNodeCount() > 0 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(itemTitle(0, 0)).assertIsFocused()
        composeRule.onAllNodes(hasTestTag(SETTLE_ENTRY_TAG) and isFocused()).assertCountEquals(0)
    }

    @Test
    fun launchFocusSkipsTheEntryWhenSectionZeroIsError() {
        val rows = listOf(
            RowFixture(SectionConfig(id = "entry_error", title = "Error"), SectionState.Error("boom")),
            RowFixture(
                SectionConfig(id = "entry_next", title = "Next"),
                SectionState.Content(items = listOf(item(1, 0), item(1, 1))),
            ),
        )
        composeRule.setContent {
            PuberTheme {
                SettleScene(
                    rows = rows,
                    isHeroItemPresent = false,
                    entryLabel = ENTRY_LABEL,
                    launchFocus = true,
                )
            }
        }
        composeRule.waitUntil(LAUNCH_WAIT_MILLIS) { focusedNodeCount() > 0 }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasTestTag(SETTLE_ENTRY_TAG) and isFocused()).assertCountEquals(0)
    }

    @Test
    fun focusEnteringACardClearsTheEntryOwnership() {
        val focus = BrowseEntryFocus(owner = true)
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = entryRows(), isHeroItemPresent = false, entryLabel = ENTRY_LABEL, focus = focus)
            }
        }
        composeRule.runOnIdle { assertTrue(focus.isOwner) }

        requestFocus(itemTitle(0, 0))

        composeRule.runOnIdle { assertFalse("entry still owns focus", focus.isOwner) }
    }

    @Test
    fun focusEnteringARetryRowClearsTheEntryOwnership() {
        val focus = BrowseEntryFocus(owner = true)
        val rows = listOf(
            RowFixture(SectionConfig(id = "entry_error", title = "Error"), SectionState.Error("boom")),
        )
        composeRule.setContent {
            PuberTheme {
                SettleScene(rows = rows, isHeroItemPresent = false, entryLabel = ENTRY_LABEL, focus = focus)
            }
        }
        composeRule.runOnIdle { assertTrue(focus.isOwner) }

        composeRule
            .onAllNodes(isFocusable() and !hasTestTag(SETTLE_ENTRY_TAG), useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertFalse("entry still owns focus", focus.isOwner) }
    }

    // Returning from the browse grid recomposes the tab with the entry as owner while the first
    // section is still Loading. Focus falls on the focusable shimmer there; when the cards arrive,
    // the row's initial-focus request used to read that stale flag and pull focus off the entry.
    @Test
    fun browseEntryOwnerKeepsFocusWhenItsSectionFinishesLoading() {
        var state by mutableStateOf<SectionState>(SectionState.Loading)
        val focus = BrowseEntryFocus(owner = true)
        composeRule.setContent {
            PuberTheme {
                OwnerEntryScene(state = state, focus = focus)
            }
        }
        requestFocusOnFirstFocusableIn(OWNER_ROW_TAG)

        state = SectionState.Content(items = listOf(item(0, 0), item(0, 1)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SETTLE_ENTRY_TAG).assertIsFocused()
    }

    @Composable
    private fun OwnerEntryScene(state: SectionState, focus: BrowseEntryFocus) {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(SETTLE_VIEWPORT_HEIGHT).focusGroup()) {
            item(key = "owner_section") {
                SectionListItem(
                    title = "Owner row",
                    isEmpty = false,
                    fillsViewport = true,
                    header = {
                        BrowseEntryHeader(
                            label = ENTRY_LABEL,
                            focus = focus,
                            sectionState = state,
                            onFocused = {},
                            onClick = {},
                            modifier = Modifier.testTag(SETTLE_ENTRY_TAG),
                        )
                    },
                ) {
                    Box(Modifier.testTag(OWNER_ROW_TAG)) {
                        SectionRowContent(
                            state = state,
                            config = SectionConfig(id = "owner_row", title = "Owner row"),
                            isTargetRow = false,
                            onItemClick = {},
                            onItemContextMenu = {},
                            onItemFocused = {},
                            onSectionFocused = {},
                            onFocusChanged = { if (it) focus.onRowFocused(state) },
                            onRetry = {},
                            onLoadMore = {},
                            initialFocusEnabled = !focus.isOwner,
                        )
                    }
                }
            }
        }
    }

    private fun emptyThenContentRows() = listOf(
        RowFixture(SectionConfig(id = "entry_empty", title = "Empty"), SectionState.Empty),
        RowFixture(
            SectionConfig(id = "entry_next", title = "Next"),
            SectionState.Content(items = listOf(item(1, 0), item(1, 1))),
        ),
    )

    private fun allRows(state: SectionState) = listOf(
        RowFixture(SectionConfig(id = "all_state_0", title = "Section 0"), state),
        RowFixture(SectionConfig(id = "all_state_1", title = "Section 1"), state),
    )

    private fun entryIsFocused() = composeRule
        .onAllNodes(hasTestTag(SETTLE_ENTRY_TAG) and isFocused())
        .fetchSemanticsNodes()
        .isNotEmpty()

    private fun focusedNodeCount() = composeRule
        .onAllNodes(isFocused(), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .size

    private fun entryRows() = listOf(
        RowFixture(
            config = SectionConfig(id = "entry_row_0", title = "Entry row 0"),
            state = SectionState.Content(items = listOf(item(0, 0), item(0, 1), item(0, 2))),
        ),
        RowFixture(
            config = SectionConfig(id = "entry_row_1", title = "Entry row 1"),
            state = SectionState.Content(items = listOf(item(1, 0), item(1, 1))),
        ),
    )

    // Mirrors ContentListLayout's item wiring: an optional hero item followed by one item per
    // section, sharing the same `rememberFocusedListItemScroller`. The two pieces of arithmetic
    // this scene is here to guard -- where a section sits in the list, and what an empty section
    // collapses to -- come from production's `sectionListItemIndex` and `SectionListItem` rather
    // than from a copy of them, so changing either breaks these tests.
    @Composable
    private fun SettleScene(
        rows: List<RowFixture>,
        isHeroItemPresent: Boolean = true,
        entryLabel: String? = null,
        focus: BrowseEntryFocus = rememberBrowseEntryFocus(rows.map { it.state }),
        launchFocus: Boolean = false,
        heroFillsViewport: Boolean = true,
    ) {
        val lazyListState = rememberLazyListState()
        // The launch path of ContentListScreenContent: the screen's launch request on the list, and
        // the nearest non-empty section becoming the target row when the first one is Empty.
        val launchRequester = rememberFocusRequesterOnLaunch()
        var targetIndex by remember { mutableIntStateOf(0) }
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
                        .then(if (launchFocus) Modifier.focusRequester(launchRequester) else Modifier)
                        .onFocusChanged { focus.onListFocusChanged(it.hasFocus) }
                        .focusGroup(),
                ) {
                    if (isHeroItemPresent) {
                        item(key = "hero", contentType = "hero") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (heroFillsViewport) Modifier.fillParentMaxHeight() else Modifier.height(SHORT_HERO_HEIGHT),
                                    )
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
                    }
                    itemsIndexed(rows, key = { _, row -> row.config.id }) { index, row ->
                        val rowIndex = sectionListItemIndex(index, isHeroItemPresent)
                        SectionListItem(
                            title = row.config.title,
                            isEmpty = row.state is SectionState.Empty,
                            fillsViewport = true,
                            modifier = Modifier.testTag(settleSectionTag(rowIndex)),
                            header = entryLabel?.takeIf { index == 0 }?.let { label ->
                                {
                                    BrowseEntryHeader(
                                        label = label,
                                        focus = focus,
                                        sectionState = row.state,
                                        onFocused = {
                                            focus.onEntryFocused()
                                            onListItemFocused(rowIndex)
                                        },
                                        onClick = {},
                                        modifier = Modifier.testTag(SETTLE_ENTRY_TAG),
                                    )
                                }
                            },
                        ) {
                            SectionRowContent(
                                state = row.state,
                                config = row.config,
                                isTargetRow = launchFocus && index == targetIndex,
                                onItemClick = {},
                                onItemContextMenu = {},
                                onItemFocused = {},
                                onSectionFocused = {},
                                onFocusChanged = { hasFocus ->
                                    if (hasFocus) {
                                        focus.onRowFocused(row.state)
                                        onListItemFocused(rowIndex)
                                    }
                                },
                                onRetry = {},
                                onLoadMore = {},
                                initialFocusEnabled = !focus.isOwner,
                                onRowEmpty = {
                                    val keys = rows.map { r ->
                                        FocusableRow(
                                            r.config.id,
                                            (r.state as? SectionState.Content)?.items?.size ?: 0,
                                        )
                                    }
                                    nearestNonEmptyRowKey(keys, index)?.let { key ->
                                        targetIndex = rows.indexOfFirst { it.config.id == key }
                                    }
                                },
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
        composeRule.waitForIdle()
    }

    private fun pressLeafFocused(key: Key) {
        composeRule
            .onNode(leafFocusedMatcher, useUnmergedTree = true)
            .performKeyInput {
                keyDown(key)
                keyUp(key)
            }
        composeRule.waitForIdle()
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
        composeRule.waitForIdle()
    }

    private fun pressCurrent(key: Key) {
        composeRule
            .onNode(focusedCardMatcher, useUnmergedTree = true)
            .performKeyInput {
                keyDown(key)
                keyUp(key)
            }
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

    private fun focusedCardBounds(row: Int, column: Int): DpRect = composeRule
        .onNodeWithText(itemTitle(row, column))
        .assertIsFocused()
        .getUnclippedBoundsInRoot()

    private fun assertHorizontallyCentred(card: DpRect, viewport: DpRect) {
        val cardCentre = (card.left.value + card.right.value) / 2f
        val viewportCentre = (viewport.left.value + viewport.right.value) / 2f
        assertEquals("focused card centre", viewportCentre, cardCentre, BOUNDS_TOLERANCE)
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
        const val HORIZONTAL_ROW_VIEWPORT_TAG = "horizontal_row_viewport"
        const val BOUNDS_TOLERANCE = 1f
        val VIEWPORT_HEIGHT = 420.dp
        val HORIZONTAL_ROW_VIEWPORT_WIDTH = 864.dp
        val HORIZONTAL_ROW_PADDING = 16.dp
        val MAX_VERTICAL_DELTA = 210.dp

        // Matches the real device: a 960x540 dp screen with a 205 dp list viewport below the
        // detail panel, and a 150 dp card (PuberTheme.Defaults.CatalogueRowItemHeight) — the
        // exact numbers behind the bug this test guards against.
        const val SETTLE_VIEWPORT_TAG = "settle_viewport"
        const val SETTLE_HERO_TAG = "settle_hero"
        const val SETTLE_ENTRY_TAG = "settle_entry"
        const val OWNER_ROW_TAG = "owner_row"
        const val ENTRY_LABEL = "Все фильмы"
        const val LAUNCH_WAIT_MILLIS = 3_000L
        const val PAST_LAUNCH_DELAY_FACTOR = 5
        val SHORT_HERO_HEIGHT = 120.dp
        val SETTLE_VIEWPORT_HEIGHT = 205.dp
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
