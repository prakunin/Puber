package com.kino.puber.core.startup

import coil3.request.ImageRequest
import coil3.size.Size
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class StartupRevealTest {
    @BeforeEach
    @AfterEach
    fun reset() = StartupReveal.resetForTests()

    @Test
    fun onUpdatePromptOpenChanged_openRevealsAtOnceWithoutContentOrImages() {
        StartupReveal.begin()
        StartupReveal.imageActivity.resolveSizeEnd(mockk<ImageRequest>(), Size.ORIGINAL)

        StartupReveal.onUpdatePromptOpenChanged(open = true)

        assertFalse(StartupReveal.isCovered)
    }

    @Test
    fun onUpdatePromptOpenChanged_closedLeavesTheSplashAlone() {
        StartupReveal.begin()

        StartupReveal.onUpdatePromptOpenChanged(open = false)

        assertTrue(StartupReveal.isCovered)
    }

    @Test
    fun onActivityCreated_freshStartCoversTheApp() {
        StartupReveal.onActivityCreated(restored = false)

        assertTrue(StartupReveal.isCovered)
    }

    @Test
    fun onActivityCreated_restoredActivityDoesNotCoverAgainAfterAReveal() {
        StartupReveal.onActivityCreated(restored = false)
        StartupReveal.markReady()
        assertFalse(StartupReveal.isCovered)

        StartupReveal.onActivityCreated(restored = true)

        assertFalse(StartupReveal.isCovered)
    }

    @Test
    fun onActivityCreated_restoredActivityNeverCoversInAFreshProcess() {
        StartupReveal.onActivityCreated(restored = true)

        assertFalse(StartupReveal.isCovered)
    }
}
