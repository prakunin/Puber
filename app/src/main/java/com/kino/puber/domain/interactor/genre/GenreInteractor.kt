package com.kino.puber.domain.interactor.genre

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Genre
import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import com.kino.puber.data.cache.ContentCacheRepository
import kotlinx.serialization.builtins.ListSerializer

class GenreInteractor(
    private val api: KinoPubApiClient,
    private val contentCache: ContentCacheRepository,
) {

    suspend fun getGenres(type: String? = null): Result<List<Genre>> {
        return runCatchingCancellable {
            contentCache.getPayload(
                key = CacheKeys.genres(type),
                serializer = ListSerializer(Genre.serializer()),
                ttl = CacheTtl.Genres,
            ) {
                api.getGenres(type).getOrThrow()
            }
        }
    }
}
