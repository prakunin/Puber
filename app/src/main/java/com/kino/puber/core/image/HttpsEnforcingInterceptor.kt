package com.kino.puber.core.image

import coil3.intercept.Interceptor
import coil3.request.ImageResult

internal class HttpsEnforcingInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data is String && data.startsWith("http://")) {
            val newRequest = chain.request.newBuilder()
                .data(data.replaceFirst("http://", "https://"))
                .build()
            return chain.withRequest(newRequest).proceed()
        }
        return chain.proceed()
    }
}
