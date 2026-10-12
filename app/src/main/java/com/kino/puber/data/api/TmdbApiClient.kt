package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.data.api.models.TmdbFindResponse
import com.kino.puber.data.api.models.TmdbSeasonDetailsResponse
import com.kino.puber.data.api.models.TmdbTvDetailsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import okhttp3.OkHttpClient
import java.io.IOException
import kotlinx.serialization.json.Json

class TmdbApiClient private constructor(
    private val httpClient: HttpClient,
) {

    /**
     * Built on the app's shared OkHttp client so this API resolves through the same DNS-over-HTTPS
     * setup, connection pool and cache as everything else. A second engine of its own would answer
     * to whatever the device's resolver says, which is exactly what that setup exists to avoid.
     */
    constructor(okHttpClient: OkHttpClient) : this(createHttpClient(okHttpClient))

    val isConfigured: Boolean
        get() = BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()

    /**
     * TV id for [imdbId]. null = TMDB has no TV match; failure = the request failed
     * (non-2xx, network, decoding).
     */
    suspend fun findTvByImdbId(imdbId: String): Result<Int?> = runCatchingCancellable {
        find(imdbId).tvResults?.firstOrNull()?.id
    }

    suspend fun getTvDetails(seriesId: Int): Result<TmdbTvDetailsResponse> = runCatchingCancellable {
        httpClient.get("${BASE_URL}tv/$seriesId")
            .also { it.requireSuccess() }
            .body()
    }

    suspend fun getTvSeasonDetails(
        seriesId: Int,
        seasonNumber: Int,
    ): Result<TmdbSeasonDetailsResponse> = runCatchingCancellable {
        httpClient.get("${BASE_URL}tv/$seriesId/season/$seasonNumber")
            .also { it.requireSuccess() }
            .body()
    }

    /**
     * TV id, else movie id, for [imdbId]. null = TMDB has no match; failure = the request
     * failed (non-2xx, network, decoding).
     */
    suspend fun findByImdbId(imdbId: String): Result<Int?> = runCatchingCancellable {
        find(imdbId).let { it.tvResults?.firstOrNull()?.id ?: it.movieResults?.firstOrNull()?.id }
    }

    private suspend fun find(imdbId: String): TmdbFindResponse =
        httpClient.get("${BASE_URL}find/${imdbId.withTmdbPrefix()}") {
            parameter("external_source", "imdb_id")
        }
            .also { it.requireSuccess() }
            .body()

    private fun String.withTmdbPrefix(): String =
        if (startsWith("tt", ignoreCase = true)) this else "tt$this"

    private fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            throw TmdbApiException(status.value)
        }
    }

    internal companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3/"

        fun forTesting(httpClient: HttpClient): TmdbApiClient = TmdbApiClient(httpClient)

        private fun createHttpClient(okHttpClient: OkHttpClient): HttpClient = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            install(DefaultRequest) {
                url(BASE_URL)
                headers.append("Authorization", "Bearer ${BuildConfig.TMDB_READ_ACCESS_TOKEN}")
                headers.append("Accept", "application/json")
            }
        }
    }
}

class TmdbApiException(val statusCode: Int) : IOException("TMDB request failed with HTTP $statusCode")
