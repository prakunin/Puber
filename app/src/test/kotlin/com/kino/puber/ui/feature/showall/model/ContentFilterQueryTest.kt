package com.kino.puber.ui.feature.showall.model

import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import com.kino.puber.domain.model.TabType
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.contentlist.model.TabTypeConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ContentFilterQueryTest {

    private val allMovie: SectionConfig = TabTypeConfig.browseEntryFor(TabType.Movies)!!.config
    private val all4k: SectionConfig = TabTypeConfig.browseEntryFor(TabType.For4k)!!.config

    @Test
    fun applyTo_ascending_sendsTheBareFieldName() {
        val query = ContentFilter(sort = SortField.YEAR, sortDirection = SortDirection.ASC).applyTo(allMovie)

        assertEquals("year", query.sort)
    }

    @Test
    fun applyTo_descendingWithGenre_keepsIdTypeAndQuality() {
        val filter = ContentFilter(genre = Genre(9, "Драма"), sort = SortField.RATING)

        listOf(allMovie, all4k).forEach { base ->
            val query = filter.applyTo(base)

            assertEquals("rating-", query.sort, base.id)
            assertEquals("9", query.genre, base.id)
            assertEquals(base.id, query.id)
            assertEquals(base.type, query.type)
            assertEquals(base.quality, query.quality)
            assertEquals(base.animeFilterMode, query.animeFilterMode)
        }
    }

    @Test
    fun applyTo_defaultFilter_returnsTheBaseConfig() {
        assertFalse(ContentFilter().isActive)
        assertEquals(allMovie, ContentFilter().applyTo(allMovie))
    }
}
