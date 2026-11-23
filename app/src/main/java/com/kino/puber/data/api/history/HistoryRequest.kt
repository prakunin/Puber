package com.kino.puber.data.api.history

internal class HistoryRequest private constructor(
    val path: String,
    val query: Map<String, String>,
    val cacheControl: String,
) {
    fun resolveUrl(baseUrl: String): String {
        require(baseUrl.isNotBlank()) { "History base URL must not be blank" }
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    companion object {
        const val NO_STORE_CACHE_CONTROL = "no-store"

        /** The server's largest page. */
        private const val MAX_PER_PAGE = 50

        fun page(page: Int, perPage: Int? = null): HistoryRequest {
            require(page > 0) { "History page must be positive" }
            require(perPage == null || perPage in 1..MAX_PER_PAGE) { "History perpage must be within 1..$MAX_PER_PAGE" }
            return HistoryRequest(
                path = "history",
                query = buildMap {
                    put("page", page.toString())
                    if (perPage != null) put("perpage", perPage.toString())
                },
                cacheControl = NO_STORE_CACHE_CONTROL,
            )
        }

        fun clearExactMedia(mediaId: Int): HistoryRequest {
            require(mediaId > 0) { "History media ID must be positive" }
            return HistoryRequest(
                path = "history/clear-for-media",
                query = mapOf("id" to mediaId.toString()),
                cacheControl = NO_STORE_CACHE_CONTROL,
            )
        }
    }
}
