package com.kino.puber.core.startup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class StartupSettledTest {

    @Test
    fun isNotSettledBeforeAnyActivityIsCreated() = runTest {
        val settled = StartupSettled(scope = backgroundScope)
        var resumed = false
        backgroundScope.launch {
            settled.await()
            resumed = true
        }

        advanceTimeBy(60.seconds)
        runCurrent()

        assertFalse(settled.isSettled)
        assertFalse(resumed)
    }

    @Test
    fun splashGoneSettlesTheLatch() = runTest {
        val settled = StartupSettled(scope = backgroundScope)
        var resumed = false
        backgroundScope.launch {
            settled.await()
            resumed = true
        }
        runCurrent()
        assertFalse(resumed)

        settled.onSplashGone()
        runCurrent()

        assertTrue(settled.isSettled)
        assertTrue(resumed)
    }

    @Test
    fun aFreshActivityAfterTheSplashIsGoneStartsNoFallback() = runTest {
        val scope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val settled = StartupSettled(scope = scope)
        settled.onSplashGone()

        settled.onActivityCreated(restored = false)
        runCurrent()

        assertTrue(settled.isSettled)
        assertEquals(0, scope.coroutineContext.job.children.count())
        scope.cancel()
    }

    @Test
    fun restoredActivitySettlesAtOnce() = runTest {
        val settled = StartupSettled(scope = backgroundScope)
        var resumed = false
        backgroundScope.launch {
            settled.await()
            resumed = true
        }

        settled.onActivityCreated(restored = true)
        runCurrent()

        assertTrue(resumed)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun freshActivitySettlesAtTheFallbackWhenTheSplashNeverReportsGone() = runTest {
        val fallback = 5.seconds
        val settled = StartupSettled(scope = backgroundScope, fallback = fallback)

        settled.onActivityCreated(restored = false)
        advanceTimeBy(fallback - 1.milliseconds)
        runCurrent()
        assertFalse(settled.isSettled)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertTrue(settled.isSettled)
    }

    @Test
    fun aSecondFreshActivityDoesNotPostponeTheFallback() = runTest {
        val settled = StartupSettled(scope = backgroundScope, fallback = 5.seconds)
        settled.onActivityCreated(restored = false)
        advanceTimeBy(3.seconds)
        runCurrent()

        settled.onActivityCreated(restored = false)
        advanceTimeBy(2.seconds)
        runCurrent()

        assertTrue(settled.isSettled)
    }
}
