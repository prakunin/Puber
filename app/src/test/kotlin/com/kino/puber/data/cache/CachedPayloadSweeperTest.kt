package com.kino.puber.data.cache

import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.data.repository.StoredPayload
import com.kino.puber.util.FakePayloadStore
import com.kino.puber.util.MainDispatcherExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CachedPayloadSweeperTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val store = FakePayloadStore()
    private val foreground = AppForegroundState()
    private val now = 100L * 24 * 60 * 60 * 1000
    private val ceiling = CachedFeed.HardCeiling.inWholeMilliseconds

    private fun TestScope.sweeper(
        startupDelay: Duration = Duration.ZERO,
        maxRows: Int = 2_000,
        trimMediaCache: () -> Unit = {},
    ) = CachedPayloadSweeper(
        store = store,
        foreground = foreground,
        scope = backgroundScope,
        clock = { now },
        startupDelay = startupDelay,
        maxRows = maxRows,
        trimMediaCache = trimMediaCache,
    )

    @Test
    fun sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical() = runTest {
        store.write("old", "old-payload", updatedAt = now - ceiling - 1)
        store.write("recent", "recent-payload", updatedAt = now - ceiling + 1)

        sweeper().start()
        runCurrent()

        assertNull(store.read("old"))
        assertEquals(StoredPayload("recent-payload", now - ceiling + 1), store.read("recent"))
    }

    @Test
    fun aRowExactlyAtTheHardCeilingSurvives() = runTest {
        store.write("edge", "edge-payload", updatedAt = now - ceiling)

        sweeper().start()
        runCurrent()

        assertEquals(StoredPayload("edge-payload", now - ceiling), store.read("edge"))
    }

    @Test
    fun theMediaCacheIsTrimmedOnceAcrossSweeps() = runTest {
        var trims = 0

        sweeper(trimMediaCache = { trims++ }).start()
        runCurrent()
        foreground.onLeftForeground()
        runCurrent()
        foreground.onEnteredForeground()
        foreground.onLeftForeground()
        runCurrent()

        assertEquals(1, trims)
    }

    @Test
    fun aFailingMediaTrimDoesNotStopThePayloadSweep() = runTest {
        store.write("old", "old-payload", updatedAt = now - ceiling - 1)

        sweeper(trimMediaCache = { error("disk gone") }).start()
        runCurrent()

        assertNull(store.read("old"))
    }

    @Test
    fun sweepCapsRowsToTheNewest() = runTest {
        (1..5).forEach { n -> store.write("k$n", "p$n", updatedAt = now - n) }

        sweeper(maxRows = 3).start()
        runCurrent()

        assertNull(store.read("k5"))
        assertNull(store.read("k4"))
        assertNotNull(store.read("k3"))
        assertNotNull(store.read("k2"))
        assertNotNull(store.read("k1"))
    }

    @Test
    fun sweepRunsOnceAfterTheStartupDelay() = runTest {
        val startupDelay = 60.seconds
        sweeper(startupDelay = startupDelay).start()
        runCurrent()

        advanceTimeBy(startupDelay - 1.milliseconds)
        runCurrent()
        assertEquals(0, store.sweepCount)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertEquals(1, store.sweepCount)

        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(1, store.sweepCount)
    }

    @Test
    fun eachOnStopRunsOneMoreSweep() = runTest {
        sweeper().start()
        runCurrent()
        val afterStartup = store.sweepCount
        assertEquals(1, afterStartup)

        foreground.onLeftForeground()
        runCurrent()
        foreground.onEnteredForeground()
        runCurrent()
        foreground.onLeftForeground()
        runCurrent()

        assertEquals(afterStartup + 2, store.sweepCount)
    }

    @Test
    fun returningToForegroundDoesNotSweep() = runTest {
        sweeper(startupDelay = 60.seconds).start()
        runCurrent()

        foreground.onEnteredForeground()
        runCurrent()
        assertEquals(0, store.sweepCount)

        foreground.onLeftForeground()
        runCurrent()
        assertEquals(1, store.sweepCount)

        foreground.onEnteredForeground()
        runCurrent()
        assertEquals(1, store.sweepCount)
    }

    @Test
    fun startTwiceRunsOneCollector() = runTest {
        val sweeper = sweeper()
        sweeper.start()
        sweeper.start()
        runCurrent()
        val afterStartup = store.sweepCount
        assertEquals(1, afterStartup)

        foreground.onLeftForeground()
        runCurrent()

        assertEquals(afterStartup + 1, store.sweepCount)
    }

    @Test
    fun sweepRunsOffTheMainThread() {
        val testThread = Thread.currentThread()
        val swept = CompletableDeferred<Thread>()
        store.onSweep = { swept.complete(Thread.currentThread()) }
        val scope = CachedPayloadSweeper.defaultScope()
        try {
            CachedPayloadSweeper(
                store = store,
                foreground = foreground,
                scope = scope,
                startupDelay = Duration.ZERO,
            ).start()

            val thread = runBlocking { withTimeout(10.seconds) { swept.await() } }

            assertNotEquals(testThread, thread)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aFailingSweepNeitherCrashesNorStopsLaterSweeps() = runTest {
        var caught: Throwable? = null
        var failed = false
        store.onSweep = {
            if (!failed) {
                failed = true
                throw IllegalStateException("disk error")
            }
        }
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> caught = error },
        )
        try {
            CachedPayloadSweeper(
                store = store,
                foreground = foreground,
                scope = scope,
                startupDelay = Duration.ZERO,
            ).start()
            advanceUntilIdle()
            assertEquals(1, store.sweepCount)

            foreground.onLeftForeground()
            runCurrent()

            assertNull(caught)
            assertEquals(2, store.sweepCount)
        } finally {
            scope.cancel()
        }
    }
}
