package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What the real controller leaves behind: server requests, cache locks and callbacks after release. */
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerResourcesTest {

    private lateinit var harness: PlaybackControllerHarness

    @Before
    fun setUp() {
        harness = PlaybackControllerHarness()
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) harness.close()
    }

    @Test
    fun emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario() {
        val path = "/media/progressive.mp4"
        val url = harness.url(path)

        val miss = harness.prepare(path)
        harness.awaitReady(miss)
        assertTrue(
            "Empty cache should use loopback upstream",
            harness.server.requestJournal.entries.any { it.path == path },
        )
        releaseAndDrain()

        harness.cache.removeResource(url)
        writeCorruptCacheEntry(url)
        harness.useMediaRoutes()
        val corrupt = harness.prepare(path)
        // Only a terminal outcome is asserted: a malformed container fails with "Source error" (see player-corrupt-cache-recovery).
        harness.awaitCondition("corrupt-cache outcome") { corrupt.sawReady || corrupt.error.get() != null }
        releaseAndDrain()

        harness.cache.removeResource(url)
        harness.useMediaRoutes()
        val clean = harness.prepare(path)
        harness.awaitReady(clean)
        assertNull(clean.error.get())
        assertTrue(
            "Clean scenario should load after corrupt entry cleanup",
            harness.server.requestJournal.entries.any { it.path == path },
        )
        releaseAndDrain()
        assertCacheOwnershipReleased("clean post-corruption scenario")
    }

    @Test
    fun releaseDuringBlockedLoad_closesRequests() {
        val gate = harness.newGate()
        harness.server.reset(
            listOf(
                harness.server.route(
                    id = "blocked",
                    path = "/media/blocked.mp4",
                    response = HermeticTestServer.delayed(
                        gate = gate,
                        body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, harness.testContext),
                        contentType = "video/mp4",
                    ),
                ),
            ),
        )
        val probe = harness.prepare("/media/blocked.mp4")
        harness.awaitCondition("blocked request starts") { harness.server.activeRequestCount > 0 }

        val mark = harness.events.mark()
        harness.onMain { harness.controller.release() }
        gate.countDown()

        harness.server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
        assertEquals(0, harness.server.activeRequestCount)
        assertNull(harness.playerRead { player })
        assertEquals("Callbacks after release", emptyList<PlaybackEvent>(), harness.events.since(mark))
        assertNull("Stale player error: ${probe.error.get()}", probe.error.get())
        assertCacheOwnershipReleased("release during blocked load")
    }

    @Test
    fun boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequests() {
        repeat(STRESS_ITERATIONS) { iteration ->
            harness.useMediaRoutes()
            val probe = harness.prepare("/media/progressive.mp4")
            harness.awaitReady(probe)

            harness.onMain {
                harness.controller.pause()
                harness.controller.seekTo(350L)
                harness.controller.play()
            }
            harness.awaitCondition("stress iteration $iteration advances") { currentPosition >= 300L }
            assertNull(probe.error.get())

            val mark = harness.events.mark()
            releaseAndDrain()
            assertEquals("stress iteration $iteration left active requests", 0, harness.server.activeRequestCount)
            assertEquals(emptyList<PlaybackEvent>(), harness.events.since(mark))
            assertCacheOwnershipReleased("stress iteration $iteration")
        }
    }

    private fun releaseAndDrain() {
        harness.onMain { harness.controller.release() }
        harness.server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
    }

    private fun writeCorruptCacheEntry(url: String) {
        val corruptBytes = "not-a-media-container".toByteArray()
        val hole = harness.cache.startReadWrite(url, 0L, corruptBytes.size.toLong())
        try {
            val file = harness.cache.startFile(url, 0L, corruptBytes.size.toLong())
            file.outputStream().use { it.write(corruptBytes) }
            harness.cache.commitFile(file, corruptBytes.size.toLong())
        } finally {
            harness.cache.releaseHoleSpan(hole)
        }
        assertTrue(harness.cache.getCachedBytes(url, 0L, corruptBytes.size.toLong()) > 0L)
    }

    private fun assertCacheOwnershipReleased(label: String) {
        harness.cache.keys.toList().forEach { key ->
            val lockProbe = awaitCacheWriteLockRelease(key)
            assertNotNull("$label retained a cache write lock for $key", lockProbe)
            checkNotNull(lockProbe).let { span ->
                assertTrue("$label returned a non-hole lock probe for $key: $span", span.isHoleSpan)
                harness.cache.releaseHoleSpan(span)
            }
            harness.cache.removeResource(key)
            assertTrue("$label recreated cache spans for $key", harness.cache.getCachedSpans(key).isEmpty())
        }
    }

    private fun awaitCacheWriteLockRelease(key: String): CacheSpan? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CACHE_CLEANUP_TIMEOUT_SECONDS)
        do {
            harness.cache.removeResource(key)
            if (harness.cache.getCachedSpans(key).isEmpty()) {
                harness.cache.startReadWriteNonBlocking(key, 0L, C.LENGTH_UNSET.toLong())?.let { return it }
            }
        } while (
            System.nanoTime() < deadline &&
            CountDownLatch(1).await(CACHE_CLEANUP_POLL_MS, TimeUnit.MILLISECONDS).not()
        )
        return null
    }

    private companion object {
        const val STRESS_ITERATIONS = 3
        const val CACHE_CLEANUP_TIMEOUT_SECONDS = 10L
        const val CACHE_CLEANUP_POLL_MS = 25L
    }
}
