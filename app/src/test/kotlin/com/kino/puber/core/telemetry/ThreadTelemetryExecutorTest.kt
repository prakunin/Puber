package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class ThreadTelemetryExecutorTest {
    @Test
    fun onlyTheWriterThreadIsTheWriterThread() {
        val executor = ThreadTelemetryExecutor()
        val onWriter = AtomicBoolean(false)
        val threadName = AtomicReference<String>()
        val done = CountDownLatch(1)

        executor.execute {
            onWriter.set(executor.isWriterThread())
            threadName.set(Thread.currentThread().name)
            done.countDown()
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(onWriter.get())
        assertEquals(ThreadTelemetryExecutor.THREAD_NAME, threadName.get())
        assertFalse(executor.isWriterThread())
    }
}
