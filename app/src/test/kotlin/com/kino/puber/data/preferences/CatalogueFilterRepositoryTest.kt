package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import com.kino.puber.util.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CatalogueFilterRepositoryTest {

    private val preferences = FakeSharedPreferences()
    private val repository: CatalogueFilterRepository = run {
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(any(), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        CatalogueFilterRepository(context)
    }

    @Test
    fun write_thenRead_returnsGenreSortAndDirection() {
        val filter = ContentFilter(
            genre = Genre(9, "Драма"),
            sort = SortField.YEAR,
            sortDirection = SortDirection.ASC,
        )

        repository.write("all_movie", filter)

        assertEquals(filter, repository.read("all_movie"))
    }

    @Test
    fun write_inactiveFilterRemovesTheEntry() {
        repository.write("all_movie", ContentFilter(sort = SortField.YEAR))
        assertTrue(preferences.values.containsKey("filter_all_movie"))

        repository.write("all_movie", ContentFilter())

        assertFalse(preferences.values.containsKey("filter_all_movie"))
    }

    @Test
    fun clear_removesOnlyThatGrid() {
        repository.write("all_movie", ContentFilter(sort = SortField.YEAR))
        repository.write("all_serial", ContentFilter(sort = SortField.RATING))

        repository.clear("all_movie")

        assertFalse(preferences.values.containsKey("filter_all_movie"))
        assertTrue(preferences.values.containsKey("filter_all_serial"))
    }

    @Test
    fun read_malformedJson_yieldsNoFilter() {
        preferences.values["filter_all_movie"] = "{not json"

        assertEquals(ContentFilter(), repository.read("all_movie"))
    }

    @Test
    fun read_unknownKeys_areIgnored() {
        preferences.values["filter_all_movie"] =
            """{"genreId":9,"genreTitle":"Драма","sort":"YEAR","direction":"ASC","country":3}"""

        assertEquals(
            ContentFilter(Genre(9, "Драма"), sort = SortField.YEAR, sortDirection = SortDirection.ASC),
            repository.read("all_movie"),
        )
    }

    @Test
    fun read_unknownSortField_fallsBackToUpdatedKeepsTheRest() {
        preferences.values["filter_all_movie"] =
            """{"genreId":9,"genreTitle":"Драма","sort":"METACRITIC","direction":"ASC"}"""

        assertEquals(
            ContentFilter(Genre(9, "Драма"), sort = SortField.UPDATED, sortDirection = SortDirection.ASC),
            repository.read("all_movie"),
        )
    }

    @Test
    fun read_unknownDirection_fallsBackToDesc() {
        preferences.values["filter_all_movie"] = """{"sort":"YEAR","direction":"UP"}"""

        assertEquals(SortDirection.DESC, repository.read("all_movie").sortDirection)
    }

    @Test
    fun read_genreWithoutTitle_isDropped() {
        preferences.values["filter_all_movie"] = """{"genreId":9}"""

        assertNull(repository.read("all_movie").genre)
    }
}
