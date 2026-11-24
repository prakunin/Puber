package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.R

import com.kino.puber.data.api.models.ANIME_GENRE_ID
import com.kino.puber.data.api.models.CARTOON_GENRE_ID
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.showall.model.ShowAllTitle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabTypeConfigTest {

    @Test
    fun moviesAndSeriesSections_followAnimePreference() {
        listOf(TabType.Movies, TabType.Series).forEach { tabType ->
            val sections = TabTypeConfig.sectionsFor(tabType)

            assertTrue(sections.isNotEmpty())
            assertEquals(
                setOf(AnimeFilterMode.FollowPreference),
                sections.map(SectionConfig::animeFilterMode).toSet(),
            )
        }
    }

    @Test
    fun cartoonsSections_useCartoonGenreAndAlwaysExcludeAnime() {
        val sections = TabTypeConfig.sectionsFor(TabType.Cartoons)

        assertEquals(
            listOf("fresh_cartoon", "popular_cartoon", "all_cartoon"),
            sections.map(SectionConfig::id),
        )
        assertEquals(
            listOf(
                R.string.content_section_fresh,
                R.string.content_section_popular,
                R.string.content_section_all,
            ),
            sections.map(SectionConfig::titleRes),
        )
        assertEquals(
            listOf("fresh", null, null),
            sections.map(SectionConfig::shortcut),
        )
        assertEquals(
            listOf(
                listOf(ItemType.MOVIE, ItemType.SERIAL),
                emptyList(),
                emptyList(),
            ),
            sections.map(SectionConfig::shortcutTypes),
        )
        assertEquals(
            listOf(null, "views-", "updated-"),
            sections.map(SectionConfig::sort),
        )
        assertEquals(
            listOf(null, null, null),
            sections.map(SectionConfig::type),
        )
        assertEquals(
            listOf(null, CARTOON_GENRE_ID.toString(), CARTOON_GENRE_ID.toString()),
            sections.map(SectionConfig::genre),
        )
        assertEquals(
            listOf(CARTOON_GENRE_ID, null, null),
            sections.map(SectionConfig::requiredGenreId),
        )
        assertEquals(
            listOf(
                AnimeFilterMode.Exclude,
                AnimeFilterMode.Exclude,
                AnimeFilterMode.Exclude,
            ),
            sections.map(SectionConfig::animeFilterMode),
        )
    }

    @Test
    fun animeSections_useAnimeGenreAndOnlyMode() {
        val sections = TabTypeConfig.sectionsFor(TabType.Anime)

        assertEquals(
            listOf("fresh_anime", "popular_anime", "all_anime"),
            sections.map(SectionConfig::id),
        )
        assertEquals(
            listOf(
                R.string.content_section_fresh,
                R.string.content_section_popular,
                R.string.content_section_all,
            ),
            sections.map(SectionConfig::titleRes),
        )
        assertEquals(
            listOf("fresh", null, null),
            sections.map(SectionConfig::shortcut),
        )
        assertEquals(
            listOf(
                listOf(ItemType.MOVIE, ItemType.SERIAL),
                emptyList(),
                emptyList(),
            ),
            sections.map(SectionConfig::shortcutTypes),
        )
        assertEquals(
            listOf(null, "views-", "updated-"),
            sections.map(SectionConfig::sort),
        )
        assertEquals(
            listOf(null, null, null),
            sections.map(SectionConfig::type),
        )
        assertEquals(
            listOf(null, ANIME_GENRE_ID.toString(), ANIME_GENRE_ID.toString()),
            sections.map(SectionConfig::genre),
        )
        assertEquals(
            listOf(ANIME_GENRE_ID, null, null),
            sections.map(SectionConfig::requiredGenreId),
        )
        assertEquals(
            listOf(
                AnimeFilterMode.Only,
                AnimeFilterMode.Only,
                AnimeFilterMode.Only,
            ),
            sections.map(SectionConfig::animeFilterMode),
        )
    }

    @Test
    fun heroConfigs_useHotShortcutAndDistinctGenreFilters() {
        val cartoons = TabTypeConfig.heroConfigsFor(TabType.Cartoons)
        val anime = TabTypeConfig.heroConfigsFor(TabType.Anime)

        assertEquals(listOf("movie", "serial"), cartoons.map(SectionConfig::type))
        cartoons.forEach { config ->
            assertEquals("hot", config.shortcut)
            assertEquals(CARTOON_GENRE_ID.toString(), config.genre)
            assertEquals(AnimeFilterMode.Exclude, config.animeFilterMode)
        }
        assertEquals(
            listOf("hero_hot_cartoon_movie", "hero_hot_cartoon_serial"),
            cartoons.map(SectionConfig::id),
        )

        assertEquals(listOf("movie", "serial"), anime.map(SectionConfig::type))
        anime.forEach { config ->
            assertEquals("hot", config.shortcut)
            assertEquals(ANIME_GENRE_ID.toString(), config.genre)
            assertEquals(AnimeFilterMode.Only, config.animeFilterMode)
        }
        assertEquals(
            listOf("hero_hot_anime_movie", "hero_hot_anime_serial"),
            anime.map(SectionConfig::id),
        )

        assertTrue(
            cartoons.none { it.id in TabTypeConfig.sectionsFor(TabType.Cartoons).map(SectionConfig::id) },
        )
        assertTrue(
            anime.none { it.id in TabTypeConfig.sectionsFor(TabType.Anime).map(SectionConfig::id) },
        )
    }

    @Test
    fun heroConfig_isAbsentForUnrelatedTabs() {
        TabType.entries
            .filterNot { it == TabType.Cartoons || it == TabType.Anime }
            .forEach { tabType ->
                assertEquals(
                    emptyList<SectionConfig>(),
                    TabTypeConfig.heroConfigsFor(tabType),
                    "$tabType unexpectedly generated a hero config",
                )
            }
    }

    @Test
    fun unrelatedCatalogTabs_doNotFilterAnime() {
        val unfilteredTabs = listOf(
            TabType.For4k,
            TabType.Concerts,
            TabType.DocMovies,
            TabType.DocSeries,
            TabType.TvShows,
        )

        unfilteredTabs.forEach { tabType ->
            val sections = TabTypeConfig.sectionsFor(tabType)

            assertTrue(sections.isNotEmpty())
            assertEquals(
                setOf(AnimeFilterMode.None),
                sections.map(SectionConfig::animeFilterMode).toSet(),
            )
        }
    }

    @Test
    fun nonCatalogTabs_haveNoGeneratedSections() {
        val configuredTabs = setOf(
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
            TabType.Anime,
            TabType.For4k,
            TabType.Concerts,
            TabType.DocMovies,
            TabType.DocSeries,
            TabType.TvShows,
        )

        TabType.entries
            .filterNot(configuredTabs::contains)
            .forEach { tabType ->
                assertTrue(
                    TabTypeConfig.sectionsFor(tabType).isEmpty(),
                    "$tabType unexpectedly generated catalog sections",
                )
            }
    }

    private val browseTabs = mapOf(
        TabType.Movies to R.string.browse_all_movies,
        TabType.Series to R.string.browse_all_series,
        TabType.Concerts to R.string.browse_all_concerts,
        TabType.DocMovies to R.string.browse_all_docmovies,
        TabType.DocSeries to R.string.browse_all_docseries,
        TabType.TvShows to R.string.browse_all_tvshows,
        TabType.For4k to R.string.browse_all_4k,
    )

    @Test
    fun browseEntryFor_labelsEachCatalogueTabWithItsOwnString() {
        browseTabs.forEach { (tabType, labelRes) ->
            assertEquals(labelRes, TabTypeConfig.browseEntryFor(tabType)?.labelRes, "$tabType")
        }
        assertEquals(
            browseTabs.size,
            browseTabs.keys.mapNotNull { TabTypeConfig.browseEntryFor(it)?.labelRes }.toSet().size,
        )
    }

    @Test
    fun browseEntryFor_isNullForTabsWithoutAnEntry() {
        TabType.entries.filterNot(browseTabs::containsKey).forEach { tabType ->
            assertNull(TabTypeConfig.browseEntryFor(tabType), "$tabType")
        }
    }

    @Test
    fun browseEntryFor_returnsTheAllSectionOfEachCatalogueTab() {
        browseTabs.keys.forEach { tabType ->
            val config = TabTypeConfig.browseEntryFor(tabType)?.config
            assertEquals(TabTypeConfig.sectionsFor(tabType).last(), config, "$tabType")
            assertTrue(config!!.id.startsWith("all_"), "$tabType")
            assertEquals("updated-", config.sort, "$tabType")
        }
        assertEquals("4k", TabTypeConfig.browseEntryFor(TabType.For4k)?.config?.quality)
        listOf(TabType.Movies, TabType.Series).forEach { tabType ->
            assertEquals(
                AnimeFilterMode.FollowPreference,
                TabTypeConfig.browseEntryFor(tabType)?.config?.animeFilterMode,
            )
        }
    }

    @Test
    fun showAllTitle_namesTheAllGridWithTheEntryLabel() {
        browseTabs.forEach { (tabType, labelRes) ->
            val entry = TabTypeConfig.browseEntryFor(tabType)!!
            assertEquals(
                ShowAllTitle(titleRes = labelRes),
                TabTypeConfig.showAllTitle(tabType, entry.config),
                "$tabType",
            )
        }
    }

    @Test
    fun showAllTitle_prefixesAShelfTitleWithTheTabTitle() {
        val fresh = TabTypeConfig.sectionsFor(TabType.Movies).first()

        val title = TabTypeConfig.showAllTitle(TabType.Movies, fresh)

        assertEquals(R.string.content_section_fresh, title.titleRes)
        assertEquals(TabType.Movies.title, title.prefixRes)
    }

    @Test
    fun isFilterable_trueForEveryBrowseEntryConfig() {
        TabType.entries.mapNotNull(TabTypeConfig::browseEntryFor).forEach { entry ->
            assertTrue(TabTypeConfig.isFilterable(entry.config), entry.config.id)
        }
    }

    @Test
    fun isFilterable_falseForShelvesAnd4kTypeGrids() {
        val filterable = TabType.entries.mapNotNull(TabTypeConfig::browseEntryFor).map { it.config.id }.toSet()
        val shelves = TabType.entries
            .flatMap(TabTypeConfig::sectionsFor)
            .filter { it.id !in filterable }
        assertTrue(shelves.any { it.id.startsWith("fresh_") })
        assertTrue(shelves.any { it.id.startsWith("hot_") })
        assertTrue(shelves.any { it.id.startsWith("popular_") })
        assertTrue(shelves.any { it.id.startsWith("4k_") })
        assertTrue(shelves.any { it.id == "popular_4k" })
        assertTrue(shelves.any { it.id == "all_cartoon" })
        assertTrue(shelves.any { it.id == "all_anime" })
        shelves.forEach { assertFalse(TabTypeConfig.isFilterable(it), it.id) }
    }

    @Test
    fun browseEntryConfigs_haveTheDefaultFilterQuery() {
        TabType.entries.mapNotNull(TabTypeConfig::browseEntryFor).forEach { entry ->
            assertEquals("updated-", entry.config.sort, entry.config.id)
            assertNull(entry.config.genre, entry.config.id)
        }
    }
}
