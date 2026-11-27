package com.kino.puber.playertestfixtures.server

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.Test

class HermeticTestServerTest {

    @Test
    fun routesMatchMethodPathAndQueryWithoutQueueOrderAndRedactValues() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "serial",
                        path = "/v1/items",
                        query = mapOf("type" to "serial"),
                        response = HermeticTestServer.text("serial"),
                    ),
                    server.route(
                        id = "movie",
                        path = "/v1/items",
                        query = mapOf("type" to "movie"),
                        response = HermeticTestServer.text("movie"),
                    ),
                ),
            )

            assertEquals("serial", request(server.url("/v1/items", mapOf("type" to "serial"))).text)
            assertEquals("movie", request(server.url("/v1/items", mapOf("type" to "movie"))).text)

            val snapshot = server.requestJournal
            assertTrue(snapshot.entries.all { "type" in it.queryKeys })
            assertTrue(snapshot.entries.none { it.path.contains("serial") || it.path.contains("movie") })
            assertEquals(0, snapshot.activeRequestCount)
        }
    }

    @Test
    fun sequenceRedirectAndRangeResponsesAreDeterministic() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "sequence",
                        path = "/sequence",
                        response = HermeticTestServer.sequence(
                            HermeticTestServer.text("first", status = 503),
                            HermeticTestServer.text("second"),
                        ),
                    ),
                    server.route(
                        id = "redirect",
                        path = "/redirect",
                        response = HermeticTestServer.redirect("/sequence"),
                    ),
                    server.route(
                        id = "range",
                        path = "/range",
                        response = ResponsePlan.Range("abcdef".toByteArray()),
                    ),
                ),
            )

            assertEquals(503, request(server.url("/sequence")).status)
            assertEquals(200, request(server.url("/sequence")).status)
            val redirect = request(server.url("/redirect"), followRedirects = false)
            assertEquals(302, redirect.status)
            assertEquals("/sequence", redirect.location)
            val range = request(server.url("/range"), range = "bytes=1-3")
            assertEquals(206, range.status)
            assertArrayEquals("bcd".toByteArray(), range.bytes)
            assertEquals("bytes 1-3/6", range.contentRange ?: range.headers.toString())
        }
    }

    @Test
    fun unknownRoutesAreRecordedAndDelayedResponsesTrackActiveRequests() {
        HermeticTestServer().use { server ->
            server.start()
            val gate = CountDownLatch(1)
            server.reset(
                listOf(
                    server.route(
                        id = "delayed",
                        path = "/delayed",
                        response = HermeticTestServer.delayed(gate, "payload".toByteArray()),
                    ),
                ),
            )

            val unknown = request(server.url("/unexpected?token=secret"), followRedirects = false)
            assertEquals(404, unknown.status)
            val requestThread = Thread {
                request(server.url("/delayed"))
            }
            requestThread.start()
            eventually { server.activeRequestCount == 1 }
            assertTrue(server.requestJournal.entries.none { it.path.contains("secret") })
            gate.countDown()
            requestThread.join(5_000)
            assertFalse(requestThread.isAlive)
            server.awaitQuiescence()
            assertEquals(1, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun resetStartsNewGenerationOnlyAfterRequestsQuiesce() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "first",
                        path = "/first",
                        response = HermeticTestServer.text("first"),
                    ),
                ),
            )
            val firstGeneration = server.generationId
            request(server.url("/first"))
            server.reset(
                listOf(
                    server.route(
                        id = "second",
                        path = "/second",
                        response = HermeticTestServer.text("second"),
                    ),
                ),
            )
            assertTrue(server.generationId > firstGeneration)
            assertEquals(0, server.requestJournal.entries.size)
            assertEquals(404, request(server.url("/first")).status)
            assertEquals(200, request(server.url("/second")).status)
        }
    }

    @Test
    fun concurrentResetCannotReplaceRoutesBeforeRequestAdmission() {
        HermeticTestServer().use { server ->
            server.start()
            val admissionSelected = CountDownLatch(1)
            val allowAdmission = CountDownLatch(1)
            val allowResponse = CountDownLatch(1)
            server.beforeRequestAdmissionForTest = {
                admissionSelected.countDown()
                check(allowAdmission.await(5, TimeUnit.SECONDS)) {
                    "Timed out waiting to admit selected route"
                }
            }
            server.reset(
                listOf(
                    server.route(
                        id = "delayed-old",
                        path = "/delayed-old",
                        response = HermeticTestServer.delayed(
                            gate = allowResponse,
                            body = "old".toByteArray(),
                        ),
                    ),
                    server.route(
                        id = "old-only",
                        path = "/old-only",
                        response = HermeticTestServer.text("old-only"),
                    ),
                ),
            )
            val firstGeneration = server.generationId
            val requestResult = AtomicReference<Response>()
            val requestFailure = AtomicReference<Throwable>()
            val requestThread = Thread {
                runCatching { request(server.url("/delayed-old")) }
                    .onSuccess(requestResult::set)
                    .onFailure(requestFailure::set)
            }
            requestThread.start()
            assertTrue(admissionSelected.await(5, TimeUnit.SECONDS))

            val resetFailure = AtomicReference<Throwable>()
            val resetThread = Thread {
                runCatching {
                    server.reset(
                        listOf(
                            server.route(
                                id = "new-only",
                                path = "/new-only",
                                response = HermeticTestServer.text("new-only"),
                            ),
                        ),
                    )
                }.onFailure(resetFailure::set)
            }
            resetThread.start()
            allowAdmission.countDown()
            resetThread.join(5_000)

            assertFalse(resetThread.isAlive)
            assertTrue(resetFailure.get() is IllegalStateException)
            assertEquals(firstGeneration, server.generationId)
            allowResponse.countDown()
            requestThread.join(5_000)
            assertFalse(requestThread.isAlive)
            assertNull(requestFailure.get())
            assertEquals("old", requestResult.get().text)
            assertEquals("old-only", request(server.url("/old-only")).text)
            assertEquals(404, request(server.url("/new-only")).status)

            server.awaitQuiescence()
            server.beforeRequestAdmissionForTest = {}
            server.reset(
                listOf(
                    server.route(
                        id = "new-only",
                        path = "/new-only",
                        response = HermeticTestServer.text("new-only"),
                    ),
                ),
            )

            assertTrue(server.generationId > firstGeneration)
            assertEquals(404, request(server.url("/old-only")).status)
            assertEquals("new-only", request(server.url("/new-only")).text)
            assertEquals(1, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun truncationAndDisconnectAreObservableWithoutLeakingActiveRequests() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "truncated",
                        path = "/truncated",
                        response = HermeticTestServer.truncated(
                            body = "complete".toByteArray(),
                            bytesToWrite = 2,
                        ),
                    ),
                    server.route(
                        id = "disconnected",
                        path = "/disconnected",
                        response = HermeticTestServer.disconnected(
                            body = "interrupted".toByteArray(),
                        ),
                    ),
                ),
            )

            val truncated = request(server.url("/truncated"))
            assertArrayEquals("co".toByteArray(), truncated.bytes)
            val disconnected = request(server.url("/disconnected"))
            assertTrue(disconnected.bytes.size < "interrupted".length)
            server.awaitQuiescence()
            assertEquals(0, server.requestJournal.activeRequestCount)
            assertEquals(2, server.requestJournal.entries.size)
            val outcomes = server.requestJournal.entries.associate { it.routeId to it.outcome }
            assertEquals(ResponseOutcome.Truncated(200), outcomes["truncated"])
            assertEquals(ResponseOutcome.Disconnected(200), outcomes["disconnected"])
        }
    }

    @Test
    fun sequenceOccurrenceIsFixedWhenTheRequestIsAdmitted() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "seq",
                        path = "/seq",
                        response = HermeticTestServer.sequence(
                            HermeticTestServer.text("first"),
                            HermeticTestServer.text("second"),
                        ),
                    ),
                ),
            )
            val admitted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val stalled = java.util.concurrent.atomic.AtomicBoolean(false)
            server.afterAdmissionForTest = {
                if (stalled.compareAndSet(false, true)) {
                    admitted.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            val firstText = AtomicReference<String?>()
            val firstRequest = Thread { firstText.set(request(server.url("/seq")).text) }
            firstRequest.start()
            assertTrue(admitted.await(5, TimeUnit.SECONDS), "first request never admitted")

            assertEquals("second", request(server.url("/seq")).text)
            release.countDown()
            firstRequest.join(5_000)

            assertEquals("first", firstText.get())
        }
    }

    @Test
    fun sequenceDelegatesToTheRangeResponseOfItsElement() {
        HermeticTestServer().use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "seq-range",
                        path = "/seq-range",
                        response = HermeticTestServer.sequence(ResponsePlan.Range("abcdef".toByteArray())),
                    ),
                ),
            )

            val response = request(server.url("/seq-range"), range = "bytes=2-5")

            assertEquals(206, response.status)
            assertEquals("cdef", response.text)
        }
    }

    @Test
    fun httpsServer_servesRangeRequest_toClientTrustingItsCertificate() {
        val held = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(held.certificate).build()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        HermeticTestServer(serverCertificates = serverCertificates).use { server ->
            server.start()
            server.reset(
                listOf(
                    server.route(
                        id = "range",
                        path = "/range",
                        response = ResponsePlan.Range("abcdef".toByteArray()),
                    ),
                ),
            )

            assertTrue(server.url("/").startsWith("https://"))
            client.newCall(Request.Builder().url(server.url("/range")).header("Range", "bytes=2-5").build())
                .execute()
                .use { response ->
                    assertEquals(206, response.code)
                    assertArrayEquals("cdef".toByteArray(), response.body.bytes())
                    assertEquals("bytes 2-5/6", response.header("Content-Range"))
                }
        }
    }

    private fun eventually(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline && !predicate()) Thread.yield()
        assertTrue(predicate())
    }

    @Test
    fun closeDoesNotStallARequestThatIsStillBeingDispatched() {
        val server = HermeticTestServer()
        server.start()
        server.reset(
            listOf(server.route(id = "ok", path = "/ok", response = HermeticTestServer.text("ok"))),
        )
        val dispatching = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        server.beforeDispatchForTest = {
            dispatching.countDown()
            proceed.await(5, TimeUnit.SECONDS)
        }
        val closeFailure = AtomicReference<Throwable?>()
        Socket(InetAddress.getByName("127.0.0.1"), URL(server.baseUrl).port).use { socket ->
            socket.getOutputStream().write("GET /ok HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
            assertTrue(dispatching.await(5, TimeUnit.SECONDS), "request never reached the dispatcher")

            // A request still inside dispatch() while close() runs (a loader that outlives the
            // controller release): close() waits for that serving task, so it must not hold
            // the server lock the dispatcher needs to finish.
            val startedAt = System.nanoTime()
            val closer = Thread {
                try {
                    server.close()
                } catch (error: Throwable) {
                    closeFailure.set(error)
                }
            }
            closer.start()
            // Release the dispatcher only once close() is parked: waiting for the serving task
            // (fixed) or blocked on the server lock (the defect).
            val parkDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (System.nanoTime() < parkDeadline && closer.state == Thread.State.RUNNABLE) {
                Thread.sleep(5)
            }
            proceed.countDown()
            closer.join(10_000)
            val closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

            assertNull(closeFailure.get(), "close() failed")
            assertTrue(closeMillis < 2_000, "close() took $closeMillis ms")
        }
    }

    private fun request(
        url: String,
        followRedirects: Boolean = true,
        range: String? = null,
    ): Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = followRedirects
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            Response(
                status = status,
                bytes = bytes,
                text = String(bytes),
                location = connection.getHeaderField("Location"),
                contentRange = connection.getHeaderField("Content-Range"),
                headers = connection.headerFields,
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class Response(
        val status: Int,
        val bytes: ByteArray,
        val text: String,
        val location: String?,
        val contentRange: String?,
        val headers: Map<String, List<String>>,
    )
}
