package com.kino.puber.core.telemetry

import android.content.ComponentCallbacks2
import com.kino.puber.util.FakeTelemetryClock
import com.kino.puber.util.RecordingTelemetryRecorder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class MemoryProbeThreadTest {
    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    @Test
    fun samplerRunsOnlyOnTheWriterThread() {
        val recorder = RecordingTelemetryRecorder()
        Telemetry.install(recorder, FakeTelemetryClock())
        val executor = ThreadTelemetryExecutor()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(1)
        val seen = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val sampler = MemorySampler {
            gate.await(5, TimeUnit.SECONDS)
            seen += Thread.currentThread().name to executor.isWriterThread()
            done.countDown()
            MemReading(1, 2, 3, null)
        }
        val probe = MemoryProbe(executor, sampler, {}, {})
        probe.attach()
        // The caller is not blocked by the gated sampler: the sample is queued.
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(0, seen.size)
        gate.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(ThreadTelemetryExecutor.THREAD_NAME to true), seen.toList())
    }
}
