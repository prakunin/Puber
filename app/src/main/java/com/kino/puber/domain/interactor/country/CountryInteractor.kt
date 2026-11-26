package com.kino.puber.domain.interactor.country

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.cache.CacheKeys
import com.kino.puber.data.cache.CacheTtl
import com.kino.puber.data.cache.ContentCacheRepository
import kotlinx.serialization.builtins.ListSerializer

class CountryInteractor(
    private val api: KinoPubApiClient,
    private val contentCache: ContentCacheRepository,
) {

    suspend fun getCountries(): Result<List<Country>> {
        return runCatchingCancellable {
            contentCache.getPayload(
                key = CacheKeys.Countries,
                serializer = ListSerializer(Country.serializer()),
                ttl = CacheTtl.Countries,
            ) {
                api.getCountries().getOrThrow()
            }
        }
    }
}
