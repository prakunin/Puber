package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val CARD_TAG = "poster-card"
private const val LONG_PRESS_DELAY_MS = 500L
private val CardWidth = 135.dp

internal class VideoItemPosterTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun videoItemPoster_showsOriginalAndYearAsSeparateTexts() {
        setCard(state(originalTitle = "Orig", year = "2020"))

        composeRule.onNodeWithText("Orig", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("· 2020", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("Orig · 2020", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun videoItemPoster_showsYearAlone_whenNoOriginalTitle() {
        setCard(state(originalTitle = "", year = "2020"))

        composeRule.onNodeWithText("2020", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole() {
        setCard(state(originalTitle = "O".repeat(LONG_ORIGINAL_LENGTH), year = "2020"))

        val original = composeRule.onNodeWithText("O".repeat(LONG_ORIGINAL_LENGTH), useUnmergedTree = true)
        val year = composeRule.onNodeWithText("· 2020", substring = true, useUnmergedTree = true)
        assertTrue(original.textLayout().isLineEllipsized(0))
        // The year Text clips instead of ellipsizing, so a cut year shows as overflow or as a
        // box narrower than its text.
        val yearLayout = year.textLayout()
        assertFalse("year overflows its width", yearLayout.didOverflowWidth)
        assertTrue(
            "year box ends before its text",
            yearLayout.size.width >= yearLayout.getLineRight(0),
        )

        val card = composeRule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot
        val yearBounds = year.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "year $yearBounds outside card $card",
            yearBounds.left >= card.left && yearBounds.right <= card.right,
        )
    }

    @Test
    fun videoItemPoster_longSelectOpensContextMenuWithoutClick() {
        var clicks = 0
        var menus = 0
        setCard(state(), onClick = { clicks++ }, onContextMenu = { menus++ })

        focusCard().performKeyInput {
            keyDown(Key.Enter)
            advanceEventTime(LONG_PRESS_DELAY_MS)
            keyUp(Key.Enter)
        }

        composeRule.runOnIdle {
            assertEquals(1, menus)
            assertEquals(0, clicks)
        }
    }

    @Test
    fun videoItemPoster_selectKeyUpInvokesClickOnce() {
        var clicks = 0
        setCard(state(), onClick = { clicks++ })
        val card = focusCard()

        card.performKeyInput { keyDown(Key.Enter) }
        composeRule.runOnIdle { assertEquals(0, clicks) }

        card.performKeyInput { keyUp(Key.Enter) }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun videoItemPoster_showsBadgesPillsAndBothTitles() {
        setCard(
            state(
                localTitle = "Рус",
                originalTitle = "Orig",
                qualityBadge = VideoQualityBadge.UHD,
                pillRatings = listOf(RatingUIState.PUB("10")),
            ),
        )

        listOf("4K", "10", "Рус", "Orig").forEach { text ->
            composeRule.onNodeWithText(text, useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun videoItemPoster_showsNoBadgesAndNoOriginalTitle_whenStateHasNone() {
        setCard(
            VideoItemUIState(id = 1, title = "Card 1", imageUrl = "", bigImageUrl = ""),
        )

        listOf("CC", "AC3", "HD", "4K").forEach { text ->
            composeRule.onNodeWithText(text, useUnmergedTree = true).assertDoesNotExist()
        }
        composeRule.onNodeWithText("Card 1", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("·", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun setCard(
        state: VideoItemUIState,
        onClick: () -> Unit = {},
        onContextMenu: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            PuberTheme {
                VideoItemPoster(
                    state = state,
                    onClick = onClick,
                    modifier = Modifier.width(CardWidth).testTag(CARD_TAG),
                    onContextMenu = onContextMenu,
                )
            }
        }
    }

    private fun focusCard(): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(CARD_TAG)
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action?.invoke(results)
        return results.first()
    }

    private fun state(
        localTitle: String = "Рус",
        originalTitle: String = "",
        year: String = "",
        qualityBadge: VideoQualityBadge? = null,
        pillRatings: List<RatingUIState> = emptyList(),
    ) = VideoItemUIState(
        id = 1,
        title = "Card 1",
        imageUrl = "",
        bigImageUrl = "",
        localTitle = localTitle,
        originalTitle = originalTitle,
        year = year,
        qualityBadge = qualityBadge,
        pillRatings = pillRatings,
    )
}

private const val LONG_ORIGINAL_LENGTH = 80
