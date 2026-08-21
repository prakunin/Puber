package com.kino.puber.data.api.network.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class LatencySample(
    val pingMillis: Long,
    val jitterMillis: Long,
)

fun interface LatencyProbe {
    suspend fun roundTripMillis(url: String): Long
}

/** Measures HTTP time to the first response byte without downloading the test payload. */
class OkHttpLatencyProbe(
    okHttpClient: OkHttpClient,
    timeout: Duration = DEFAULT_TIMEOUT,
    private val clock: () -> Long = System::currentTimeMillis,
) : LatencyProbe {

    private val client = okHttpClient.newBuilder()
        .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun roundTripMillis(url: String): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val startedAt = clock()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Latency probe refused")
            response.body.source().readByte()
            (clock() - startedAt).coerceAtLeast(0L)
        }
    }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 1.seconds
    }
}

internal fun latencySampleOf(roundTripsMillis: List<Long>): LatencySample? {
    if (roundTripsMillis.isEmpty()) return null
    val jitterSamples = roundTripsMillis.zipWithNext { first, second -> abs(second - first) }
    return LatencySample(
        pingMillis = roundTripsMillis.median(),
        jitterMillis = jitterSamples.medianOrZero(),
    )
}

private fun List<Long>.medianOrZero(): Long = if (isEmpty()) 0L else median()

private fun List<Long>.median(): Long {
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2
    }
}
