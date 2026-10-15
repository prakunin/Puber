package com.kino.puber.core.telemetry

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Everything telemetry does off the caller's thread runs here, on one thread. */
internal interface TelemetryExecutor {
    fun execute(task: () -> Unit)
    fun schedule(delayMs: Long, task: () -> Unit)
    fun scheduleRepeating(periodMs: Long, task: () -> Unit): AutoCloseable
    fun isWriterThread(thread: Thread = Thread.currentThread()): Boolean
}

/** One daemon thread `puber-telemetry`, created on first use. A failing task never kills it. */
internal class ThreadTelemetryExecutor : TelemetryExecutor {
    @Volatile
    private var writerThread: Thread? = null

    private val pool: ScheduledThreadPoolExecutor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, THREAD_NAME).apply {
                isDaemon = true
                writerThread = this
            }
        }.apply { removeOnCancelPolicy = true }
    }

    override fun execute(task: () -> Unit) {
        pool.execute { runCatching(task) }
    }

    override fun schedule(delayMs: Long, task: () -> Unit) {
        pool.schedule({ runCatching(task) }, delayMs, TimeUnit.MILLISECONDS)
    }

    override fun scheduleRepeating(periodMs: Long, task: () -> Unit): AutoCloseable {
        val future = pool.scheduleWithFixedDelay(
            { runCatching(task) },
            periodMs,
            periodMs,
            TimeUnit.MILLISECONDS,
        )
        return AutoCloseable { future.cancel(false) }
    }

    override fun isWriterThread(thread: Thread): Boolean = thread === writerThread

    companion object {
        const val THREAD_NAME = "puber-telemetry"
    }
}
