package com.kino.puber.data.api.network.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How much of a bounded download arrived, and how long it took. */
data class ThroughputSample(val bytes: Long, val elapsedMillis: Long) {

    /**
     * Zero rather than infinity when no time passed. A download the clock could not separate from
     * its own start is a measurement that failed, and reporting it as an unbounded link would put
     * the largest number on the screen for the least evidence.
     */
    val bitsPerSecond: Double
        get() = if (elapsedMillis <= 0L) {
            0.0
        } else {
            bytes.toDouble() * BITS_PER_BYTE * MILLIS_PER_SECOND / elapsedMillis
        }
}

/** Downloads a bounded prefix of a URL and reports nothing but its size and its duration. */
fun interface BoundedDownloader {
    suspend fun measure(
        url: String,
        maxBytes: Long,
        onProgress: (ThroughputSample) -> Unit,
    ): ThroughputSample
}

/**
 * Measures against the shared client, so the download travels the same DNS-over-HTTPS path every
 * real request takes. Only the call timeout is per-call, through `newBuilder()`, the way
 * [com.kino.puber.data.api.network.HttpEndpointProbe] already does it — the singleton's own
 * configuration is never touched, because a diagnostic must not be able to change how the app talks
 * to the network.
 *
 * What the elapsed time covers, because the rate it feeds is read as a quality claim: the clock
 * starts before the call is made, so name resolution, the TCP and TLS handshakes and however long
 * the server thinks before its first byte are all divided into the bytes that arrive. The figure is
 * therefore how fast the video *arrives*, not how fast the link carries it, and on a high-latency
 * link it reads lower than the link's own capacity. That is the number worth reporting on a screen
 * about stuttering playback — a player waits through setup too — but it is not a link speed test.
 */
class OkHttpBoundedDownloader(
    okHttpClient: OkHttpClient,
    timeout: Duration = DEFAULT_TIMEOUT,
    private val clock: () -> Long = System::currentTimeMillis,
) : BoundedDownloader {

    private val client = okHttpClient.newBuilder()
        .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun measure(
        url: String,
        maxBytes: Long,
        onProgress: (ThroughputSample) -> Unit,
    ): ThroughputSample =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${maxBytes - 1}")
                .get()
                .build()

            val startedAt = clock()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Bounded download refused")
                // A server that ignores Range answers 200 with the whole file; the cap is what
                // stops us either way, so the status is not treated as a failure.
                var lastProgressAt = startedAt
                response.body.source().readAtMost(
                    maxBytes = maxBytes,
                    isActive = { isActive },
                    onBytesRead = { downloadedBytes ->
                        val now = clock()
                        if (now - lastProgressAt >= PROGRESS_INTERVAL_MILLIS || downloadedBytes == maxBytes) {
                            onProgress(
                                ThroughputSample(
                                    bytes = downloadedBytes,
                                    elapsedMillis = now - startedAt,
                                )
                            )
                            lastProgressAt = now
                        }
                    },
                )
            }
            ThroughputSample(bytes = bytes, elapsedMillis = clock() - startedAt)
        }

    private companion object {
        val DEFAULT_TIMEOUT: Duration = 20.seconds
    }
}

/** How much is pulled off the socket between two cancellation checks. */
internal const val DOWNLOAD_CHUNK_BYTES = 64L * 1024L

/**
 * Reads at most [maxBytes], discarding everything it reads.
 *
 * Nothing is kept because nothing may be: the media probe's URL is authenticated, and its body is
 * somebody's film. Only the count leaves this function.
 */
internal fun BufferedSource.readAtMost(maxBytes: Long, isActive: () -> Boolean): Long {
    return readAtMost(maxBytes, isActive) {}
}

internal fun BufferedSource.readAtMost(
    maxBytes: Long,
    isActive: () -> Boolean,
    onBytesRead: (Long) -> Unit,
): Long {
    val sink = Buffer()
    var total = 0L
    while (total < maxBytes && isActive()) {
        val read = try {
            read(sink, minOf(DOWNLOAD_CHUNK_BYTES, maxBytes - total))
        } catch (error: IOException) {
            if (!isActive()) throw CancellationException("Speed test cancelled", error)
            // A speed test is time-bounded, not size-dependent. When the per-call deadline ends a
            // healthy but slow transfer, the bytes that did arrive still form a valid sample.
            if (total > 0L) -1L else throw error
        }
        if (read == -1L) break
        total += read
        sink.clear()
        onBytesRead(total)
    }
    return total
}

private const val BITS_PER_BYTE = 8
private const val MILLIS_PER_SECOND = 1_000
private const val PROGRESS_INTERVAL_MILLIS = 500L
