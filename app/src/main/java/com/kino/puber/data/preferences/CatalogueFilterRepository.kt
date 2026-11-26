package com.kino.puber.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.ContentFilter
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The filter a browse grid was left with, kept per grid config id so a reopened grid or a restarted
 * app starts on it. Only genre, sort, country, the year bounds and the finished flag are stored; an entry that cannot be decoded reads as no
 * filter, so a renamed enum or a damaged value never stops a grid from opening.
 */
class CatalogueFilterRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(configId: String): ContentFilter {
        val raw = prefs.getString(key(configId), null) ?: return ContentFilter()
        val stored = runCatching { json.decodeFromString<StoredFilter>(raw) }.getOrNull()
            ?: return ContentFilter()
        val genreId = stored.genreId
        val genreTitle = stored.genreTitle
        val countryId = stored.countryId
        val countryTitle = stored.countryTitle
        val yearFrom = stored.yearFrom
        val yearTo = stored.yearTo
        return ContentFilter(
            genre = if (genreId != null && genreTitle != null) Genre(genreId, genreTitle) else null,
            country = if (countryId != null && countryTitle != null) Country(countryId, countryTitle) else null,
            yearRange = if (yearFrom != null && yearTo != null && yearFrom <= yearTo) yearFrom..yearTo else null,
            finished = if (stored.finished == true) true else null,
            sort = SortField.entries.find { it.name == stored.sort } ?: SortField.UPDATED,
            sortDirection = SortDirection.entries.find { it.name == stored.direction } ?: SortDirection.DESC,
        )
    }

    fun write(configId: String, filter: ContentFilter) {
        if (!filter.isActive) {
            clear(configId)
            return
        }
        val stored = StoredFilter(
            genreId = filter.genre?.id,
            genreTitle = filter.genre?.title,
            sort = filter.sort.name,
            direction = filter.sortDirection.name,
            countryId = filter.country?.id,
            countryTitle = filter.country?.title,
            yearFrom = filter.yearRange?.first,
            yearTo = filter.yearRange?.last,
            finished = filter.finished,
        )
        prefs.edit { putString(key(configId), json.encodeToString(stored)) }
    }

    fun clear(configId: String) {
        prefs.edit { remove(key(configId)) }
    }

    private fun key(configId: String) = KEY_PREFIX + configId

    @Serializable
    private data class StoredFilter(
        val genreId: Int? = null,
        val genreTitle: String? = null,
        val sort: String? = null,
        val direction: String? = null,
        val countryId: Int? = null,
        val countryTitle: String? = null,
        val yearFrom: Int? = null,
        val yearTo: Int? = null,
        val finished: Boolean? = null,
    )

    private companion object {
        const val PREFS_NAME = "catalogue_filters"
        const val KEY_PREFIX = "filter_"
    }
}
