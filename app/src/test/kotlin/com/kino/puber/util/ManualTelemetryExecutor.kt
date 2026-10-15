package com.kino.puber.util

import com.kino.puber.core.telemetry.TelemetryExecutor

/** Queues tasks; the test decides when the "writer thread" runs them. */
internal class ManualTelemetryExecutor : TelemetryExecutor {
    private val queued = ArrayDeque<() -> Unit>()
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val repeating = mutableListOf<() -> Unit>()
    private var running = false

    /** Every use of the executor, so a test can assert it was never touched. */
    var useCount = 0
        private set

    override fun execute(task: () -> Unit) {
        useCount++
        queued.addLast(task)
    }

    override fun schedule(delayMs: Long, task: () -> Unit) {
        useCount++
        scheduled += delayMs to task
    }

    override fun scheduleRepeating(periodMs: Long, task: () -> Unit): AutoCloseable {
        useCount++
        repeating += task
        return AutoCloseable { repeating.remove(task) }
    }

    override fun isWriterThread(thread: Thread): Boolean = running

    val scheduledDelays: List<Long> get() = scheduled.map { it.first }

    fun runAll() {
        while (queued.isNotEmpty()) inWriter(queued.removeFirst())
    }

    /** One tick of every repeating task (the periodic drain), then whatever it queued. */
    fun runRepeating() {
        repeating.toList().forEach { inWriter(it) }
        runAll()
    }

    fun runScheduled() {
        val due = scheduled.toList()
        scheduled.clear()
        due.forEach { inWriter(it.second) }
        runAll()
    }

    private fun inWriter(task: () -> Unit) {
        running = true
        try {
            task()
        } finally {
            running = false
        }
    }
}
