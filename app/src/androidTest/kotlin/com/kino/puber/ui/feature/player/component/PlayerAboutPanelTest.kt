package com.kino.puber.ui.feature.player.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.player.model.PlayerAboutUIState
import org.junit.Rule
import org.junit.Test

private const val TITLE = "Начало"
private const val META_LINE = "2010 · 2 ч 28 мин · США · 16+"
private const val GENRES_LINE = "Фантастика, боевик"
private const val RATINGS_LINE = "IMDb 8.8 · Кинопоиск 8.7"
private const val DESCRIPTION = "Кобб — талантливый вор, лучший из лучших в опасном искусстве извлечения."
private const val DIRECTOR = "Кристофер Нолан"
private const val CAST = "Леонардо ДиКаприо, Джозеф Гордон-Левитт"

/**
 * The panel behind the About button. Before it carried more than the plot, an item whose synopsis
 * the API had never filled in had no door at all, so what is asserted here is as much which rows
 * appear as that they are legible.
 */
internal class PlayerAboutPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun panelShowsTheTitleFactsPlotAndCredits() {
        composeRule.setContent {
            PuberTheme {
                PlayerAboutPanel(visible = true, isMovie = true, about = fullAbout())
            }
        }

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(META_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(GENRES_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(RATINGS_LINE).assertIsDisplayed()
        composeRule.onNodeWithText(DESCRIPTION).assertIsDisplayed()
        composeRule.onNodeWithText(DIRECTOR).assertIsDisplayed()
        composeRule.onNodeWithText(CAST).assertIsDisplayed()
    }

    /** An item with credits but no synopsis still has a panel worth opening. */
    @Test
    fun panelOpensOnTheFactsWhenTheApiSentNoPlot() {
        composeRule.setContent {
            PuberTheme {
                PlayerAboutPanel(
                    visible = true,
                    isMovie = true,
                    about = fullAbout().copy(description = null),
                )
            }
        }

        composeRule.onNodeWithText(CAST).assertIsDisplayed()
        composeRule.onAllNodesWithText(DESCRIPTION).assertCountEquals(0)
    }

    /** Nothing but a title is nothing to read: the panel stays shut rather than opening blank. */
    @Test
    fun panelStaysShutWhenTheItemCarriesNothingButItsTitle() {
        composeRule.setContent {
            PuberTheme {
                PlayerAboutPanel(
                    visible = true,
                    isMovie = true,
                    about = PlayerAboutUIState(
                        title = TITLE,
                        metaLine = "",
                        genresLine = "",
                        ratingsLine = "",
                        description = null,
                        director = null,
                        cast = null,
                    ),
                )
            }
        }

        composeRule.onAllNodesWithText(TITLE).assertCountEquals(0)
    }

    private fun fullAbout() = PlayerAboutUIState(
        title = TITLE,
        metaLine = META_LINE,
        genresLine = GENRES_LINE,
        ratingsLine = RATINGS_LINE,
        description = DESCRIPTION,
        director = DIRECTOR,
        cast = CAST,
    )
}
