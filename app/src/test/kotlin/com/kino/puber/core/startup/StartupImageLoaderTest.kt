package com.kino.puber.core.startup

import coil3.ImageLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class StartupImageLoaderTest {
    @Test
    fun reportingToStartupReveal_installsTheGatesImageActivityAsTheEventListener() {
        val builder = mockk<ImageLoader.Builder>()
        every { builder.eventListener(any()) } returns builder

        builder.reportingToStartupReveal()

        verify(exactly = 1) { builder.eventListener(StartupReveal.imageActivity) }
    }
}
