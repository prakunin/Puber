package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** The country, year and finished filters of a browse grid reach the server query and its cache key. */
internal class ContentListInteractorBrowseFilterTest : ContentListInteractorTestFixture() {

    @Test
    fun filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType() = runTest {
        val filtered = config(AnimeFilterMode.None).copy(country = 1, year = "2010-2019", finished = true)
        coEvery { api.getItems("movie", "updated", 1, null, null, null, 1, "2010-2019", true) } returns
            Result.success(page(item(id = 1, title = "Movie")))

        observedPages(filtered)

        coVerify(exactly = 1) { api.getItems("movie", "updated", 1, null, null, null, 1, "2010-2019", true) }
    }

    @Test
    fun firstPageCache_withNoBrowseFilter_keepsTheKeyShapeOfBeforeTheFilters() = runTest {
        val config = config(AnimeFilterMode.None)
        coEvery { api.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(page(item(id = 1, title = "Movie")))
        val legacyKey = ContentCacheRepository.queryKey(
            CacheKeys.section(
                listOf(
                    config.id, "", config.type, "", config.sort, config.quality, "", config.requiredGenreId,
                    config.animeFilterMode, true, false,
                ).joinToString(separator = "_"),
            ),
        )

        observedPages(config)

        assertNotNull(payloadStore.read(legacyKey))
    }

    @Test
    fun firstPageCache_isSeparatedByCountryYearAndFinished() = runTest {
        val base = config(AnimeFilterMode.None)
        val variants = listOf(
            base,
            base.copy(country = 1),
            base.copy(year = "2010-2019"),
            base.copy(finished = true),
        )
        coEvery { api.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(page(item(id = 1, title = "Movie")))

        variants.forEach { observedPages(it) }

        coVerify(exactly = 4) { api.getItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
