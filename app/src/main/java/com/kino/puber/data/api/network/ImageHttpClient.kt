package com.kino.puber.data.api.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.EventListener
import okhttp3.OkHttpClient

/** A plain Ktor client over [okHttpClient] for image fetches: no plugins, no KinoPub auth or headers. */
fun createImageHttpClient(
    okHttpClient: OkHttpClient,
    listener: EventListener.Factory? = null,
): HttpClient = HttpClient(OkHttp) {
    engine {
        preconfigured = listener?.let { okHttpClient.newBuilder().eventListenerFactory(it).build() }
            ?: okHttpClient
    }
}
