package com.kino.puber.core.telemetry

import android.content.ComponentCallbacks2
import com.kino.puber.util.ManualTelemetryExecutor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class RecordingMemorySampler : MemorySampler {
    val calls = mutableListOf<Pair<Boolean, String>>()
    var failNext = false

    override fun sample(withPss: Boolean): MemReading {
        calls += withPss to Thread.currentThread().name
        if (failNext) {
            failNext = false
            error("sampler failed")
        }
        return MemReading(100, 200, 300, 400)
    }
}

internal class MemoryProbeTest {
    private val h = SinkHarness()
    private val memExec = ManualTelemetryExecutor()
    private val sampler = RecordingMemorySampler()
    private var registered = 0
    private var unregistered = 0
    private val probe = MemoryProbe(
        memExec,
        sampler,
        registerTrim = { registered++ },
        unregisterTrim = { unregistered++ },
    )

    @BeforeEach
    fun setUp() {
        h.start()
        Telemetry.install(h.sink, h.clock)
    }

    @AfterEach
    fun tearDown() {
        Telemetry.resetForTests()
    }

    private fun lines(): List<kotlinx.serialization.json.JsonObject> {
        h.drain()
        return h.linesOf("mem")
    }

    @Test
    fun foregroundSchedulesOnePeriodicSampleEverySixtySeconds() {
        probe.attach()
        probe.onForeground(true)
        Telemetry.screen = HomeScreen::class.java
        assertEquals(listOf(60_000L), memExec.repeatingPeriods)
        assertEquals(0, sampler.calls.size)
        memExec.runRepeating()
        val line = lines().single()
        assertEquals(setOf("t", "sid", "type", "scr", "java_kb", "java_max_kb", "native_kb", "pss_kb"), line.keys)
        assertEquals("100", line.str("java_kb"))
        assertEquals("200", line.str("java_max_kb"))
        assertEquals("300", line.str("native_kb"))
        assertEquals("400", line.str("pss_kb"))
        assertEquals("Home", line.str("scr"))
        assertEquals(true, sampler.calls.single().first)
    }

    @Test
    fun noPeriodicLineInTheBackgroundAndTimerIsCancelled() {
        probe.attach()
        probe.onForeground(false)
        assertEquals(0, memExec.repeatingCount)
        memExec.runRepeating()
        assertEquals(0, lines().size)
        probe.onForeground(true)
        probe.onForeground(false)
        assertEquals(0, memExec.repeatingCount)
        probe.onForeground(true)
        memExec.runRepeating()
        assertEquals(1, lines().size)
    }

    @Test
    fun reenteringForegroundKeepsOneTimerNotTwo() {
        probe.attach()
        probe.onForeground(true)
        probe.onForeground(true)
        assertEquals(1, memExec.repeatingCount)
        probe.onForeground(false)
        probe.onForeground(true)
        assertEquals(1, memExec.repeatingCount)
        memExec.runRepeating()
        assertEquals(1, lines().size)
    }

    @Test
    fun trimWritesOneExtraLineWithLevelAndNoPss() {
        probe.attach()
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        memExec.runAll()
        val line = lines().single()
        assertEquals(setOf("t", "sid", "type", "scr", "java_kb", "java_max_kb", "native_kb", "trim"), line.keys)
        assertEquals("10", line.str("trim"))
        assertEquals(false, sampler.calls.single().first)
    }

    @Test
    fun uiHiddenTrimIsIgnored() {
        probe.attach()
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        memExec.runAll()
        assertEquals(0, lines().size)
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        memExec.runAll()
        assertEquals(1, lines().size)
    }

    @Test
    fun backgroundTrimLevelIsWrittenButPeriodicIsNot() {
        probe.attach()
        probe.onForeground(false)
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        memExec.runAll()
        memExec.runRepeating()
        val line = lines().single()
        assertEquals("60", line.str("trim"))
    }

    @Test
    fun periodicSampleRunsOnTheWriterThreadNotOnTheCaller() {
        var onWriter = false
        val inner = MemorySampler { withPss ->
            onWriter = memExec.isWriterThread()
            sampler.sample(withPss)
        }
        val p = MemoryProbe(memExec, inner, {}, {})
        p.attach()
        p.onForeground(true)
        assertEquals(0, sampler.calls.size)
        memExec.runRepeating()
        assertTrue(onWriter)
        assertEquals(1, sampler.calls.size)
    }

    @Test
    fun detachUnregistersTrimClosesTimerAndLaterTrimIsIgnored() {
        probe.attach()
        probe.attach()
        assertEquals(1, registered)
        probe.onForeground(true)
        assertEquals(1, memExec.repeatingCount)
        probe.detach()
        probe.detach()
        assertEquals(1, unregistered)
        assertEquals(0, memExec.repeatingCount)
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        probe.onForeground(true)
        memExec.runAll()
        assertEquals(0, memExec.repeatingCount)
        assertEquals(0, lines().size)
        // Control: attached again, the same trim writes a line.
        probe.attach()
        probe.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        memExec.runAll()
        assertEquals(1, lines().size)
    }

    @Test
    fun samplerExceptionDoesNotStopTheTimer() {
        probe.attach()
        probe.onForeground(true)
        sampler.failNext = true
        memExec.runRepeating()
        assertEquals(0, lines().size)
        assertEquals(1, memExec.repeatingCount)
        memExec.runRepeating()
        assertEquals(1, lines().size)
    }
}
