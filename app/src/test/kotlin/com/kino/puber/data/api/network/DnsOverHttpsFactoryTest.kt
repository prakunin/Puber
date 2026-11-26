package com.kino.puber.data.api.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import okhttp3.DnsCache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import okio.Buffer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.IOException

internal class DnsOverHttpsFactoryTest {

    companion object {
        /**
         * DoH asks the public suffix list whether a host is private. On the JVM that list is an
         * Android asset that does not exist, so every host is declared public.
         */
        @JvmStatic
        @BeforeAll
        fun declareEveryHostPublic() {
            val database = mockk<PublicSuffixDatabase>()
            every { database.getEffectiveTldPlusOne(any()) } answers { firstArg() }
            mockkObject(PublicSuffixDatabase.Companion)
            every { PublicSuffixDatabase.Companion.get() } returns database
        }

        @JvmStatic
        @AfterAll
        fun restorePublicSuffixDatabase() {
            unmockkObject(PublicSuffixDatabase.Companion)
        }
    }

    private val queries = AtomicInteger()
    private val cache = DnsCache(
        minimumTimeToLive = Duration.ZERO,
        revalidateBeforeExpire = Duration.ZERO,
    )
    private val dns = DnsOverHttpsFactory.create(
        client = OkHttpClient.Builder().addInterceptor(fakeDohServer()).build(),
        cache = cache,
    )

    private val failures = AtomicInteger()
    private val requestedUrls = mutableListOf<String>()

    private fun fakeDohServer() = Interceptor { chain ->
        queries.incrementAndGet()
        requestedUrls += chain.request().url.toString()
        if (failures.getAndUpdate { maxOf(it - 1, 0) } > 0) throw IOException("resolver blip")
        val request = chain.request()
        val query = Buffer().also { request.body!!.writeTo(it) }.readByteArray()
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, // name pointer to the question
            0, 1, // type A
            0, 1, // class IN
            0, 0, 0, 1, // TTL 1 s
            0, 4, // RDLENGTH
            1, 2, 3, 4,
        )
        val body = query.copyOf()
        body[2] = 0x81.toByte()
        body[3] = 0x80.toByte()
        body[6] = 0
        body[7] = 1
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .body((body + answer).toResponseBody("application/dns-message".toMediaType()))
            .build()
    }

    @Test
    fun googleResolver_isDnsGoogleWithBootstrap8844And8888() {
        assertEquals(
            DohResolver("https://dns.google/dns-query", listOf("8.8.4.4", "8.8.8.8")),
            DohResolver.GOOGLE,
        )
    }

    @Test
    fun create_queriesTheGivenResolverUrl() {
        val resolver = DohResolver("https://dns.baseline.test:18765/dns-query", listOf("127.0.0.1"))
        val pinned = DnsOverHttpsFactory.create(
            client = OkHttpClient.Builder().addInterceptor(fakeDohServer()).build(),
            cache = HttpStack.createDnsCache(),
            resolver = resolver,
        )

        pinned.lookup("media.example")

        assertEquals(listOf(resolver.url), requestedUrls)
    }

    @Test
    fun lookup_repeatWithinTtl_issuesNoDohQuery() {
        dns.lookup("media.example")
        dns.lookup("media.example")

        assertEquals(1, queries.get())
    }

    @Test
    fun lookup_afterTtl_issuesOneDohQuery() {
        dns.lookup("media.example")
        dns.lookup("media.example")
        assertEquals(1, queries.get())

        Thread.sleep(1_200)
        dns.lookup("media.example")

        assertEquals(2, queries.get())
    }

    @Test
    fun lookup_afterEvictAll_issuesOneDohQuery() {
        dns.lookup("media.example")
        dns.lookup("media.example")
        assertEquals(1, queries.get())

        cache.evictAll()
        dns.lookup("media.example")

        assertEquals(2, queries.get())
    }

    @Test
    fun lookup_afterAFailedLookup_asksTheResolverAgain() {
        failures.set(1)
        val productionDns = DnsOverHttpsFactory.create(
            client = OkHttpClient.Builder().addInterceptor(fakeDohServer()).build(),
            cache = HttpStack.createDnsCache(),
        )

        assertThrows(IOException::class.java) { productionDns.lookup("media.example") }
        productionDns.lookup("media.example")

        assertEquals(2, queries.get())
    }
}
