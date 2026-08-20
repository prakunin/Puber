package com.kino.puber.ui.feature.details.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.Play
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsButtonUIState
import com.kino.puber.ui.feature.details.model.DetailsInfoUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import org.junit.Rule
import org.junit.Test

private const val PRIMARY_ACTION = "Primary details action"
private const val DEFAULT_EPISODE = "S1E1"
private const val TARGET_EPISODE = "S8E4 target"

private const val HERO_TITLE = "Hero Title"
private const val HERO_DESCRIPTION = "Short plot synopsis."
private const val HERO_YEAR = "2024"
private const val HERO_GENRES = "Drama"
private const val HERO_COUNTRY = "US"
private const val HERO_DURATION = "1h 40m"
private const val HERO_FACTS_LINE = "4K · 16+"
private const val HERO_CREDITS_LINE = "Режиссёр: Иван Иванов"
private val HERO_META_LINE = listOf(HERO_YEAR, HERO_GENRES, HERO_COUNTRY, HERO_DURATION).joinToString(" · ")

internal class DetailsScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun visibleEpisodePanelFocusesExactTargetInLaterSeason() {
        val episodes = episodes()
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes,
                        seasonsPanelVisible = true,
                        initialEpisodeFocusId = TARGET_EPISODE_ID,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Season 8").assertIsDisplayed()
        composeRule.onNodeWithText(TARGET_EPISODE).assertIsDisplayed()
        composeRule.waitUntil {
            composeRule
                .onNodeWithText(TARGET_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(TARGET_EPISODE).assertIsFocused()
    }

    @Test
    fun ordinaryDetailsWithoutEpisodeTargetKeepsPrimaryActionFocused() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = false,
                        initialEpisodeFocusId = null,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithText(PRIMARY_ACTION)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsFocused()
        composeRule.onNodeWithText(TARGET_EPISODE).assertDoesNotExist()
    }

    @Test
    fun heroDisplaysTitleMetaFactsCreditsAndButtonsWithoutScrolling() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = false,
                        initialEpisodeFocusId = null,
                        title = HERO_TITLE,
                        description = HERO_DESCRIPTION,
                        year = HERO_YEAR,
                        genres = HERO_GENRES,
                        duration = HERO_DURATION,
                        country = HERO_COUNTRY,
                        factsLine = HERO_FACTS_LINE,
                        creditsLine = HERO_CREDITS_LINE,
                    ),
                    onAction = {},
                )
            }
        }

        // All on the same screen, with no scroll action performed to reach any of them.
        composeRule.onNodeWithText(HERO_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_META_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_DESCRIPTION).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_FACTS_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(HERO_CREDITS_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsDisplayed()
    }

    @Test
    fun emptyFactsLineDoesNotHideCreditsLine() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = false,
                        initialEpisodeFocusId = null,
                        description = HERO_DESCRIPTION,
                        factsLine = "",
                        creditsLine = HERO_CREDITS_LINE,
                    ),
                    onAction = {},
                )
            }
        }

        // Fails if a blank facts row still consumed space and pushed the credits line out.
        composeRule.onNodeWithText(HERO_CREDITS_LINE).assertIsDisplayed()
    }

    @Test
    fun visibleEpisodePanelWithoutTargetFocusesDefaultEpisode() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        seasonsPanelVisible = true,
                        initialEpisodeFocusId = null,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithText(DEFAULT_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(DEFAULT_EPISODE).assertIsFocused()
    }

    private fun content(
        episodes: VideoGridUIState,
        seasonsPanelVisible: Boolean,
        initialEpisodeFocusId: Int?,
        title: String = "Synthetic details",
        description: String = "",
        year: String = "",
        genres: String = "",
        duration: String = "",
        country: String = "",
        factsLine: String = "",
        creditsLine: String = "",
    ): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = VideoDetailsUIState(
                id = 42,
                title = title,
                description = description,
                imageUrl = "",
                trailerUrl = "",
                ratings = emptyList(),
                year = year,
                genres = genres,
                duration = duration,
                country = country,
            ),
            info = DetailsInfoUIState(
                ratings = emptyList(),
                factsLine = factsLine,
                creditsLine = creditsLine,
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
            seasonsPanelVisible = seasonsPanelVisible,
            episodes = episodes,
            initialEpisodeFocusId = initialEpisodeFocusId,
        )
    }

    private fun episodes(): VideoGridUIState {
        return VideoGridUIState(
            list = (1..8).flatMap { seasonNumber ->
                listOf(
                    VideoGridItemUIState.Title("Season $seasonNumber"),
                    VideoGridItemUIState.Items(
                        items = listOf(
                            episode(
                                id = seasonNumber * 100 + 1,
                                seasonNumber = seasonNumber,
                                episodeNumber = 1,
                            ),
                            if (seasonNumber == 8) {
                                episode(
                                    id = TARGET_EPISODE_ID,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = 4,
                                    title = TARGET_EPISODE,
                                )
                            } else {
                                episode(
                                    id = seasonNumber * 100 + 2,
                                    seasonNumber = seasonNumber,
                                    episodeNumber = 2,
                                )
                            },
                        ),
                        rowKey = "season_$seasonNumber",
                    ),
                )
            },
        )
    }

    private fun episode(
        id: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        title: String = "S${seasonNumber}E$episodeNumber",
    ): VideoItemUIState {
        return VideoItemUIState(
            id = id,
            title = title,
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
    }

    private companion object {
        const val TARGET_EPISODE_ID = 804
    }
}
