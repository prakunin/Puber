package com.kino.puber.core.image

import android.content.Context
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpsEnforcingInterceptorTest {

    private val context = mockk<Context>(relaxed = true)
    private val result = mockk<ImageResult>()

    private class RecordingChain(
        override val request: ImageRequest,
        private val result: ImageResult,
        private val proceeded: MutableList<ImageRequest>,
    ) : Interceptor.Chain {
        override val size: Size = Size.ORIGINAL

        override fun withRequest(request: ImageRequest): Interceptor.Chain =
            RecordingChain(request, result, proceeded)

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(): ImageResult {
            proceeded += request
            return result
        }
    }

    private suspend fun proceededData(data: Any): Any? {
        val proceeded = mutableListOf<ImageRequest>()
        val request = ImageRequest.Builder(context).data(data).build()
        HttpsEnforcingInterceptor().intercept(RecordingChain(request, result, proceeded))
        assertEquals(1, proceeded.size)
        return proceeded.single().data
    }

    @Test
    fun httpPosterUrl_isRewrittenToHttps() = runTest {
        assertEquals("https://x/p.jpg", proceededData("http://x/p.jpg"))
    }

    @Test
    fun httpsAndNonStringData_passThroughUnchanged() = runTest {
        assertEquals("https://x/p.jpg", proceededData("https://x/p.jpg"))
        assertEquals(7, proceededData(7))
    }
}
