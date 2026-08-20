package com.kino.puber.ui.feature.details.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.input.key.Key
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
// Deliberately a middle season, not the last one. With the target in season 8 of 8 this test
// passed even while the initial scroll used an index from the wrong list: `scrollToItem` clamps,
// and clamping happened to land on the right season. A middle season has nothing to clamp to.
private const val TARGET_EPISODE = "S4E4 target"

private const val HERO_TITLE = "Hero Title"
private const val HERO_DESCRIPTION = "Short plot synopsis."
private const val HERO_YEAR = "2024"
private const val HERO_GENRES = "Drama"
private const val HERO_COUNTRY = "US"
private const val HERO_DURATION = "1h 40m"
private const val HERO_FACTS_LINE = "4K · 16+"
private const val HERO_CREDITS_LINE = "Режиссёр: Иван Иванов"
private val HERO_META_LINE = listOf(HERO_YEAR, HERO_GENRES, HERO_COUNTRY, HERO_DURATION).joinToString(" · ")
private const val SIMILAR_ITEM_TITLE = "A similar film"
private const val FIRST_SEASON_EPISODE = "First season episode"
private const val SECOND_SEASON_EPISODE = "Second season episode"

internal class DetailsScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun episodeGridFocusesExactTargetInLaterSeason() {
        val episodes = episodes()
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes,
                        initialEpisodeFocusId = TARGET_EPISODE_ID,
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Season 4").assertIsDisplayed()
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
        // A film: no season list, so the buttons are the only thing that can hold focus. Passing a
        // series here would contradict `episodeGridWithoutTargetFocusesDefaultEpisode` below, which
        // asserts that a series hands focus to its episodes -- the two used to be told apart by the
        // seasons panel's visibility flag, and that flag is gone.
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = null,
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
    fun episodeGridWithoutTargetFocusesDefaultEpisode() {
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
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

    @Test
    fun downFromTheButtonsReachesTheSimilarItems() {
        // The page that used to sit between these two caught this key. Nothing on the device can
        // exercise this: every similar-items answer this account returns is empty, so the page
        // below never appears there. This test is the only thing holding the behaviour.
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = episodes(),
                        initialEpisodeFocusId = null,
                        title = HERO_TITLE,
                        description = HERO_DESCRIPTION,
                        similarItems = listOf(
                            VideoItemUIState(
                                id = 7,
                                title = SIMILAR_ITEM_TITLE,
                                imageUrl = "",
                                bigImageUrl = "",
                                showTitle = true,
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsDisplayed()

        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionDown) }
        // Safe to wait for idleness here, unlike anywhere near a long plot: HERO_DESCRIPTION fits,
        // so the description's own scroll never starts and the pager's animation is all there is.
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SIMILAR_ITEM_TITLE).assertIsDisplayed()
    }

    @Test
    fun downFromTheButtonsOnASeriesReachesTheSeasonsRatherThanThePageBelow() {
        // The other half of the same handover. The hero keeps a DOWN handler for films, where it is
        // the bottom of the page; on a series that handler must not fire, or the buttons would jump
        // straight past every season to the similar items.
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = twoSeasonEpisodes(),
                        initialEpisodeFocusId = null,
                        title = HERO_TITLE,
                        description = HERO_DESCRIPTION,
                        similarItems = listOf(
                            VideoItemUIState(
                                id = 7,
                                title = SIMILAR_ITEM_TITLE,
                                imageUrl = "",
                                bigImageUrl = "",
                                showTitle = true,
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithText(PRIMARY_ACTION).assertIsDisplayed()

        composeRule.onNodeWithText(PRIMARY_ACTION).performKeyInput { pressKey(Key.DirectionDown) }

        // Focused, not merely displayed: the season is on screen before the key is pressed, so
        // asserting that it is visible would pass with the handover deleted.
        composeRule.waitUntil {
            composeRule
                .onNodeWithText(FIRST_SEASON_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(FIRST_SEASON_EPISODE).assertIsFocused()
    }

    @Test
    fun downFromTheLastSeasonReachesTheSimilarItems() {
        // On a series the hero is no longer the bottom of the page -- the season list is -- so its
        // own DOWN handler must step aside and let the grid catch the key once the last season has
        // focus. Nothing on the device can exercise this: every similar-items answer this account
        // returns is empty, so the page below never appears there. This test is the only thing
        // holding the behaviour.
        composeRule.setContent {
            PuberTheme {
                DetailsScreenContent(
                    state = content(
                        episodes = twoSeasonEpisodes(),
                        initialEpisodeFocusId = null,
                        title = HERO_TITLE,
                        description = HERO_DESCRIPTION,
                        similarItems = listOf(
                            VideoItemUIState(
                                id = 7,
                                title = SIMILAR_ITEM_TITLE,
                                imageUrl = "",
                                bigImageUrl = "",
                                showTitle = true,
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithText(FIRST_SEASON_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }

        // DOWN from the first season's episode reaches the second season -- there is no page-below
        // handoff yet, because this is not the last row.
        composeRule.onNodeWithText(FIRST_SEASON_EPISODE).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SECOND_SEASON_EPISODE).assertIsDisplayed()

        // DOWN again, now from the last season, reaches the page below.
        composeRule.onNodeWithText(SECOND_SEASON_EPISODE).performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(SIMILAR_ITEM_TITLE).assertIsDisplayed()

        // And UP comes back to the episode that was left, not to the first season and not to the
        // buttons. Without the handover this test would still have passed on the two assertions
        // above, which is what the review pointed out.
        composeRule.onNodeWithText(SIMILAR_ITEM_TITLE).performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.waitUntil {
            composeRule
                .onNodeWithText(SECOND_SEASON_EPISODE)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithText(SECOND_SEASON_EPISODE).assertIsFocused()
    }

    private fun twoSeasonEpisodes(): VideoGridUIState {
        return VideoGridUIState(
            list = listOf(
                VideoGridItemUIState.Title("Season 1"),
                VideoGridItemUIState.Items(
                    items = listOf(
                        episode(id = 101, seasonNumber = 1, episodeNumber = 1, title = FIRST_SEASON_EPISODE),
                    ),
                    rowKey = "season_1",
                ),
                VideoGridItemUIState.Title("Season 2"),
                VideoGridItemUIState.Items(
                    items = listOf(
                        episode(id = 201, seasonNumber = 2, episodeNumber = 1, title = SECOND_SEASON_EPISODE),
                    ),
                    rowKey = "season_2",
                ),
            ),
        )
    }

    private fun content(
        episodes: VideoGridUIState?,
        initialEpisodeFocusId: Int?,
        title: String = "Synthetic details",
        description: String = "",
        year: String = "",
        genres: String = "",
        duration: String = "",
        country: String = "",
        factsLine: String = "",
        creditsLine: String = "",
        similarItems: List<VideoItemUIState> = emptyList(),
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
            episodes = episodes,
            initialEpisodeFocusId = initialEpisodeFocusId,
            similarItems = similarItems,
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
                            if (seasonNumber == 4) {
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
