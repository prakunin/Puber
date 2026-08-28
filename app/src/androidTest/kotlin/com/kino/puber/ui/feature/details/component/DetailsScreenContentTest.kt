package com.kino.puber.ui.feature.details.component

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Play
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsSeasonUIState
import org.junit.Rule
import org.junit.Test

private const val PRIMARY_ACTION = "Primary details action"

private const val HERO_TITLE = "Hero Title"
private const val HERO_DESCRIPTION = "Short plot synopsis."
private const val HERO_CHIP = "4K"
private const val HERO_FACTS_LINE = "Дубляж · Дорожек: 3"
private const val HERO_GENRES_LINE = "Жанры: Драма, Фантастика"
private const val HERO_DIRECTOR_LINE = "Режиссёр: Иван Иванов"
private const val HERO_CAST_LINE = "В ролях: Пётр Петров, Сидор Сидоров"
private const val HERO_RESUME_LINE = "Остановились на 2 сезоне, 1 серии"

private const val FIRST_SEASON_CHIP = "1"
private const val FIRST_SEASON_EPISODE = "First season episode"
private const val SECOND_SEASON_EPISODE = "Second season episode"
private const val SIMILAR_ITEM_TITLE = "A similar film"

/**
 * A plot longer than its block. Only such a text grows the focusable scrolling that replaced the
 * auto-scroll.
 */
private val LONG_DESCRIPTION = "Довольно длинное описание. ".repeat(60)

internal class DetailsScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun heroShowsTitleChipsFactsCreditsResumeAndActionsWithoutScrolling() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(state = seriesContent(), onAction = {})
            }
        }

        // All on one screen, with no scroll performed to reach any of it.
        composeRule.onNodeWithText(HERO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_CHIP).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_DESCRIPTION).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_FACTS_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_DIRECTOR_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_CAST_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_RESUME_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsDisplayed()
    }

    @Test
    fun theCastLineSurvivesAnEmptyFactsLine() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = seriesContent(factsLine = ""),
                    onAction = {},
                )
            }
        }

        // Fails if a blank facts row still took space and pushed the credits out.
        composeRule.onNodeWithText(HERO_CAST_LINE).assertIsDisplayed()
    }

    @Test
    fun theActionsHoldFocusWhenTheScreenOpens() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(state = seriesContent(), onAction = {})
            }
        }

        composeRule.waitForFocus(PRIMARY_ACTION)
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsFocused()
    }

    @Test
    fun downFromTheActionsGoesThroughTheSeasonChipsToTheEpisodes() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(state = seriesContent(), onAction = {})
            }
        }

        composeRule.waitForFocus(PRIMARY_ACTION)
        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionDown) }

        // The season chips sit between the buttons and the episodes, and focus passes through
        // them -- otherwise the season could not be switched from the remote at all.
        composeRule.waitForFocus(FIRST_SEASON_CHIP)
        composeRule.onNodeWithText(FIRST_SEASON_CHIP).performKeyInput { pressKey(Key.DirectionDown) }

        // The second season is selected, so its episodes are what lies below -- not the first's.
        composeRule.waitForFocus(SECOND_SEASON_EPISODE)
        composeRule.onNodeWithText(SECOND_SEASON_EPISODE).assertIsFocused()
    }

    @Test
    fun theRailShowsTheEpisodesOfWhicheverSeasonIsSelected() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(state = seriesContent(selectedSeason = 1), onAction = {})
            }
        }

        composeRule.onNodeWithText(FIRST_SEASON_EPISODE).assertIsDisplayed()
    }

    @Test
    fun aSeasonChipAsksForItsSeason() {
        val actions = mutableListOf<DetailsAction>()
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = seriesContent(),
                    onAction = { action -> (action as? DetailsAction)?.let(actions::add) },
                )
            }
        }

        // Keys only: a TV Surface listens for the remote, not for touch, and does not answer
        // performClick -- exactly as on the television.
        composeRule.waitForFocus(PRIMARY_ACTION)
        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForFocus(FIRST_SEASON_CHIP)
        composeRule.onNodeWithText(FIRST_SEASON_CHIP).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assert(actions.contains(DetailsAction.SeasonSelected(1))) { actions.toString() }
    }

    @Test
    fun downFromTheActionsOnAMovieReachesTheSimilarItems() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(state = movieContent(), onAction = {})
            }
        }

        composeRule.waitForFocus(PRIMARY_ACTION)
        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()

        // Similar items no longer hide on a second pager page nobody reached.
        composeRule.onNodeWithText(SIMILAR_ITEM_TITLE).assertIsDisplayed()
    }

    @Test
    fun aPlotTooLongForItsBlockTakesFocusOnUpFromTheActions() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = seriesContent(description = LONG_DESCRIPTION),
                    onAction = {},
                )
            }
        }

        composeRule.waitForFocus(PRIMARY_ACTION)
        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitForIdle()

        // Focus left the buttons: the plot took it, because it has somewhere to scroll.
        composeRule
            .onNodeWithText(PRIMARY_ACTION)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Focused)
            .let { focused -> assert(focused != true) { "кнопка удержала фокус вместо описания" } }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForFocus(text: String) {
        waitUntil {
            onNodeWithText(text)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
    }

    private fun seriesContent(
        description: String = HERO_DESCRIPTION,
        factsLine: String = HERO_FACTS_LINE,
        selectedSeason: Int = 2,
    ) = content(
        description = description,
        factsLine = factsLine,
        seasons = listOf(
            DetailsSeasonUIState(
                number = 1,
                episodes = listOf(episode(101, 1, 1, FIRST_SEASON_EPISODE)),
                summary = "1 сезон · 1 серия",
            ),
            DetailsSeasonUIState(
                number = 2,
                episodes = listOf(episode(201, 2, 1, SECOND_SEASON_EPISODE)),
                summary = "2 сезон · 1 серия",
            ),
        ),
        selectedSeasonNumber = selectedSeason,
    )

    private fun movieContent() = content(
        similarItems = listOf(
            VideoItemUIState(
                id = 900,
                title = SIMILAR_ITEM_TITLE,
                imageUrl = "",
                bigImageUrl = "",
                showTitle = true,
            ),
        ),
    )

    private fun content(
        description: String = HERO_DESCRIPTION,
        factsLine: String = HERO_FACTS_LINE,
        seasons: List<DetailsSeasonUIState> = emptyList(),
        selectedSeasonNumber: Int? = null,
        similarItems: List<VideoItemUIState> = emptyList(),
    ): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = VideoDetailsUIState(
                id = 42,
                title = HERO_TITLE,
                description = description,
                imageUrl = "",
                trailerUrl = "",
                ratings = emptyList(),
                year = "2024",
                genres = "Drama",
                duration = "1h 40m",
                country = "US",
            ),
            info = DetailsInfoUIState(
                ratings = emptyList(),
                chips = listOf(HERO_CHIP),
                genresLine = HERO_GENRES_LINE,
                factsLine = factsLine,
                directorLine = HERO_DIRECTOR_LINE,
                castLine = HERO_CAST_LINE,
                resumeLine = HERO_RESUME_LINE,
            ),
            buttons = listOf(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_watch_movie,
                    icon = PhosphorIcons.Duotone.Play,
                    action = DetailsAction.PlayClicked,
                    textOverride = PRIMARY_ACTION,
                ),
            ),
            isInWatchlist = false,
            isWatched = false,
            seasons = seasons,
            selectedSeasonNumber = selectedSeasonNumber,
            similarItems = similarItems,
        )
    }

    private fun episode(
        id: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String,
    ) = VideoItemUIState(
        id = id,
        title = title,
        imageUrl = "",
        bigImageUrl = "",
        showTitle = true,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
    )
}
